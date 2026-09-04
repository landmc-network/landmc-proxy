package pl.landmc.proxy.friend;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.UUID;

/**
 * A pending invitation, which unlike a friendship has a direction.
 *
 * <p>The key is {@code requester:requestee} so a second invitation to the same player cannot
 * create a second row, and {@code createdAt} is indexed because expired invitations are swept
 * by age - the original had no expiry at all, so its table only ever grew.
 */
@DatabaseTable(tableName = "friend_requests")
public class FriendRequestEntity {

    @DatabaseField(id = true, columnName = "request_key", width = 73)
    public String requestKey;

    @DatabaseField(columnName = "requester", canBeNull = false, index = true)
    public UUID requester;

    @DatabaseField(columnName = "requestee", canBeNull = false, index = true)
    public UUID requestee;

    @DatabaseField(columnName = "created_at", index = true)
    public long createdAt;

    /** Required by ORMLite. */
    public FriendRequestEntity() {
    }

    FriendRequestEntity(UUID requester, UUID requestee, long createdAt) {
        this.requestKey = requestKey(requester, requestee);
        this.requester = requester;
        this.requestee = requestee;
        this.createdAt = createdAt;
    }

    static String requestKey(UUID requester, UUID requestee) {
        return requester + ":" + requestee;
    }
}
