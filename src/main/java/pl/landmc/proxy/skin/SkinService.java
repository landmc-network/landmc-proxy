package pl.landmc.proxy.skin;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Applies skins through SkinsRestorer, and rate-limits who may ask.
 *
 * <p>The proxy owns the cooldown rather than leaving it to SkinsRestorer because a skin change
 * is a lookup against Mojang: without one, a player holding down the command turns into
 * outbound requests from the network's address.
 *
 * <p>The cooldown map is cleared when a player leaves. The original never removed anything from
 * it, so every player who had ever changed a skin stayed in memory until the proxy restarted -
 * the same leak the private-message service carried.
 */
public final class SkinService {

    /** Minecraft's own username shape; anything else cannot name a premium account. */
    private static final Pattern SKIN_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final ProxyServer proxy;
    private final Object plugin;
    private final ProxyConfig config;
    private final Logger logger;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SkinService(ProxyServer proxy, Object plugin, ProxyConfig config, Logger logger) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Whether SkinsRestorer is installed and still speaks the API this bridge expects.
     *
     * <p>Checked once at startup so the command can be left unregistered rather than replying
     * with an error every time somebody uses it. An installed but incompatible version is
     * reported as a warning, because that is an update that needs attention rather than a
     * feature nobody asked for.
     */
    public static boolean isAvailable(Logger logger) {
        Objects.requireNonNull(logger, "logger");

        try {
            SkinsRestorerApiBridge.provider();
        }
        catch (ReflectiveOperationException | RuntimeException | NoClassDefFoundError exception) {
            logger.info("SkinsRestorer is not installed; /skin stays unregistered.");
            return false;
        }

        try {
            SkinsRestorerApiBridge.verify(Player.class);
            logger.info("SkinsRestorer found; /skin is available.");
            return true;
        }
        catch (ReflectiveOperationException | RuntimeException | NoClassDefFoundError exception) {
            logger.warn(
                    "SkinsRestorer is installed but its API no longer matches; /skin stays"
                            + " unregistered. Update landmc-proxy or pin SkinsRestorer.",
                    exception);
            return false;
        }
    }

    public static boolean isValidSkinName(String skinName) {
        return skinName != null && SKIN_NAME.matcher(skinName).matches();
    }

