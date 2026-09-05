package pl.landmc.proxy.live;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Who may announce a stream, and whether they are actually streaming.
 *
 * <p>Three separate gates, and it is worth being explicit about which does what, because the
 * previous version had only the first two and that was not enough:
 *
 * <ul>
 *   <li>a <b>profile</b>, registered by staff, decides <i>who</i> may announce and <i>where</i>
 *       the link points - a player cannot supply their own URL, so the command is not an
 *       advertising slot;
 *   <li>an <b>API check</b> decides <i>whether</i> there is a stream to announce, so nobody
 *       announces one they are not running;
 *   <li>a <b>cooldown</b> decides <i>how often</i>. Without it somebody who really is live can
 *       repeat a network-wide message as fast as they can type, and being genuinely on air is
 *       no comfort to everybody reading it for the fifth time.
 * </ul>
 */
public final class LiveService {

    private final LiveRepository repository;
    private final ProxyConfig config;
    private final Map<StreamPlatform, StreamStatusClient> clients =
            new EnumMap<>(StreamPlatform.class);

    /** When each player may announce again. Cleared as it is read, and when they disconnect. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public LiveService(
            LiveRepository repository, ProxyConfig config, List<StreamStatusClient> clients) {

        this.repository = Objects.requireNonNull(repository, "repository");
        this.config = Objects.requireNonNull(config, "config");

        for (StreamStatusClient client : clients) {
            this.clients.put(client.platform(), client);
        }
    }

    public void createTables() {
        this.repository.createTables();
    }

    public CompletableFuture<Optional<StreamProfile>> profile(String playerName) {
        return this.repository.find(playerName);
    }

    public CompletableFuture<Void> register(String playerName, StreamProfile profile, String by) {
        return this.repository.save(playerName, profile, by);
    }

    public CompletableFuture<Boolean> unregister(String playerName) {
        return this.repository.remove(playerName);
    }

    public CompletableFuture<List<LiveRepository.Entry>> list() {
        return this.repository.list();
    }

    /**
     * Whether the channel is on air.
     *
     * <p>A platform nothing can ask about answers {@link StreamStatus#UNKNOWN} rather than
     * pretending - what the caller does with that is a decision about trust, not about the API.
     */
    public CompletableFuture<StreamStatus> status(StreamProfile profile) {
        StreamStatusClient client = this.clients.get(profile.platform());
        if (client == null || !client.isConfigured()) {
            return CompletableFuture.completedFuture(StreamStatus.UNKNOWN);
        }
        return client.check(profile.identifier());
    }

    /** Whether a platform can be checked at all on this proxy, given what is configured. */
    public boolean canVerify(StreamPlatform platform) {
        StreamStatusClient client = this.clients.get(platform);
        return client != null && client.isConfigured();
    }

    // --- cooldown ------------------------------------------------------------------------

    /** Seconds left before this player may announce again, or zero. */
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

    /**
     * Starts the cooldown.
     *
     * <p>Called after a successful announcement rather than before the check: a player whose
     * stream turned out to be offline has not used anything up, and telling them to wait half an
     * hour because the API said no would be a punishment for a failed command.
     */
    public void startCooldown(UUID playerId) {
        this.cooldowns.put(
                playerId,
                System.currentTimeMillis()
                        + Duration.ofMinutes(Math.max(0, this.config.live.cooldownMinutes)).toMillis());
    }

    /** Forgets a player who left, so the map does not grow with everybody who ever announced. */
    public void onDisconnect(UUID playerId) {
        this.cooldowns.remove(playerId);
    }
}
