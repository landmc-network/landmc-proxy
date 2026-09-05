package pl.landmc.proxy.report;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import pl.landmc.menus.protocol.MenuPayload;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.rank.RankProvider;

/**
 * Reports: who reported whom, for what, and how often they may.
 *
 * <p>The reasons are configuration, and they travel to the menu with the payload, so the side
 * that has to understand the click back is the side that decided what could be clicked. A
 * reason that is not on the list is refused here rather than trusted - the click arrives over
 * a player's own connection and a modified client can send whatever it likes.
 *
 * <p>The cooldown is per pair, not per player, exactly as the original: reporting one player
 * must not stop you reporting a second one who is doing something else at the same time, and
 * reporting the same player five times in a row adds nothing staff did not read the first time.
 */
public final class ReportService {

    /**
     * How long an opened report menu stays meaningful.
     *
     * <p>Long enough to read five tiles, short enough that a click arriving from a menu opened
     * an hour ago - which is a client that kept the window, not a person - reports nobody.
     */
    private static final Duration OPEN_TIMEOUT = Duration.ofMinutes(2);

    private final ProxyConfig config;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final RankProvider ranks;
    private final Supplier<ProxyMessages> messages;

    /**
     * When each reporter may report each target again.
     *
     * <p>Keyed by the pair. Entries are dropped as they are read rather than swept: a map that
     * only grows while people report each other is a slow leak, and the read happens exactly
     * when the entry stops mattering.
     */
    private final Map<Pair, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * Who each player currently has the report menu open on.
     *
     * <p>Kept here rather than sent to the menu and back. The click arrives over the player's
     * own connection, so anything travelling with it is something a modified client can choose;
     * the target is the one part of a report that must not be chooseable, and this is what
     * makes it the player they actually ran the command on.
     */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ReportService(
            ProxyConfig config,
            VelocityNoticeService<ProxyMessages> notices,
            RankProvider ranks,
            Supplier<ProxyMessages> messages) {

        this.config = Objects.requireNonNull(config, "config");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isEnabled() {
        return this.config.report.enabled;
    }

    /** The menu to show, with the reported player named on it. */
    public MenuPayload.Report payload(Pending reported) {
        List<MenuPayload.Report.Reason> reasons =
                new ArrayList<>(this.config.report.reasons.size());

        for (ProxyConfig.ReportReason reason : this.config.report.reasons) {
            reasons.add(new MenuPayload.Report.Reason(
                    reason.id, reason.label, reason.material, reason.slot));
        }

        return new MenuPayload.Report(reported.displayName(), reasons);
    }

    /** Remembers who this player is about to report, and says what was remembered. */
    public Pending open(Player reporter, Player reported) {
        // A menu somebody opened and walked away from is not worth keeping. Swept on each open
        // rather than on a timer: the map holds one entry per player who has the menu up, and
        // the sweep costs nothing at that size.
        long stale = System.currentTimeMillis() - OPEN_TIMEOUT.toMillis();
        this.pending.values().removeIf(entry -> entry.openedAt() < stale);

        Pending open = new Pending(
                reported.getUniqueId(),
                reported.getUsername(),
                this.describe(reported),
                System.currentTimeMillis());

        this.pending.put(reporter.getUniqueId(), open);
        return open;
    }

    /** Who this player has the menu open on, or null when the menu is not open or has expired. */
    public Pending pending(UUID reporter) {
        Pending open = this.pending.remove(reporter);
        if (open == null
                || open.openedAt() < System.currentTimeMillis() - OPEN_TIMEOUT.toMillis()) {

            return null;
        }
        return open;
    }

    public void forget(UUID reporter) {
        this.pending.remove(reporter);
    }

    /** How long before this player may report that one again; zero when they may now. */
    public Duration remaining(UUID reporter, UUID reported) {
        Long until = this.cooldowns.get(new Pair(reporter, reported));
        if (until == null) {
            return Duration.ZERO;
        }

        long left = until - System.currentTimeMillis();
        if (left <= 0L) {
            this.cooldowns.remove(new Pair(reporter, reported));
            return Duration.ZERO;
        }
        return Duration.ofMillis(left);
    }

    /** The configured reason with this id, or null when there is none. */
    public ProxyConfig.ReportReason reason(String id) {
        for (ProxyConfig.ReportReason reason : this.config.report.reasons) {
            if (reason.id.equalsIgnoreCase(id)) {
                return reason;
            }
        }
        return null;
    }

    /**
     * Sends a report to whoever is on duty, and starts the cooldown.
     *
     * <p>The reported player is named rather than held: they may be on another server, or have
     * left between opening the menu and clicking it, and a report about somebody who has just
     * logged off is still worth reading.
     */
    public void send(Player reporter, String reportedName, ProxyConfig.ReportReason reason) {
        Formatter placeholders = new Formatter()
                .register("{PLAYER}", this.describe(reporter))
                .register("{REPORTED}", reportedName)
                .register("{REASON}", reason.label)
                .register("{SERVER}", currentServerOf(reporter));

        this.notices.create()
                .onlinePlayers(this.config.report.receivePermission)
                .console()
                .notice(messages -> messages.reportBroadcast)
                .formatter(placeholders)
                .send();

        this.notices.create()
                .player(reporter.getUniqueId())
                .notice(messages -> messages.reportSent)
                .formatter(placeholders)
                .send();
    }

    /** Starts the cooldown for this pair. Called once the report is actually accepted. */
    public void startCooldown(UUID reporter, UUID reported) {
        long seconds = Math.max(0L, this.config.report.cooldownSeconds);
        if (seconds == 0L) {
            return;
        }

        this.cooldowns.put(
                new Pair(reporter, reported),
                System.currentTimeMillis() + Duration.ofSeconds(seconds).toMillis());
    }

    /** A player's name with their rank in front, the way the old server wrote both of them. */
    private String describe(Player player) {
        String prefix = this.ranks.prefixOf(player);
        return prefix.isBlank() ? player.getUsername() : prefix + player.getUsername();
    }

    public ProxyMessages messages() {
        return this.messages.get();
    }

    private static String currentServerOf(Player player) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("?");
    }

    /** One reporter and one reported player, as a key. */
    private record Pair(UUID reporter, UUID reported) {
    }

    /**
     * A report menu that is open.
     *
     * @param displayName the reported player with their rank in front, for the menu's sign
     */
    public record Pending(UUID reported, String username, String displayName, long openedAt) {
    }
}
