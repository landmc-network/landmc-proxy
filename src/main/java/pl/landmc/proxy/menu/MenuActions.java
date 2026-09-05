package pl.landmc.proxy.menu;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import pl.landmc.menus.protocol.MenuAction;
import pl.landmc.menus.protocol.MenuKind;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.friend.FriendService;
import pl.landmc.proxy.report.ReportService;
import pl.landmc.proxy.routing.RoutingService;

/**
 * What happens when somebody clicks something in a menu.
 *
 * <p>Two rules run through all of it.
 *
 * <p>An action is never trusted for what it says, only for who it came from. The argument is
 * checked against what it is supposed to be - a player name, a server on the menu - before it
 * reaches anything, so a hand-written action can ask for nothing a click could not.
 *
 * <p>And where a command already does the job, the action runs that command rather than
 * reimplementing it. Removing a friend from the menu is {@code /friend usun}: the same checks,
 * the same messages, and no second copy to forget to update. It is also the clearest possible
 * statement of what a menu is allowed to do, which is exactly what its owner could type.
 */
public final class MenuActions {

    /** What a Minecraft name may be. Anything else never becomes part of a command line. */
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final ProxyServer proxy;

    /** Null when the friends system is switched off, in which case that menu has no actions. */
    private final @Nullable FriendService friends;
    private final RoutingService routing;
    private final ServerMenuService servers;

    /** Null when reports are switched off, in which case that menu has no actions either. */
    private final @Nullable ReportService reports;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final Logger logger;

