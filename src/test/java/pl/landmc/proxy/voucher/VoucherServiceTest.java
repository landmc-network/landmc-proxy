package pl.landmc.proxy.voucher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import pl.landmc.platform.database.DatabaseConfig;
import pl.landmc.platform.database.DatabaseService;
import pl.landmc.platform.database.DatabaseType;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.voucher.VoucherService.RedeemResult;

/**
 * Runs against a real embedded database, because the thing most worth testing is what the
 * database does under a race: a code posted publicly is typed by several people at once, and a
 * voucher that pays out twice is a voucher that costs real money.
 */
class VoucherServiceTest {

    private DatabaseService database;
    private VoucherService service;
    private ProxyConfig config;

    @BeforeEach
    void openDatabase(@TempDir Path directory) {
        this.config = new ProxyConfig();
        this.config.vouchers.enabled = true;
        this.config.database.type = DatabaseType.H2;
        this.config.database.fileName = "vouchers-test";
        this.config.database.poolSize = 4;

        this.database = new DatabaseService(
                "vouchers-test", this.config.database, directory, NOPLogger.NOP_LOGGER);
        this.database.enable();

        this.service = new VoucherService(this.database, this.config);
        this.service.createTables();
    }

    @AfterEach
    void closeDatabase() {
        if (this.database != null) {
            this.database.close();
        }
    }

    @Test
    @DisplayName("a code is redeemed once and names its reward")
    void redeemsAValidCode() throws Exception {
        String code = await(this.service.issue("vip7", null, 1, "Admin")).getFirst();

        RedeemResult result = await(this.service.redeem(UUID.randomUUID(), "Crispi", code));

        assertEquals(RedeemResult.Outcome.REDEEMED, result.outcome());
        assertEquals("vip7", result.type());
        assertEquals("Ranga VIP na 7 dni", result.reward().name);
    }

    @Test
    @DisplayName("the same code cannot be redeemed twice")
    void refusesASecondRedeem() throws Exception {
        String code = await(this.service.issue("vip7", null, 1, "Admin")).getFirst();
        await(this.service.redeem(UUID.randomUUID(), "Crispi", code));

        assertEquals(
                RedeemResult.Outcome.ALREADY_USED,
                await(this.service.redeem(UUID.randomUUID(), "Ktos", code)).outcome());
    }

    @Test
    @DisplayName("twenty players racing on one code produce exactly one winner")
    void survivesARace() throws Exception {
        String code = await(this.service.issue("vip7", null, 1, "Admin")).getFirst();

        int racers = 20;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger redeemed = new AtomicInteger();
        List<Thread> threads = new ArrayList<>(racers);

        for (int index = 0; index < racers; index++) {
            String name = "Gracz" + index;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    if (this.service.redeem(UUID.randomUUID(), name, code).get(10, TimeUnit.SECONDS)
                            .outcome() == RedeemResult.Outcome.REDEEMED) {
                        redeemed.incrementAndGet();
                    }
                }
                catch (Exception exception) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        for (Thread thread : threads) {
            thread.join(15_000);
        }

        assertEquals(1, redeemed.get(), "a voucher paid out more than once under a race");
    }

    @Test
    @DisplayName("a code assigned to somebody else reads as if it did not exist")
    void hidesSomebodyElsesCode() throws Exception {
        String code = await(this.service.issue("vip7", "Anna", 1, "Admin")).getFirst();

        // Not ALREADY_USED and not a "this is not yours" - either would confirm to a guesser
        // that the code is real.
        assertEquals(
                RedeemResult.Outcome.UNKNOWN,
                await(this.service.redeem(UUID.randomUUID(), "Borek", code)).outcome());

        assertEquals(
                RedeemResult.Outcome.REDEEMED,
                await(this.service.redeem(UUID.randomUUID(), "Anna", code)).outcome());
    }

    @Test
    @DisplayName("an assigned code works whatever case the name was typed in")
    void matchesTheAssignedNameCaseInsensitively() throws Exception {
        String code = await(this.service.issue("vip7", "AnNa", 1, "Admin")).getFirst();

        assertEquals(
                RedeemResult.Outcome.REDEEMED,
                await(this.service.redeem(UUID.randomUUID(), "anna", code)).outcome());
    }

