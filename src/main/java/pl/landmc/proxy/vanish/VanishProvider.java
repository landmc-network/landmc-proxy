package pl.landmc.proxy.vanish;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Asks the vanish plugin whether one player may see another.
 *
 * <p>Without this a hidden moderator is trivially detectable: {@code /msg Nick} answering
 * "delivered" says they are online, whatever the player list shows. Every place that reveals
 * presence has to route through here.
 *
 * <p>Reached by reflection, and deliberately so: the vanish plugin is a separate project on the
 * same proxy, and a compile-time dependency would tie the two together for three boolean
 * methods. When it is absent every answer is "visible", which is the right default - the
 * feature hides people, so its absence must not hide anyone by accident.
 *
 * <p>Failure is answered the same way. A vanish plugin that changed its API leaves moderators
 * visible rather than making the proxy behave as though everyone were hidden.
 */
public final class VanishProvider {

    private final ProxyServer proxy;
    private final String pluginId;
    private final boolean enabled;

    private VanishProvider(ProxyServer proxy, String pluginId, boolean enabled) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.enabled = enabled;
    }

    /** Binds to the configured vanish plugin, or to a provider that hides nobody. */
    public static VanishProvider create(ProxyServer proxy, ProxyConfig config, Logger logger) {
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(logger, "logger");

        String pluginId = config.vanish.pluginId;
        boolean present = config.vanish.enabled
                && !pluginId.isBlank()
                && proxy.getPluginManager().isLoaded(pluginId);

        if (present) {
            logger.info("Vanish plugin '{}' found; hidden staff stay hidden from /msg.", pluginId);
        }
        else if (config.vanish.enabled) {
            logger.info("No vanish plugin installed; every online player is treated as visible.");
        }

        return new VanishProvider(proxy, pluginId, present);
    }

    public boolean isVanished(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return this.callBoolean(false, "isVanished", new Class<?>[] {UUID.class}, playerId);
    }

    /**
     * Whether the viewer may know the target is online.
     *
     * <p>A player always sees themselves, and a target who is not hidden is visible to
     * everybody - both are settled here so the vanish plugin is only asked about the case it
     * exists for.
     */
    public boolean canSee(Player viewer, Player target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");

        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        if (!this.isVanished(target.getUniqueId())) {
            return true;
        }

        return this.callBoolean(true, "canSee", new Class<?>[] {Player.class, Player.class}, viewer, target);
    }

    /**
     * @param fallback the answer when the plugin is absent or cannot be asked - stated per call
     *     rather than shared, so "nobody is hidden" and "everybody is visible" cannot collapse
     *     into one value that is wrong for one of them
     */
    private boolean callBoolean(
            boolean fallback, String methodName, Class<?>[] parameterTypes, Object... arguments) {

        if (!this.enabled) {
            return fallback;
        }

        Optional<Object> instance = this.proxy.getPluginManager()
                .getPlugin(this.pluginId)
                .flatMap(PluginContainer::getInstance);

        if (instance.isEmpty()) {
            return fallback;
        }

        try {
            Method method = instance.get().getClass().getMethod(methodName, parameterTypes);
            Object result = method.invoke(instance.get(), arguments);
            return result instanceof Boolean answer ? answer : fallback;
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return fallback;
        }
    }
}
