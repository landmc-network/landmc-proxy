package pl.landmc.proxy.live;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * A registered streamer, as stored. Nothing outside {@link LiveRepository} touches this.
 *
 * <p>Keyed by the lower-cased Minecraft name, the same way accounts are: a profile is looked up
 * by whoever ran the command, and a name is what both sides of that have in common.
 *
 * <p>The platform and the channel identifier are stored separately rather than as one URL. The
 * identifier is what an API is asked about and the URL is what a player clicks; keeping only the
 * URL means re-parsing it on every use, and a parser that runs on stored data is a parser whose
 * failure is a broken row rather than a rejected command.
 */
@DatabaseTable(tableName = "live_profiles")
public class LiveProfileEntity {

    @DatabaseField(id = true, columnName = "name", width = 16)
    public String name;

    /** The capitalisation the player uses, for showing them in a list. */
    @DatabaseField(canBeNull = false, columnName = "display_name", width = 16)
    public String displayName;

    @DatabaseField(canBeNull = false, columnName = "platform", width = 16)
    public String platform;

    @DatabaseField(canBeNull = false, columnName = "identifier", width = 64)
    public String identifier;

    @DatabaseField(columnName = "added_by", width = 32)
    public String addedBy;

    @DatabaseField(columnName = "added_at")
    public long addedAt;

    /** Required by ORMLite. */
    public LiveProfileEntity() {
    }
}