    @Test
    @DisplayName("a code nobody issued is refused, and so is one typed with spaces or lower case")
    void handlesWhatPlayersActuallyType() throws Exception {
        String code = await(this.service.issue("vip7", null, 1, "Admin")).getFirst();

        assertEquals(
                RedeemResult.Outcome.UNKNOWN,
                await(this.service.redeem(UUID.randomUUID(), "Crispi", "NIE-MA-TAKIEGO")).outcome());

        // Read off a stream and typed back in, so leading spaces and lower case are normal.
        assertEquals(
                RedeemResult.Outcome.REDEEMED,
                await(this.service.redeem(UUID.randomUUID(), "Crispi", "  " + code.toLowerCase() + " "))
                        .outcome());
    }

    @Test
    @DisplayName("a code naming a reward that was deleted fails loudly rather than silently")
    void reportsAMissingRewardType() throws Exception {
        String code = await(this.service.issue("vip7", null, 1, "Admin")).getFirst();
        this.config.vouchers.types.remove("vip7");

        RedeemResult result = await(this.service.redeem(UUID.randomUUID(), "Crispi", code));

        assertEquals(RedeemResult.Outcome.UNKNOWN_TYPE, result.outcome());
        assertEquals("vip7", result.type());
    }

    @Test
    @DisplayName("a failed redeem does not consume the code")
    void doesNotConsumeOnFailure() throws Exception {
        String code = await(this.service.issue("vip7", null, 1, "Admin")).getFirst();
        this.config.vouchers.types.remove("vip7");
        await(this.service.redeem(UUID.randomUUID(), "Crispi", code));

        this.config.vouchers.types.put(
                "vip7", new ProxyConfig.VoucherReward("Ranga VIP na 7 dni", List.of()));

        assertEquals(
                RedeemResult.Outcome.REDEEMED,
                await(this.service.redeem(UUID.randomUUID(), "Crispi", code)).outcome(),
                "the code was burned by a failure that was not the player's fault");
    }

    @Test
    @DisplayName("issued codes are unique, readable and free of characters people mistype")
    void generatesUsableCodes() throws Exception {
        List<String> codes = await(this.service.issue("vip7", null, 50, "Admin"));

        Set<String> unique = new HashSet<>(codes);
        assertEquals(50, unique.size(), "two issued codes were identical");

        for (String code : codes) {
            assertFalse(code.contains("O"), code);
            assertFalse(code.contains("0"), code);
            assertFalse(code.contains("I"), code);
            assertFalse(code.contains("1"), code);
            assertTrue(code.contains("-"), "codes are grouped so they can be read aloud: " + code);
        }

        assertNotEquals(codes.getFirst(), codes.get(1));
    }

    @Test
    @DisplayName("the waiting count only counts this player's unused codes")
    void countsWhatIsWaiting() throws Exception {
        await(this.service.issue("vip7", "Anna", 3, "Admin"));
        await(this.service.issue("vip7", "Borek", 1, "Admin"));
        String annaCode = await(this.service.issue("vip7", "Anna", 1, "Admin")).getFirst();

        assertEquals(4L, await(this.service.unusedFor("Anna")).longValue());

        await(this.service.redeem(UUID.randomUUID(), "Anna", annaCode));

        assertEquals(3L, await(this.service.unusedFor("Anna")).longValue());
        assertEquals(0L, await(this.service.unusedFor("NigdyTuNieByl")).longValue());
    }

    @Test
    @DisplayName("the rate limit counts down and is forgotten when the player leaves")
    void ratelimitsGuessing() {
        UUID playerId = UUID.randomUUID();

        assertEquals(0L, this.service.remainingCooldownSeconds(playerId));

        this.service.startCooldown(playerId);
        assertTrue(this.service.remainingCooldownSeconds(playerId) > 0L);

        this.service.onDisconnect(playerId);
        assertEquals(0L, this.service.remainingCooldownSeconds(playerId));
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(20, TimeUnit.SECONDS);
    }
}
