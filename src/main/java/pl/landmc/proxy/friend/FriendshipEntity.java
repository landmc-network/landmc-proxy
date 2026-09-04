package pl.landmc.proxy.friend;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.UUID;

/**
 * One friendship, stored once rather than once per direction.
 *
 * <p>The two ids are sorted before they are written, and the pair key is built from them in that
 * order. That is what makes the friendship a single row with a primary key on it: "A befriends
 * B" and "B befriends A" produce the same key, so the database refuses the duplicate instead of
 * the code having to check for it first.
 *
 * <p>Both columns are indexed because the only query that matters asks for one player's
 * friends, and either column can be the one that matches.
 */
@DatabaseTable(tableName = "friendships")
public class FriendshipEntity {

    @DatabaseField(id = true, columnName = "pair_key", width = 73)
    public String pairKey;

    @DatabaseField(columnName = "player_a", canBeNull = false, index = true)
    public UUID playerA;

    @DatabaseField(columnName = "player_b", canBeNull = false, index = true)
    public UUID playerB;

    @DatabaseField(columnName = "since")
    public long since;

    /** Required by ORMLite. */
    public FriendshipEntity() {
    }

    FriendshipEntity(UUID first, UUID second, long since) {
        UUID low = order(first, second) ? first : second;
        UUID high = order(first, second) ? second : first;

        this.pairKey = pairKey(first, second);
        this.playerA = low;
        this.playerB = high;
        this.since = since;
    }

    /** The stable key for a pair, in either order. */
    static String pairKey(UUID first, UUID second) {
        return order(first, second) ? first + ":" + second : second + ":" + first;
    }

    /** The other player in this friendship. */
    UUID otherThan(UUID playerId) {
        return this.playerA.equals(playerId) ? this.playerB : this.playerA;
    }

    private static boolean order(UUID first, UUID second) {
        return first.toString().compareTo(second.toString()) <= 0;
    }
}
