package pl.landmc.proxy.friend;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import pl.landmc.platform.database.DatabaseService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.friend.FriendRepository.AcceptOutcome;
import pl.landmc.proxy.friend.FriendRepository.FriendProfile;
import pl.landmc.proxy.vanish.VanishProvider;

/**
 * The friends list, as the rest of the proxy sees it.
 *
 * <p>Everything returns a future and nothing blocks. The repository is reached through the
 * platform's bounded database executor, so a slow query costs a database worker rather than the
 * thread Velocity uses to forward packets - which is what the original spent.
 *
 * <p>Two rules the original had no room for, both of which it needed:
 *
 * <ul>
 *   <li>a friends list has a maximum size, checked inside the transaction that would grow it;
 *   <li>an invitation expires, so the table of pending invitations stops growing.
 * </ul>
 *
 * <p>Offline players are first-class here. Names are resolved through the profile table, so a
 * friend who is not online is shown by name and can be invited or removed - the original could
 * only work with players who happened to be connected, and printed raw UUIDs for the rest.
 */
public final class FriendService {

    private final ProxyServer proxy;
    private final FriendRepository repository;
    private final DatabaseService database;
    private final ProxyConfig config;
    private final VanishProvider vanish;
    private final Logger logger;

    public FriendService(
            ProxyServer proxy,
            FriendRepository repository,
            DatabaseService database,
            ProxyConfig config,
            VanishProvider vanish,
            Logger logger) {

        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.database = Objects.requireNonNull(database, "database");
        this.config = Objects.requireNonNull(config, "config");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Creates the tables and sweeps invitations nobody ever answered.
     *
     * <p>Blocking, and called from the bootstrap: a proxy that comes up before its tables exist
     * would answer the first {@code /friend} with an internal error.
     */
    public void start() {
        this.repository.createTables();

        try {
            long oldestAllowed = System.currentTimeMillis()
                    - Duration.ofDays(Math.max(1, this.config.friends.requestExpiryDays)).toMillis();
            int removed = this.repository.deleteExpiredRequests(oldestAllowed);
            if (removed > 0) {
                this.logger.info("Removed {} friend request(s) older than {} day(s).",
                        removed, this.config.friends.requestExpiryDays);
            }
        }
        catch (java.sql.SQLException exception) {
            // Housekeeping: worth a line in the log, not worth refusing to start over.
            this.logger.warn("Could not sweep expired friend requests", exception);
        }
    }

    /** Remembers the name a player is currently using, so friends can see it while they are away. */
    public void onJoin(Player player) {
        UUID playerId = player.getUniqueId();
        String name = player.getUsername();

        this.database.runAsync(() -> this.repository.touchProfile(playerId, name, System.currentTimeMillis()))
                .exceptionally(throwable -> {
                    this.logger.warn("Could not record the profile of {}", name, throwable);
                    return null;
                });
    }

    /** Invites somebody, online or not. */
    public CompletableFuture<RequestOutcome> invite(Player sender, String targetName) {
        UUID senderId = sender.getUniqueId();

        return this.database.supplyAsync(() -> {
            Optional<FriendProfile> target = this.resolve(targetName);
            if (target.isEmpty()) {
                return RequestOutcome.unknownPlayer(targetName);
            }

            FriendProfile profile = target.get();
            if (profile.playerId().equals(senderId)) {
                return RequestOutcome.of(RequestResult.SELF, profile);
            }
            if (this.repository.areFriends(senderId, profile.playerId())) {
                return RequestOutcome.of(RequestResult.ALREADY_FRIENDS, profile);
            }
            if (this.repository.countFriends(senderId) >= this.maxFriends()) {
                return RequestOutcome.of(RequestResult.LIST_FULL, profile);
            }

            // They invited us first: accepting is what the player meant, and making them type a
            // second command to reach the same state is a worse answer than just doing it.
            if (this.repository.hasRequest(profile.playerId(), senderId)) {
                AcceptOutcome accepted = this.repository.acceptRequest(
                        profile.playerId(), senderId, this.maxFriends(), System.currentTimeMillis());
                return RequestOutcome.of(
                        accepted == AcceptOutcome.ACCEPTED ? RequestResult.ACCEPTED_INSTEAD : RequestResult.FAILED,
                        profile);
            }

            boolean created = this.repository.createRequest(
                    senderId, profile.playerId(), System.currentTimeMillis());
            return RequestOutcome.of(created ? RequestResult.SENT : RequestResult.ALREADY_SENT, profile);
        });
    }

    /** Accepts an invitation from the named player. */
    public CompletableFuture<AcceptResult> accept(Player accepter, String requesterName) {
        UUID accepterId = accepter.getUniqueId();

        return this.database.supplyAsync(() -> {
            Optional<FriendProfile> requester = this.resolve(requesterName);
            if (requester.isEmpty()) {
                return new AcceptResult(AcceptOutcome.NO_REQUEST, new FriendProfile(accepterId, requesterName));
            }

            AcceptOutcome outcome = this.repository.acceptRequest(
                    requester.get().playerId(), accepterId, this.maxFriends(), System.currentTimeMillis());
            return new AcceptResult(outcome, requester.get());
        });
    }

    /** Refuses an invitation; reports false when there was nothing to refuse. */
    public CompletableFuture<Optional<FriendProfile>> decline(Player player, String requesterName) {
        UUID playerId = player.getUniqueId();

        return this.database.supplyAsync(() -> {
            Optional<FriendProfile> requester = this.resolve(requesterName);
            if (requester.isEmpty() || !this.repository.deleteRequest(requester.get().playerId(), playerId)) {
                return Optional.<FriendProfile>empty();
            }
            return requester;
        });
    }

    /**
     * Removes a friend.
     *
     * <p>The result says whether they were friends at all. Telling somebody "you were removed
     * from a friends list" when they never were on one is a way to send a message to a player
     * who has not agreed to receive any.
     */
    public CompletableFuture<Optional<FriendProfile>> remove(Player player, String targetName) {
        UUID playerId = player.getUniqueId();

        return this.database.supplyAsync(() -> {
            Optional<FriendProfile> target = this.resolve(targetName);
            if (target.isEmpty() || !this.repository.removeFriend(playerId, target.get().playerId())) {
                return Optional.<FriendProfile>empty();
            }
            return target;
        });
    }

    /** The player's friends. Online state is filled in by the caller, on its own thread. */
    public CompletableFuture<List<FriendProfile>> list(UUID playerId) {
        return this.database.supplyAsync(() -> this.repository.listFriends(playerId));
    }

    public CompletableFuture<List<FriendProfile>> pendingRequests(UUID playerId) {
        return this.database.supplyAsync(() -> this.repository.listIncomingRequests(playerId));
    }

    /**
     * Whether a friend should be shown as online.
     *
     * <p>Reads the proxy's own player list, so it is called from the thread that renders the
     * answer rather than from a database worker. A hidden moderator reads as offline: a friends
     * list that shows them defeats the vanish as surely as {@code /msg} would.
     */
    public boolean isVisiblyOnline(Player viewer, UUID friendId) {
        return this.proxy.getPlayer(friendId)
                .filter(friend -> this.vanish.canSee(viewer, friend))
                .isPresent();
    }

    /** The connected player with this id, if any. */
    public Optional<Player> onlinePlayer(UUID playerId) {
        return this.proxy.getPlayer(playerId);
    }

    /** The server a friend is on, when the viewer may know. */
    public Optional<String> serverOf(Player viewer, UUID friendId) {
        return this.proxy.getPlayer(friendId)
                .filter(friend -> this.vanish.canSee(viewer, friend))
                .flatMap(Player::getCurrentServer)
                .map(connection -> connection.getServerInfo().getName());
    }

    /**
     * A player by name, preferring whoever is connected right now.
     *
     * <p>An online player is authoritative: the profile table records the last name seen, and a
     * player who changed names would otherwise be found under the old one.
     */
    private Optional<FriendProfile> resolve(String name) throws java.sql.SQLException {
        Optional<Player> online = this.proxy.getPlayer(name);
        if (online.isPresent()) {
            return Optional.of(new FriendProfile(online.get().getUniqueId(), online.get().getUsername()));
        }
        return this.repository.findProfileByName(name);
    }

    private int maxFriends() {
        return Math.max(1, this.config.friends.maxFriends);
    }

    /** What came of an invitation. */
    public enum RequestResult {
        SENT,
        /** The same invitation was already waiting. */
        ALREADY_SENT,
        /** They had invited us first, so this accepted instead. */
        ACCEPTED_INSTEAD,
        ALREADY_FRIENDS,
        LIST_FULL,
        SELF,
        UNKNOWN_PLAYER,
        FAILED
    }

    /** An invitation's result together with who it was about. */
    public record RequestOutcome(RequestResult result, FriendProfile target) {

        static RequestOutcome of(RequestResult result, FriendProfile target) {
            return new RequestOutcome(result, target);
        }

        static RequestOutcome unknownPlayer(String name) {
            return new RequestOutcome(RequestResult.UNKNOWN_PLAYER, new FriendProfile(new UUID(0L, 0L), name));
        }
    }

    /** An acceptance's result together with who invited. */
    public record AcceptResult(AcceptOutcome outcome, FriendProfile requester) {
    }
}
