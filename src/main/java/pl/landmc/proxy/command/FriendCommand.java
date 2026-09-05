package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.argument.Key;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import pl.landmc.platform.proxy.command.VelocityCommands;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.friend.FriendRepository.AcceptOutcome;
import pl.landmc.proxy.friend.FriendRepository.FriendProfile;
import pl.landmc.proxy.friend.FriendService;
import pl.landmc.proxy.menu.FriendMenuService;
import pl.landmc.proxy.menu.MenuBridge;

/**
 * {@code /friend} - the friends list.
 *
 * <p>Thin on purpose: every rule lives in {@link FriendService}, and this turns its answers into
 * messages. Nothing here waits on a future, so no command blocks a proxy thread.
 *
 * <p>Bare {@code /friend} asks the backend to open the menu, as the original did. The
 * subcommands stay reachable, because a menu that the backend cannot open would otherwise leave
 * the whole feature unusable.
 */
@Command(name = "friend", aliases = {"znajomi", "f"})
@Permission("landmc.command.friend")
public class FriendCommand {

    private final FriendService friends;
    private final FriendMenuService menu;
    private final MenuBridge bridge;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final ProxyConfig config;
    private final Logger logger;

    public FriendCommand(
            FriendService friends,
            FriendMenuService menu,
            MenuBridge bridge,
            VelocityNoticeService<ProxyMessages> notices,
            ProxyConfig config,
            Logger logger) {

        this.friends = Objects.requireNonNull(friends, "friends");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Bare {@code /friend} opens the menu, with the list sent along in the same message.
     *
     * <p>The backend used to be told only to open a menu and then had to ask for the contents,
     * which is a second round trip to deliver a list this side already had in its hand.
     */
    @Execute
    void menu(@Context Player player) {
        if (!this.config.friends.guiEnabled) {
            this.list(player);
            return;
        }

        this.menu.payload(player)
                .thenAccept(payload -> {
                    if (!this.bridge.send(player, payload)) {
                        // The backend has no menu plugin, or the player is between servers.
                        // Showing the list is a better answer than telling them their own
                        // command is unavailable.
                        this.list(player);
                    }
                })
                .exceptionally(this.report(player, "menu"));
    }

    @Execute(name = "zapros", aliases = {"dodaj", "invite", "add"})
    void invite(@Context Player player, @Key(VelocityCommands.PLAYER) @Arg("gracz") String target) {
        this.friends.invite(player, target)
                .thenAccept(outcome -> this.replyToInvite(player, outcome))
                .exceptionally(this.report(player, "invite " + target));
    }

    @Execute(name = "akceptuj", aliases = {"przyjmij", "accept"})
    void accept(
            @Context Player player,
            @Key(VelocityCommands.PLAYER) @Arg("gracz") String requester) {
        this.friends.accept(player, requester)
                .thenAccept(result -> this.replyToAccept(player, result, requester))
                .exceptionally(this.report(player, "accept " + requester));
    }

    @Execute(name = "odrzuc", aliases = {"odrzuć", "decline", "deny"})
    void decline(
            @Context Player player,
            @Key(VelocityCommands.PLAYER) @Arg("gracz") String requester) {
        this.friends.decline(player, requester)
                .thenAccept(declined -> this.notices.create()
                        .viewer(player)
                        .notice(messages -> declined.isPresent()
                                ? messages.friendRequestDeclined
                                : messages.friendNoRequest)
                        .formatter(new Formatter().register(
                                "{PLAYER}", declined.map(FriendProfile::displayName).orElse(requester)))
                        .send())
                .exceptionally(this.report(player, "decline " + requester));
    }

    @Execute(name = "usun", aliases = {"usuń", "remove", "delete"})
    void remove(@Context Player player, @Key(VelocityCommands.PLAYER) @Arg("gracz") String target) {
        this.friends.remove(player, target)
                .thenAccept(removed -> {
                    this.notices.create()
                            .viewer(player)
                            .notice(messages -> removed.isPresent()
                                    ? messages.friendRemoved
                                    : messages.friendNotOnList)
                            .formatter(new Formatter().register(
                                    "{PLAYER}", removed.map(FriendProfile::displayName).orElse(target)))
                            .send();

                    // Only somebody who really was a friend is told about it; otherwise the
                    // command would be a way to message players who never agreed to hear from
                    // the sender.
                    removed.map(FriendProfile::playerId)
                            .flatMap(this.friends::onlinePlayer)
                            .ifPresent(other -> this.notices.create()
                                    .viewer(other)
                                    .notice(messages -> messages.friendRemovedYou)
                                    .formatter(new Formatter().register("{PLAYER}", player.getUsername()))
                                    .send());
                })
                .exceptionally(this.report(player, "remove " + target));
    }

    @Execute(name = "lista", aliases = {"list"})
    void list(@Context Player player) {
        this.friends.list(player.getUniqueId())
                .thenAccept(friends -> {
                    if (friends.isEmpty()) {
                        this.notices.create().viewer(player).notice(messages -> messages.friendListEmpty).send();
                        return;
                    }

                    StringJoiner online = new StringJoiner(", ");
                    StringJoiner offline = new StringJoiner(", ");
                    for (FriendProfile friend : friends) {
                        if (this.friends.isVisiblyOnline(player, friend.playerId())) {
                            online.add(friend.displayName()
                                    + this.friends.serverOf(player, friend.playerId())
                                            .map(server -> " (" + server + ")")
                                            .orElse(""));
                        }
                        else {
                            offline.add(friend.displayName());
                        }
                    }

                    this.notices.create()
                            .viewer(player)
                            .notice(messages -> messages.friendList)
                            .formatter(new Formatter()
                                    .register("{COUNT}", Integer.toString(friends.size()))
                                    .register("{ONLINE}", online.length() == 0 ? "-" : online.toString())
                                    .register("{OFFLINE}", offline.length() == 0 ? "-" : offline.toString()))
                            .send();
                })
                .exceptionally(this.report(player, "list"));
    }

    @Execute(name = "zaproszenia", aliases = {"requests", "pending"})
    void pending(@Context Player player) {
        this.friends.pendingRequests(player.getUniqueId())
                .thenAccept(requests -> {
                    if (requests.isEmpty()) {
                        this.notices.create()
                                .viewer(player)
                                .notice(messages -> messages.friendNoPendingRequests)
                                .send();
                        return;
                    }

                    StringJoiner names = new StringJoiner(", ");
                    requests.forEach(request -> names.add(request.displayName()));

                    this.notices.create()
                            .viewer(player)
                            .notice(messages -> messages.friendPendingRequests)
                            .formatter(new Formatter().register("{PLAYERS}", names.toString()))
                            .send();
                })
                .exceptionally(this.report(player, "pending"));
    }

    private void replyToInvite(Player player, FriendService.RequestOutcome outcome) {
        String name = outcome.target().displayName();

        this.notices.create()
                .viewer(player)
                .notice(messages -> switch (outcome.result()) {
                    case SENT -> messages.friendRequestSent;
                    case ALREADY_SENT -> messages.friendRequestAlreadySent;
                    case ACCEPTED_INSTEAD -> messages.friendRequestAcceptedInstead;
                    case ALREADY_FRIENDS -> messages.friendAlreadyFriends;
                    case LIST_FULL -> messages.friendListFull;
                    case SELF -> messages.friendSelf;
                    case UNKNOWN_PLAYER -> messages.friendUnknownPlayer;
                    case FAILED -> messages.friendFailed;
                })
                .formatter(new Formatter()
                        .register("{PLAYER}", name)
                        .register("{LIMIT}", Integer.toString(this.config.friends.maxFriends)))
                .send();

        if (outcome.result() == FriendService.RequestResult.SENT) {
            this.friends.onlinePlayer(outcome.target().playerId()).ifPresent(target -> this.notices.create()
                    .viewer(target)
                    .notice(messages -> messages.friendRequestReceived)
                    .formatter(new Formatter().register("{PLAYER}", player.getUsername()))
                    .send());
        }
        if (outcome.result() == FriendService.RequestResult.ACCEPTED_INSTEAD) {
            this.notifyAccepted(player, outcome.target());
        }
    }

    private void replyToAccept(Player player, FriendService.AcceptResult result, String requesterName) {
        String name = result.requester().name() == null ? requesterName : result.requester().displayName();

        this.notices.create()
                .viewer(player)
                .notice(messages -> switch (result.outcome()) {
                    case ACCEPTED -> messages.friendRequestAccepted;
                    case NO_REQUEST -> messages.friendNoRequest;
                    case ALREADY_FRIENDS -> messages.friendAlreadyFriends;
                    case REQUESTER_FULL -> messages.friendOtherListFull;
                    case ACCEPTER_FULL -> messages.friendListFull;
                })
                .formatter(new Formatter()
                        .register("{PLAYER}", name)
                        .register("{LIMIT}", Integer.toString(this.config.friends.maxFriends)))
                .send();

        if (result.outcome() == AcceptOutcome.ACCEPTED) {
            this.notifyAccepted(player, result.requester());
        }
    }

    /** Tells the other side their invitation went through, when they are around to hear it. */
    private void notifyAccepted(Player accepter, FriendProfile requester) {
        this.friends.onlinePlayer(requester.playerId()).ifPresent(other -> this.notices.create()
                .viewer(other)
                .notice(messages -> messages.friendRequestAcceptedByOther)
                .formatter(new Formatter().register("{PLAYER}", accepter.getUsername()))
                .send());
    }

    /** Logs the cause and tells the player once; a stack trace is not an answer to a command. */
    private Function<Throwable, Void> report(Player player, String what) {
        return throwable -> {
            this.logger.error("Friend command failed for {} ({})", player.getUsername(), what, throwable);
            this.notices.create().viewer(player).notice(messages -> messages.friendFailed).send();
            return null;
        };
    }
}
