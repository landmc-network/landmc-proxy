package pl.landmc.proxy.voucher;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.UUID;

/**
 * One voucher code.
 *
 * <p>The code is the primary key, which is what makes redeeming it a lookup rather than a scan,
 * and what makes a duplicate code impossible rather than merely unlikely.
 *
 * <p>Everything here goes through ORMLite's parameter binding. The original built these queries
 * by concatenating the code a player typed straight into the SQL - {@code WHERE code = '" + code
 * + "'"} - from a command any player could run.
 */
@DatabaseTable(tableName = "vouchers")
public class VoucherEntity {

    @DatabaseField(id = true, columnName = "code", width = 32)
    public String code;

    @DatabaseField(canBeNull = false, columnName = "type", width = 32, index = true)
    public String type;

    /** Who it was issued for, lower-cased; null when anyone may redeem it. */
    @DatabaseField(columnName = "assigned_to", width = 16, index = true)
    public String assignedTo;

    @DatabaseField(canBeNull = false, columnName = "issued_by", width = 32)
    public String issuedBy;

    @DatabaseField(columnName = "issued_at")
    public long issuedAt;

    @DatabaseField(columnName = "redeemed_by", width = 16)
    public String redeemedBy;

    @DatabaseField(columnName = "redeemed_by_id")
    public UUID redeemedById;

    /** Zero while unused; indexed because that is the column every redeem checks. */
    @DatabaseField(columnName = "redeemed_at", index = true)
    public long redeemedAt;

    /** Required by ORMLite. */
    public VoucherEntity() {
    }

    VoucherEntity(String code, String type, String assignedTo, String issuedBy, long issuedAt) {
        this.code = code;
        this.type = type;
        this.assignedTo = assignedTo;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
    }

    boolean isRedeemed() {
        return this.redeemedAt > 0L;
    }
}
