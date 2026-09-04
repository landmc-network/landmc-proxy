package pl.landmc.proxy.voucher;

import com.j256.ormlite.dao.Dao;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import pl.landmc.platform.database.DatabaseService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyConfig.VoucherReward;

/**
 * Issues and redeems voucher codes.
 *
 * <p>A voucher is a code somebody types once for a reward. Which reward is a matter of
 * configuration - each type names the commands the console runs - so adding "a week of VIP" or
 * "500 coins" is an edit to config.yml rather than a class here. The original hard-coded eleven
 * types in an enum, which meant a new reward needed a release.
 *
 * <p>Redeeming is one atomic update, not a read followed by a write. Two players racing on the
 * same code - which is what happens the moment one is posted publicly - would otherwise both
 * pass the "is it used?" check and both be rewarded.
 */
public final class VoucherService {

    /**
     * The alphabet codes are drawn from.
     *
     * <p>No {@code O}, {@code 0}, {@code I} or {@code 1}: these are read off a stream, a video
     * or a printed card and typed back in, and those four are what people get wrong.
     */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int CODE_LENGTH = 12;

    /** How many times to retry when a generated code already exists. */
    private static final int GENERATION_ATTEMPTS = 5;

    private final DatabaseService database;
    private final ProxyConfig config;
    private final SecureRandom random = new SecureRandom();

    /** When each player may use the command again; cleared as it is read. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public VoucherService(DatabaseService database, ProxyConfig config) {
        this.database = Objects.requireNonNull(database, "database");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Creates the table on first start. Startup work, blocking. */
    public void createTables() {
        this.database.createTables(VoucherEntity.class);
    }

    /** The reward definitions, by name. */
    public Map<String, VoucherReward> types() {
        return this.config.vouchers.types;
    }

