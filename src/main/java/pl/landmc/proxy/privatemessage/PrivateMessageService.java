package pl.landmc.proxy.privatemessage;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import pl.landmc.platform.config.ConfigService;
import pl.landmc.platform.notice.NoticeServiceProvider;
import pl.landmc.platform.notice.PlatformNotice;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.vanish.VanishProvider;

/**
 * Private messages, the reply target, social spy and the ignore list.
 *
 * <p>Everything transient is keyed by UUID, not by {@code Player}. The original kept
 * {@code Set<Player>} for social spies and for players with messages disabled, which pins a
 * disconnected player's object - and everything it references - in memory for the lifetime of
 * the proxy. {@link #onDisconnect(UUID)} clears the rest.
 *
 * <p>Called from command threads and from the disconnect event, so the maps are concurrent.
 */
public final class PrivateMessageService {

    private final ProxyServer proxy;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final IgnoreStorage ignores;
    private final ConfigService configs;
    private final VanishProvider vanish;
    private final NoticeServiceProvider<CommandSource> platformNotices;

    /** Who last messaged whom, so {@code /r} knows where to go. Cleared on disconnect. */
    private final ConcurrentHashMap<UUID, UUID> replyTargets = new ConcurrentHashMap<>();

    private final Set<UUID> socialSpies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> messagesDisabled = ConcurrentHashMap.newKeySet();

    public PrivateMessageService(
            ProxyServer proxy,
            VelocityNoticeService<ProxyMessages> notices,
            NoticeServiceProvider<CommandSource> platformNotices,
            IgnoreStorage ignores,
            ConfigService configs,
            VanishProvider vanish) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.platformNotices = Objects.requireNonNull(platformNotices, "platformNotices");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
    }

    /**
     * Delivers a message, unless the receiver has messages off or is ignoring the sender.
     *
     * <p>The sender is told which of the two happened only in the first case. "You are being
     * ignored" would tell someone they have been muted, which is exactly what an ignore is
     * meant to hide - so an ignored message reports the same thing as a delivered one.
     */
    public void send(Player sender, Player receiver, String message) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(receiver, "receiver");

        if (sender.getUniqueId().equals(receiver.getUniqueId())) {
            this.notices.viewer(sender, messages -> messages.messageToSelf);
            return;
        }

        // A hidden moderator must answer exactly as an offline player does. Anything else -
        // a different message, or even a different one of ours - turns /msg into a way to
        // check who is watching.
        if (!this.vanish.canSee(sender, receiver)) {
            this.platformNotices.send(
                    sender,
                    PlatformNotice.PLAYER_NOT_FOUND,
                    Placeholder.unparsed("player", receiver.getUsername()));
            return;
        }

        if (this.messagesDisabled.contains(receiver.getUniqueId())) {
            this.notices.viewer(
                    sender,
                    messages -> messages.messageReceiverDisabled,
                    new Formatter().register("{PLAYER}", receiver.getUsername()));
            return;
        }

        Formatter placeholders = new Formatter()
                .register("{SENDER}", sender.getUsername())
                .register("{RECEIVER}", receiver.getUsername())
                .register("{MESSAGE}", message);

        this.notices.viewer(sender, messages -> messages.messageOutgoing, placeholders);

        if (this.isIgnoring(receiver.getUniqueId(), sender.getUniqueId())) {
            // Silently dropped: the sender must not be able to detect the ignore.
            return;
        }

        this.notices.viewer(receiver, messages -> messages.messageIncoming, placeholders);
        this.replyTargets.put(receiver.getUniqueId(), sender.getUniqueId());

        this.notifySpies(sender, receiver, placeholders);
    }

    /** Sends to whoever last messaged this player. */
    public void reply(Player sender, String message) {
        UUID targetId = this.replyTargets.get(sender.getUniqueId());
        if (targetId == null) {
            this.notices.viewer(sender, messages -> messages.replyNoTarget);
            return;
        }

        Optional<Player> target = this.proxy.getPlayer(targetId);
        if (target.isEmpty()) {
            this.notices.viewer(sender, messages -> messages.replyTargetOffline);
            return;
        }

        this.send(sender, target.get(), message);
    }

    /** Turns incoming private messages on or off; returns the new state. */
    public boolean toggleMessages(UUID playerId) {
        if (this.messagesDisabled.remove(Objects.requireNonNull(playerId, "playerId"))) {
            return true;
        }
        this.messagesDisabled.add(playerId);
        return false;
    }

    public boolean acceptsMessages(UUID playerId) {
        return !this.messagesDisabled.contains(playerId);
    }

    /** Turns social spy on or off; returns the new state. */
    public boolean toggleSocialSpy(UUID playerId) {
        if (this.socialSpies.remove(Objects.requireNonNull(playerId, "playerId"))) {
            return false;
        }
        this.socialSpies.add(playerId);
        return true;
    }

    public boolean isSpying(UUID playerId) {
        return this.socialSpies.contains(playerId);
    }

    /**
     * Adds or removes an ignore; returns true when the target is now ignored.
     *
     * <p>Writes the file on every change. Ignores are rare enough that this is cheaper than
     * tracking dirty state, and going through the config service means the write is atomic.
     */
    public boolean toggleIgnore(UUID playerId, UUID targetId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetId, "targetId");

        Set<UUID> ignored = this.ignores.ignoredBy(playerId);
        boolean nowIgnored;
        if (ignored.remove(targetId)) {
            nowIgnored = false;
        }
        else {
            ignored.add(targetId);
            nowIgnored = true;
        }

        this.configs.save(this.ignores);
        return nowIgnored;
    }

    public boolean isIgnoring(UUID playerId, UUID targetId) {
        Set<UUID> ignored = this.ignores.ignoredPlayers.get(playerId);
        return ignored != null && ignored.contains(targetId);
    }

    /** Names of the players someone ignores; unknown ids fall back to the raw UUID. */
    public List<String> ignoredNames(UUID playerId) {
        Set<UUID> ignored = this.ignores.ignoredPlayers.get(playerId);
        if (ignored == null || ignored.isEmpty()) {
            return List.of();
        }

        return ignored.stream()
                .map(id -> this.proxy.getPlayer(id).map(Player::getUsername).orElseGet(id::toString))
                .sorted()
                .toList();
    }

    /**
     * Drops the transient state of a player who left.
     *
     * <p>The ignore list is not touched - that is the part meant to survive. Reply targets
     * pointing at this player are left to expire naturally when the next reply finds them
     * offline, rather than scanning the whole map on every disconnect.
     */
    public void onDisconnect(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        this.replyTargets.remove(playerId);
        this.socialSpies.remove(playerId);
        this.messagesDisabled.remove(playerId);
    }

    private void notifySpies(Player sender, Player receiver, Formatter placeholders) {
        if (this.socialSpies.isEmpty()) {
            return;
        }

        for (UUID spyId : this.socialSpies) {
            if (spyId.equals(sender.getUniqueId()) || spyId.equals(receiver.getUniqueId())) {
                continue;
            }
            this.proxy.getPlayer(spyId).ifPresent(spy ->
                    this.notices.viewer(spy, messages -> messages.socialSpyFormat, placeholders));
        }
    }
}