    /**
     * Czy tego skina wolno komus zalozyc.
     *
     * <p>Lista jest po to, zeby nikt nie chodzil po spawnie jako wlasciciel serwera. Oryginal
     * mial te cztery nicki wpisane w kod i porownywal je z ranga; tutaj to plik i uprawnienie,
     * wiec nowy czlonek ekipy nie wymaga wydania pluginu.
     */
    public boolean mayWear(Player player, String skinName) {
        if (player.hasPermission(this.config.skin.protectedBypass)) {
            return true;
        }

        for (String guarded : this.config.skin.protectedSkins) {
            if (guarded.equalsIgnoreCase(skinName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Zdejmuje wybranego skina i przywraca wlasny.
     *
     * <p>Tak samo, jak zakladanie: przez magazyn SkinsRestorera, zeby zmiana przezyla
     * przelaczenie serwera i wylogowanie. Bez tego "przywroc" dzialaloby do najblizszego
     * wejscia na serwer.
     */
    public CompletableFuture<SkinResult> restore(Player player) {
        Objects.requireNonNull(player, "player");

        UUID playerId = player.getUniqueId();
        this.setCooldown(playerId, this.config.skin.successCooldownSeconds);

        CompletableFuture<SkinResult> result = new CompletableFuture<>();
        this.proxy.getScheduler()
                .buildTask(this.plugin, () -> result.complete(this.restoreBlocking(player)))
                .schedule();
        return result;
    }

    private SkinResult restoreBlocking(Player player) {
        try {
            Object skinsRestorer = SkinsRestorerApiBridge.provider();
            Object playerStorage = SkinsRestorerApiBridge.invokeNoArgs(
                    skinsRestorer, "getPlayerStorage");

            SkinsRestorerApiBridge.removePlayerSkin(playerStorage, player.getUniqueId());

            Object applier = SkinsRestorerApiBridge.skinApplier(skinsRestorer, Player.class);
            SkinsRestorerApiBridge.applySkin(applier, player);
            return SkinResult.APPLIED;
        }
        catch (ReflectiveOperationException | RuntimeException | NoClassDefFoundError failed) {
            this.setCooldown(player.getUniqueId(), this.config.skin.errorCooldownSeconds);
            this.logger.warn("Could not restore the skin of {}.", player.getUsername(), failed);
            return SkinResult.FAILED;
        }
    }

    public String permission() {
        return this.config.skin.permission;
    }

    /** Seconds the player still has to wait, or zero when they may go ahead. */
    public long remainingCooldownSeconds(UUID playerId) {
        return this.remainingCooldownSeconds(playerId, System.currentTimeMillis());
    }

    long remainingCooldownSeconds(UUID playerId, long now) {
        Long expiresAt = this.cooldowns.get(playerId);
        if (expiresAt == null || expiresAt <= now) {
            this.cooldowns.remove(playerId);
            return 0L;
        }
        return Math.max(1L, (expiresAt - now + 999L) / 1_000L);
    }

    /**
     * Looks the skin up and applies it, off the calling thread.
     *
     * <p>The cooldown starts before the work, not after it, so a player cannot queue up lookups
     * while the first one is still running. A failed lookup is shortened to the error cooldown,
     * because being made to wait the full time for a typo is its own annoyance.
     *
     * @return what to tell the player
     */
    public CompletableFuture<SkinResult> apply(Player player, String skinName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(skinName, "skinName");

        UUID playerId = player.getUniqueId();
        this.setCooldown(playerId, this.config.skin.successCooldownSeconds);

        CompletableFuture<SkinResult> result = new CompletableFuture<>();
        this.proxy.getScheduler()
                .buildTask(this.plugin, () -> result.complete(this.applyBlocking(player, skinName)))
                .schedule();
        return result;
    }

    /** Forgets a player's cooldown; called when they disconnect. */
    public void onDisconnect(UUID playerId) {
        this.cooldowns.remove(playerId);
    }

    private SkinResult applyBlocking(Player player, String skinName) {
        try {
            Object skinsRestorer = SkinsRestorerApiBridge.provider();
            Object skinStorage = SkinsRestorerApiBridge.invokeNoArgs(skinsRestorer, "getSkinStorage");
            Optional<?> skin = SkinsRestorerApiBridge.findOrCreateSkinData(skinStorage, skinName);
            if (skin.isEmpty()) {
                this.setCooldown(player.getUniqueId(), this.config.skin.errorCooldownSeconds);
                return SkinResult.NOT_FOUND;
            }

            Object identifier = SkinsRestorerApiBridge.invokeNoArgs(skin.get(), "getIdentifier");
            Object playerStorage = SkinsRestorerApiBridge.invokeNoArgs(skinsRestorer, "getPlayerStorage");
            SkinsRestorerApiBridge.setPlayerSkin(playerStorage, player.getUniqueId(), identifier);
            Object applier = SkinsRestorerApiBridge.skinApplier(skinsRestorer, Player.class);
            SkinsRestorerApiBridge.applySkin(applier, player);
            return SkinResult.APPLIED;
        }
        catch (ReflectiveOperationException | RuntimeException | NoClassDefFoundError exception) {
            this.setCooldown(player.getUniqueId(), this.config.skin.errorCooldownSeconds);
            this.logger.warn("Could not apply skin {} to {}.", skinName, player.getUsername(), exception);
            return SkinResult.FAILED;
        }
    }

    private void setCooldown(UUID playerId, int seconds) {
        this.setCooldown(playerId, System.currentTimeMillis(), seconds);
    }

    /** @param now the current time, so a test does not have to wait out a real cooldown */
    void setCooldown(UUID playerId, long now, int seconds) {
        this.cooldowns.put(playerId, now + Math.max(0, seconds) * 1_000L);
    }

    boolean hasNoCooldowns() {
        return this.cooldowns.isEmpty();
    }

    /** What came of a skin request. */
    public enum SkinResult {
        APPLIED,
        /** No premium account by that name. */
        NOT_FOUND,
        /** SkinsRestorer or Mojang could not be reached; the failure is in the log. */
        FAILED
    }
}