    /**
     * Seconds the player still has to wait, or zero.
     *
     * <p>The rate limit exists because the command is a guessing oracle otherwise: without it,
     * codes can be tried as fast as the network allows.
     */
    public long remainingCooldownSeconds(UUID playerId) {
        Long until = this.cooldowns.get(playerId);
        if (until == null) {
            return 0L;
        }

        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            this.cooldowns.remove(playerId, until);
            return 0L;
        }
        return Math.max(1L, (remaining + 999L) / 1_000L);
    }

    public void startCooldown(UUID playerId) {
        this.cooldowns.put(
                playerId,
                System.currentTimeMillis()
                        + Duration.ofSeconds(Math.max(1, this.config.vouchers.cooldownSeconds)).toMillis());
    }

    /** Forgets a player who left, so the map does not grow with everyone who ever tried a code. */
    public void onDisconnect(UUID playerId) {
        this.cooldowns.remove(playerId);
    }

    /**
     * Generates codes of one type.
     *
     * @param assignedTo the only player who may redeem them, or null for anybody
     * @return the codes, in the order they were created
     */
    public CompletableFuture<List<String>> issue(
            String type, @Nullable String assignedTo, int count, String issuedBy) {

        String normalisedTarget = assignedTo == null ? null : assignedTo.toLowerCase(Locale.ROOT);

        return this.database.supplyAsync(() -> {
            Dao<VoucherEntity, String> dao = this.dao();
            List<String> codes = new ArrayList<>(count);
            long now = System.currentTimeMillis();

            for (int index = 0; index < count; index++) {
                codes.add(this.createUnique(dao, type, normalisedTarget, issuedBy, now));
            }
            return codes;
        });
    }

    /**
     * Redeems a code for a player.
     *
     * <p>The claim is an {@code UPDATE ... WHERE redeemed_at = 0}, so whoever the database
     * applies first is the one who gets it. A read followed by a write would hand the same
     * reward to everybody who typed the code in the same second.
     */
    public CompletableFuture<RedeemResult> redeem(UUID playerId, String playerName, String code) {
        String normalised = code.trim().toUpperCase(Locale.ROOT);

        return this.database.supplyAsync(() -> {
            Dao<VoucherEntity, String> dao = this.dao();

            VoucherEntity voucher = dao.queryForId(normalised);
            if (voucher == null) {
                return RedeemResult.unknown();
            }
            if (voucher.isRedeemed()) {
                return RedeemResult.alreadyUsed(voucher.redeemedBy);
            }
            if (voucher.assignedTo != null
                    && !voucher.assignedTo.equalsIgnoreCase(playerName)) {
                // Deliberately the same answer as an unknown code: telling somebody that a code
                // exists but is not theirs turns a wrong guess into a confirmed hit.
                return RedeemResult.unknown();
            }

            VoucherReward reward = this.config.vouchers.types.get(voucher.type);
            if (reward == null) {
                return RedeemResult.unknownType(voucher.type);
            }

            if (!this.claim(dao, normalised, playerId, playerName)) {
                return RedeemResult.alreadyUsed(null);
            }

            return RedeemResult.redeemed(voucher.type, reward);
        });
    }

    /** How many of a player's assigned codes are still waiting, for {@code /voucher}. */
    public CompletableFuture<Long> unusedFor(String playerName) {
        String normalised = playerName.toLowerCase(Locale.ROOT);

        return this.database.supplyAsync(() -> this.dao().queryBuilder()
                .setCountOf(true)
                .where()
                .eq("assigned_to", normalised)
                .and()
                .eq("redeemed_at", 0L)
                .countOf());
    }

    /**
     * Marks the code used, and reports whether this call is the one that did it.
     *
     * @return false when somebody else got there first
     */
    private boolean claim(Dao<VoucherEntity, String> dao, String code, UUID playerId, String playerName)
            throws SQLException {

        com.j256.ormlite.stmt.UpdateBuilder<VoucherEntity, String> builder = dao.updateBuilder();
        builder.updateColumnValue("redeemed_at", System.currentTimeMillis());
        builder.updateColumnValue("redeemed_by", playerName);
        builder.updateColumnValue("redeemed_by_id", playerId);
        builder.where().idEq(code).and().eq("redeemed_at", 0L);

        return builder.update() == 1;
    }

    private String createUnique(
            Dao<VoucherEntity, String> dao,
            String type,
            @Nullable String assignedTo,
            String issuedBy,
            long now) throws SQLException {

        for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
            String code = this.randomCode();
            // The primary key is what actually guarantees uniqueness; this only avoids an
            // exception in the vanishingly rare case of a collision.
            if (!dao.idExists(code)) {
                dao.create(new VoucherEntity(code, type, assignedTo, issuedBy, now));
                return code;
            }
        }

        throw new SQLException("Could not generate a unique voucher code after "
                + GENERATION_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH + 2);
        for (int index = 0; index < CODE_LENGTH; index++) {
            if (index > 0 && index % 4 == 0) {
                // Grouped, because a code is read aloud and typed back in.
                code.append('-');
            }
            code.append(ALPHABET[this.random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    private Dao<VoucherEntity, String> dao() {
        return this.database.dao(VoucherEntity.class);
    }

    /** What came of a redeem attempt. */
    public record RedeemResult(
            Outcome outcome,
            @Nullable String type,
            @Nullable VoucherReward reward,
            @Nullable String detail) {

        public enum Outcome {
            REDEEMED,
            /** No such code, or one that is not this player's - answered identically. */
            UNKNOWN,
            ALREADY_USED,
            /** The code names a reward type that is no longer in the configuration. */
            UNKNOWN_TYPE
        }

        static RedeemResult redeemed(String type, VoucherReward reward) {
            return new RedeemResult(Outcome.REDEEMED, type, reward, null);
        }

        static RedeemResult unknown() {
            return new RedeemResult(Outcome.UNKNOWN, null, null, null);
        }

        static RedeemResult alreadyUsed(@Nullable String by) {
            return new RedeemResult(Outcome.ALREADY_USED, null, null, by);
        }

        static RedeemResult unknownType(String type) {
            return new RedeemResult(Outcome.UNKNOWN_TYPE, type, null, type);
        }
    }
}
