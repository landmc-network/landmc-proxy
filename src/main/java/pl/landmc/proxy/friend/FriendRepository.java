package pl.landmc.proxy.friend;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.Where;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import pl.landmc.platform.database.DatabaseService;

/**
 * Reads and writes friendships. The only class in the proxy that knows ORMLite exists.
 *
 * <p>Every method here is blocking and runs on a database worker thread - callers reach it
 * through {@code DatabaseService#supplyAsync}, never from an event thread. The original ran all
 * of this inline on the thread handling the command, which on a proxy is a Netty thread: one
 * slow query and the proxy stops forwarding packets for everybody.
 *
 * <p>Lives here rather than in {@code platform-database} for the same reason the lobby's
 * repository lives in the lobby: the platform provides the connection, the plugin owns what is
 * stored in it.
 */
public final class FriendRepository {

    private final DatabaseService database;

    public FriendRepository(DatabaseService database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** Creates the tables on first start. Startup work, blocking. */
    public void createTables() {
        this.database.createTables(
                FriendshipEntity.class, FriendRequestEntity.class, FriendProfileEntity.class);
    }

    /** Records that a player was seen, so an offline friend can still be shown by name. */
    public void touchProfile(UUID playerId, String name, long now) throws SQLException {
        this.profiles().createOrUpdate(new FriendProfileEntity(playerId, name, now));
    }

    /**
     * Finds a player by name, whether or not they are online.
     *
     * <p>Case-insensitively, because a player typing a friend's name has no reason to match the
     * capitalisation, and Minecraft names are unique regardless of case.
     */
    public Optional<FriendProfile> findProfileByName(String name) throws SQLException {
        List<FriendProfileEntity> matches = this.profiles().queryBuilder()
                .limit(1L)
                .where()
                .raw("LOWER(name) = ?",
                        new SelectArg(SqlType.STRING, name.toLowerCase(Locale.ROOT)))
                .query();

        return matches.isEmpty()
                ? Optional.empty()
                : Optional.of(new FriendProfile(matches.getFirst().playerId, matches.getFirst().name));
    }

    /** How many friends a player has; used to enforce the limit without loading the list. */
    public long countFriends(UUID playerId) throws SQLException {
        QueryBuilder<FriendshipEntity, String> builder = this.friendships().queryBuilder();
        builder.setCountOf(true);
        return builder.where()
                .eq("player_a", playerId)
                .or()
                .eq("player_b", playerId)
                .countOf();
    }

    /**
     * A player's friends, with the names they were last seen under.
     *
     * <p>Two queries rather than a join: ORMLite's join support would tie the entities together
     * for a list that is at most a few dozen rows, and the second query is a single lookup by
     * primary key per friend.
     */
    public List<FriendProfile> listFriends(UUID playerId) throws SQLException {
        List<FriendshipEntity> rows = this.friendships().queryBuilder()
                .where()
                .eq("player_a", playerId)
                .or()
                .eq("player_b", playerId)
                .query();

        Dao<FriendProfileEntity, UUID> profiles = this.profiles();
        List<FriendProfile> friends = new ArrayList<>(rows.size());
        for (FriendshipEntity row : rows) {
            UUID friendId = row.otherThan(playerId);
            FriendProfileEntity profile = profiles.queryForId(friendId);
            friends.add(new FriendProfile(friendId, profile == null ? null : profile.name));
        }
        return friends;
    }

    public boolean areFriends(UUID first, UUID second) throws SQLException {
        return this.friendships().idExists(FriendshipEntity.pairKey(first, second));
    }

    /** @return false when the same invitation already exists */
    public boolean createRequest(UUID requester, UUID requestee, long now) throws SQLException {
        Dao<FriendRequestEntity, String> requests = this.requests();
        if (requests.idExists(FriendRequestEntity.requestKey(requester, requestee))) {
            return false;
        }
        return requests.create(new FriendRequestEntity(requester, requestee, now)) == 1;
    }

    public boolean hasRequest(UUID requester, UUID requestee) throws SQLException {
        return this.requests().idExists(FriendRequestEntity.requestKey(requester, requestee));
    }

    /**
     * Turns an invitation into a friendship, or reports why it could not.
     *
     * <p>One transaction: an accepted invitation that was deleted without the friendship being
     * written would leave both players believing the other had refused.
     */
    public AcceptOutcome acceptRequest(UUID requester, UUID requestee, int maxFriends, long now)
            throws SQLException {

        return TransactionManager.callInTransaction(
                this.database.connectionSource(),
                () -> this.acceptInTransaction(requester, requestee, maxFriends, now));
    }

    private AcceptOutcome acceptInTransaction(UUID requester, UUID requestee, int maxFriends, long now)
            throws SQLException {

        Dao<FriendRequestEntity, String> requests = this.requests();
        String key = FriendRequestEntity.requestKey(requester, requestee);
        if (!requests.idExists(key)) {
            return AcceptOutcome.NO_REQUEST;
        }

        if (this.areFriends(requester, requestee)) {
            // Already friends, so the invitation is stale rather than valid; drop it quietly.
            requests.deleteById(key);
            return AcceptOutcome.ALREADY_FRIENDS;
        }

        if (this.countFriends(requester) >= maxFriends) {
            return AcceptOutcome.REQUESTER_FULL;
        }
        if (this.countFriends(requestee) >= maxFriends) {
            return AcceptOutcome.ACCEPTER_FULL;
        }

        requests.deleteById(key);
        // The invitation in the other direction, if both invited each other, is now moot.
        requests.deleteById(FriendRequestEntity.requestKey(requestee, requester));
        this.friendships().create(new FriendshipEntity(requester, requestee, now));
        return AcceptOutcome.ACCEPTED;
    }

    /** @return false when there was no invitation to refuse */
    public boolean deleteRequest(UUID requester, UUID requestee) throws SQLException {
        return this.requests().deleteById(FriendRequestEntity.requestKey(requester, requestee)) == 1;
    }

    /** Everyone who has invited this player and is still waiting. */
    public List<FriendProfile> listIncomingRequests(UUID playerId) throws SQLException {
        List<FriendRequestEntity> rows = this.requests().queryBuilder()
                .where()
                .eq("requestee", playerId)
                .query();

        Dao<FriendProfileEntity, UUID> profiles = this.profiles();
        List<FriendProfile> pending = new ArrayList<>(rows.size());
        for (FriendRequestEntity row : rows) {
            FriendProfileEntity profile = profiles.queryForId(row.requester);
            pending.add(new FriendProfile(row.requester, profile == null ? null : profile.name));
        }
        return pending;
    }

    /** @return false when the two were not friends, so nobody is told they were removed */
    public boolean removeFriend(UUID first, UUID second) throws SQLException {
        return this.friendships().deleteById(FriendshipEntity.pairKey(first, second)) == 1;
    }

    /**
     * Drops invitations older than the configured lifetime.
     *
     * <p>Run at startup rather than on a timer: invitations expire in days, and a proxy that
     * runs for weeks without a restart accumulating a few extra rows is not worth a scheduled
     * task.
     *
     * @return how many were removed, for the startup log
     */
    public int deleteExpiredRequests(long oldestAllowed) throws SQLException {
        DeleteBuilder<FriendRequestEntity, String> builder = this.requests().deleteBuilder();
        Where<FriendRequestEntity, String> where = builder.where();
        where.lt("created_at", oldestAllowed);
        return builder.delete();
    }

    private Dao<FriendshipEntity, String> friendships() {
        return this.database.dao(FriendshipEntity.class);
    }

    private Dao<FriendRequestEntity, String> requests() {
        return this.database.dao(FriendRequestEntity.class);
    }

    private Dao<FriendProfileEntity, UUID> profiles() {
        return this.database.dao(FriendProfileEntity.class);
    }

    /** How {@link #acceptRequest} ended. */
    public enum AcceptOutcome {
        ACCEPTED,
        NO_REQUEST,
        ALREADY_FRIENDS,
        /** The player who sent the invitation has since filled their list. */
        REQUESTER_FULL,
        ACCEPTER_FULL
    }

    /** A player as the friends list knows them: an id, and the last name they were seen under. */
    public record FriendProfile(UUID playerId, String name) {

        /** The name to show; falls back to the id for somebody who has never been seen. */
        public String displayName() {
            return this.name == null || this.name.isBlank() ? this.playerId.toString() : this.name;
        }
    }
}
