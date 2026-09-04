package pl.landmc.proxy.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The registry is what makes the join log readable, so the properties tested here are the ones
 * somebody relies on while reading it at two in the morning: one id per attempt, timings from
 * the attempt's own start, and no entry left behind by a connection that never finished.
 */
class JoinTraceRegistryTest {

    @Test
    @DisplayName("events before and after authentication share one trace id")
    void correlatesPreLoginWithTheAuthenticatedPlayer() {
        AtomicLong nanoTime = new AtomicLong(1_000_000L);
        JoinTraceRegistry registry = new JoinTraceRegistry(nanoTime::get);
        UUID playerId = UUID.randomUUID();

        JoinTraceRegistry.JoinTrace started = registry.start("CrispiDEV");
        nanoTime.addAndGet(25_000_000L);
        // The client sends the name it was given; the proxy may see either case.
        JoinTraceRegistry.JoinTrace attached = registry.attach("crispidev", playerId);

        assertEquals(started.id(), attached.id());
        assertEquals(25L, registry.elapsedMillis(attached));
    }

    @Test
    @DisplayName("a reconnect is a new attempt, not a continuation of the last one")
    void startsAFreshTraceForTheNextAttempt() {
        JoinTraceRegistry registry = new JoinTraceRegistry();
        UUID playerId = UUID.randomUUID();

        JoinTraceRegistry.JoinTrace first = registry.attach("CrispiDEV", playerId);
        registry.finish(playerId);
        JoinTraceRegistry.JoinTrace second = registry.attach("CrispiDEV", playerId);

        assertNotEquals(first.id(), second.id());
    }

    @Test
    @DisplayName("a refused login does not keep its trace waiting for a player who never arrives")
    void dropsATraceForARejectedLogin() {
        JoinTraceRegistry registry = new JoinTraceRegistry();

        JoinTraceRegistry.JoinTrace rejected = registry.start("CrispiDEV");
        registry.reject("CrispiDEV");
        JoinTraceRegistry.JoinTrace later = registry.pending("CrispiDEV");

        assertNotEquals(rejected.id(), later.id());
    }

    @Test
    @DisplayName("connections that die mid-handshake are swept instead of accumulating")
    void prunesTracesThatNeverFinished() {
        AtomicLong nanoTime = new AtomicLong(0L);
        JoinTraceRegistry registry = new JoinTraceRegistry(nanoTime::get);

        // Every one of these disappears mid-handshake: started, never attached, never rejected.
        JoinTraceRegistry.JoinTrace abandoned = registry.start("Abandoned");
        nanoTime.addAndGet(Duration.ofMinutes(11).toNanos());

        // The sweep runs on a later start rather than on a timer, so it takes 256 of them.
        for (int attempt = 0; attempt < 256; attempt++) {
            registry.start("Player" + attempt);
        }

        assertNotEquals(
                abandoned.id(),
                registry.pending("Abandoned").id(),
                "the abandoned trace survived the sweep");
    }

    @Test
    @DisplayName("elapsed time never reads negative, whatever the clock does")
    void neverReportsNegativeElapsedTime() {
        AtomicLong nanoTime = new AtomicLong(5_000_000L);
        JoinTraceRegistry registry = new JoinTraceRegistry(nanoTime::get);

        JoinTraceRegistry.JoinTrace trace = registry.start("CrispiDEV");
        nanoTime.set(0L);

        assertTrue(registry.elapsedMillis(trace) >= 0L);
    }
}
