package pl.landmc.proxy.friend;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.UUID;

/**
 * The last name a player was seen under, and when.
 *
 * <p>A friends list is mostly a list of people who are not online, and a friendship stores ids.
 * Without this table an offline friend can only be shown as a UUID - which is what the original
 * did, despite having a table exactly like this one that nothing ever read from.
 *
 * <p>The name is indexed because inviting somebody who is offline means looking them up by it.
 */
@DatabaseTable(tableName = "friend_profiles")
public class FriendProfileEntity {

    @DatabaseField(id = true, columnName = "player_id")
    public UUID playerId;

    @DatabaseField(columnName = "name", canBeNull = false, width = 16, index = true)
    public String name;

    @DatabaseField(columnName = "last_seen")
    public long lastSeen;

    /** Required by ORMLite. */
    public FriendProfileEntity() {
    }

    FriendProfileEntity(UUID playerId, String name, long lastSeen) {
        this.playerId = playerId;
        this.name = name;
        this.lastSeen = lastSeen;
    }
}
