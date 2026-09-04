package pl.landmc.proxy.debug;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Ties the events of one login attempt together under a single id.
 *
 * <p>A join crosses seven or eight events and several threads, and several players can be
 * joining at once, so reading the log without an id per attempt means guessing which line
 * belongs to which player. Timings are relative to the attempt's own start.
 *
 * <p>A trace begins keyed by username, because that is all the proxy knows before the profile
 * arrives, and moves to a UUID key as soon as there is a player. Entries that never reach that
 * point - a connection dropped mid-handshake - are swept on a later start rather than by a
 * timer: this is a diagnostic aid and does not deserve a thread of its own.
 */
public final class JoinTraceRegistry {

    private static final long TRACE_TTL_NANOS = Duration.ofMinutes(10).toNanos();

    /** Sweep every 256 traces; cheap to test, and often enough on any real proxy. */
    private static final long PRUNE_INTERVAL_MASK = 255L;

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<String, JoinTrace> pendingByUsername = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, JoinTrace> byPlayerId = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public JoinTraceRegistry() {
        this(System::nanoTime);
    }

    /** @param nanoTime the clock, so a test can age traces without waiting ten minutes */
    public JoinTraceRegistry(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public JoinTrace start(String username) {
        long now = this.nanoTime.getAsLong();
        long traceId = this.sequence.incrementAndGet();
        JoinTrace trace = new JoinTrace(traceId, now);
        this.pendingByUsername.put(normalize(username), trace);
        if ((traceId & PRUNE_INTERVAL_MASK) == 0L) {
            this.prune(now);
        }
        return trace;
    }

    /** The trace for a login in progress, created if the first event was missed. */
    public JoinTrace pending(String username) {
        return this.pendingByUsername.computeIfAbsent(
                normalize(username),
                ignored -> new JoinTrace(this.sequence.incrementAndGet(), this.nanoTime.getAsLong()));
    }

    /** Moves a username-keyed trace onto the player's UUID, now that there is one. */
    public JoinTrace attach(String username, UUID playerId) {
        return this.byPlayerId.computeIfAbsent(playerId, ignored -> {
            JoinTrace pending = this.pendingByUsername.remove(normalize(username));
            return pending == null
                    ? new JoinTrace(this.sequence.incrementAndGet(), this.nanoTime.getAsLong())
                    : pending;
        });
    }

    public void reject(String username) {
        this.pendingByUsername.remove(normalize(username));
    }

    public void finish(UUID playerId) {
        this.byPlayerId.remove(playerId);
    }

    public long elapsedMillis(JoinTrace trace) {
        return Math.max(0L, (this.nanoTime.getAsLong() - trace.startedNanos()) / 1_000_000L);
    }

    private void prune(long now) {
        this.pendingByUsername.values().removeIf(trace -> now - trace.startedNanos() >= TRACE_TTL_NANOS);
        this.byPlayerId.values().removeIf(trace -> now - trace.startedNanos() >= TRACE_TTL_NANOS);
    }

    private static String normalize(String username) {
        return username == null ? "<unknown>" : username.toLowerCase(Locale.ROOT);
    }

    public record JoinTrace(long id, long startedNanos) {
    }
}