    public MenuActions(
            ProxyServer proxy,
            @Nullable FriendService friends,
            RoutingService routing,
            ServerMenuService servers,
            @Nullable ReportService reports,
            VelocityNoticeService<ProxyMessages> notices,
            Logger logger) {

        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.friends = friends;
        this.routing = Objects.requireNonNull(routing, "routing");
        this.servers = Objects.requireNonNull(servers, "servers");
        this.reports = reports;
        this.notices = Objects.requireNonNull(notices, "notices");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Registers this class as the handler for the menus it deals with.
     *
     * <p>The friends menu is only wired up when there is a friends system behind it. An action
     * with no handler is ignored and logged at debug, which is the right answer for a click on
     * a menu that cannot exist.
     */
    public void registerOn(MenuBridge bridge) {
        if (this.friends != null) {
            bridge.handler(MenuKind.FRIENDS, this::onFriendsAction);
        }
        bridge.handler(MenuKind.SERVERS, this::onServersAction);
        bridge.handler(MenuKind.LOBBIES, this::onLobbiesAction);
        bridge.handler(MenuKind.PROFILE, this::onProfileAction);
        bridge.handler(MenuKind.STATISTICS, this::onStatisticsAction);
        if (this.reports != null) {
            bridge.handler(MenuKind.REPORT, this::onReportAction);
        }
    }

    /**
     * A reason was picked in the report menu.
     *
     * <p>Nothing about who is being reported comes from the click. The proxy remembered that
     * when the command ran, and this only supplies the reason - which is then checked against
     * the configured list, because the click arrives over a player's own connection.
     *
     * <p>The cooldown starts here rather than when the menu opened: a player who opens it and
     * closes it without choosing has not reported anybody and should not be made to wait.
     */
    private void onReportAction(Player player, MenuAction action) {
        if (this.reports == null || !"send".equals(action.action())) {
            this.logger.debug("Unknown report menu action: {}", action.action());
            return;
        }

        ReportService.Pending open = this.reports.pending(player.getUniqueId());
        if (open == null) {
            // The menu was opened more than a couple of minutes ago, or never - a click with
            // nothing behind it. Silent: there is nobody this could name.
            return;
        }

        ProxyConfig.ReportReason reason = this.reports.reason(action.argument());
        if (reason == null) {
            this.notices.viewer(player, messages -> messages.reportUnknownReason);
            return;
        }

        this.reports.send(player, open.username(), reason);
        this.reports.startCooldown(player.getUniqueId(), open.reported());
    }

    /**
     * The profile's tiles are doors, not switches.
     *
     * <p>Each one runs the command that owns what it leads to - the friends list, the premium
     * login setting - so the profile never has a second opinion about either, and a tile can
     * reach nothing its owner could not already be asked for.
     */
    private void onProfileAction(Player player, MenuAction action) {
        switch (action.action()) {
            // Sent by the lobby's hotbar. A backend cannot run a proxy command itself - a
            // command dispatched there never leaves it - so it asks for the menu instead.
            case "open" -> this.dispatch(player, "profil");
            case "friends" -> this.dispatch(player, "friend");
            // The tile the old profile had: straight into the rank shop.
            case "shop" -> this.dispatch(player, "rangi");
            case "visual" -> this.dispatch(player, "rangiwizualne");
            case "statistics" -> this.dispatch(player, "statystyki");
            case "premium" -> this.dispatch(player, "premium");
            default -> this.logger.debug("Unknown profile menu action: {}", action.action());
        }
    }

    private void onFriendsAction(Player player, MenuAction action) {
        switch (action.action()) {
            case "remove" -> this.runAsPlayer(player, "friend usun ", action.argument());
            case "requests" -> this.dispatch(player, "friend zaproszenia");
            // The tab strip: back to the profile this list was opened from.
            case "profile" -> this.dispatch(player, "profil");
            case "statistics" -> this.dispatch(player, "statystyki");
            case "join" -> this.joinFriend(player, action.argument());
            default -> this.logger.debug("Unknown friends menu action: {}", action.action());
        }
    }

    /** The statistics tab is a strip and nothing else; both of its tabs lead elsewhere. */
    private void onStatisticsAction(Player player, MenuAction action) {
        switch (action.action()) {
            case "profile" -> this.dispatch(player, "profil");
            case "friends" -> this.dispatch(player, "friend");
            default -> this.logger.debug("Unknown statistics action: {}", action.action());
        }
    }

    /**
     * The lobby list: the hotbar asking for it, and a click on one of them.
     *
     * <p>Kept apart from the server list rather than sharing its handler, because the two menus
     * offer different sets and a click on one must not reach a server only the other lists.
     */
    private void onLobbiesAction(Player player, MenuAction action) {
        if ("open".equals(action.action())) {
            this.dispatch(player, "podserwery");
            return;
        }

        if (!"connect".equals(action.action())) {
            this.logger.debug("Unknown lobbies menu action: {}", action.action());
            return;
        }

        Optional<RegisteredServer> target = this.servers.selectableLobby(action.argument());
        if (target.isEmpty()) {
            this.notices.viewer(player, messages -> messages.menuServerUnavailable);
            return;
        }

        this.routing.connect(player, target.get());
    }

    private void onServersAction(Player player, MenuAction action) {
        if ("open".equals(action.action())) {
            // The lobby's compass. See the note on the profile's "open".
            this.dispatch(player, "serwery");
            return;
        }

        if (!"connect".equals(action.action())) {
            this.logger.debug("Unknown servers menu action: {}", action.action());
            return;
        }

        // Only a server the menu offers. The configured list is the permission model here:
        // /server has one, and this is the way a player without it changes servers.
        Optional<RegisteredServer> target = this.servers.selectable(action.argument());
        if (target.isEmpty()) {
            this.notices.create()
                    .viewer(player)
                    .notice(messages -> messages.menuServerUnavailable)
                    .send();
            return;
        }

        this.routing.connect(player, target.get());
    }

    /** Follows a friend to wherever they are, if they are somewhere this player may see. */
    private void joinFriend(Player player, String name) {
        if (this.friends == null || !PLAYER_NAME.matcher(name).matches()) {
            return;
        }

        Optional<Player> friend = this.proxy.getPlayer(name)
                .filter(other -> this.friends.isVisiblyOnline(player, other.getUniqueId()));

        Optional<RegisteredServer> target = friend
                .flatMap(other -> this.friends.serverOf(player, other.getUniqueId()))
                .flatMap(this.proxy::getServer);

        if (target.isEmpty()) {
            // They logged off, or went somewhere this player cannot see, between the menu being
            // drawn and the click. Not an error - just no longer true.
            this.notices.create()
                    .viewer(player)
                    .notice(messages -> messages.menuServerUnavailable)
                    .send();
            return;
        }

        this.routing.connect(player, target.get());
    }

    /** Runs a command on the player's behalf, with an argument that has been checked first. */
    private void runAsPlayer(Player player, String commandPrefix, String name) {
        if (!PLAYER_NAME.matcher(name).matches()) {
            this.logger.debug("Refused a menu action naming {}", name);
            return;
        }

        this.dispatch(player, commandPrefix + name);
    }

    private void dispatch(Player player, String commandLine) {
        this.proxy.getCommandManager()
                .executeAsync(player, commandLine)
                .exceptionally(throwable -> {
                    this.logger.error(
                            "Menu command '{}' failed for {}",
                            commandLine, player.getUsername(), throwable);
                    return null;
                });
    }
}
