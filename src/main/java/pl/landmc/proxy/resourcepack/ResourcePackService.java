package pl.landmc.proxy.resourcepack;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Offers the network's resource pack to players and tracks who has it.
 *
 * <p>The proxy sends the pack rather than the backends, so a player downloads it once for the
 * whole network instead of once per server switch. Backend offers are refused for the same
 * reason.
 *
 * <p>Players are held at their first backend connection until the pack is applied - see
 * {@link #awaitInitialPack(Player)}. That is a deliberate availability trade: a player who
 * cannot load the pack does not get in. It is switchable in the configuration.
 *
 * <p>Does no I/O of its own; {@link ManifestSource} fetches, and this reacts to what it returns.
 */
public final class ResourcePackService implements AutoCloseable {

    private final ProxyServer proxy;
    private final PluginContainer plugin;
    private final ManifestSource source;
    private final ProxyConfig config;
    private final ComponentFormatter formatter;
    private final Logger logger;

    private final AtomicReference<ResourcePackManifest> manifest = new AtomicReference<>();
    private final Map<UUID, DeliveryState> states = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> gates = new ConcurrentHashMap<>();

    private volatile boolean closed;

    /** How long to wait before a join may retry a manifest that failed to load. */
    private static final long RETRY_INTERVAL_MILLIS = 30_000L;

    private static final java.util.concurrent.atomic.AtomicLongFieldUpdater<ResourcePackService>
            RECOVERY_ATTEMPT = java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater(
                    ResourcePackService.class, "lastRecoveryAttempt");

    private volatile long lastRecoveryAttempt;
    private volatile String lastFetchError;

    public ResourcePackService(
            ProxyServer proxy,
            PluginContainer plugin,
            ManifestSource source,
            ProxyConfig config,
            ComponentFormatter formatter,
            Logger logger) {

        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.source = Objects.requireNonNull(source, "source");
        this.config = Objects.requireNonNull(config, "config");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean isEnabled() {
        return this.config.resourcePack.enabled && !this.closed;
    }

    /** Reads the manifest once at startup; a failure is logged, not fatal. */
    public void start() {
        this.refresh("startup");
    }

    /**
     * Re-reads the manifest.
     *
     * <p>Called when the builder announces a rebuild over the message bus, and when a backend
     * asks over the plugin channel. Both are events, not polls.
     */
    public void refresh(String reason) {
        if (!this.isEnabled()) {
            return;
        }

        this.source.fetch().whenComplete((fetched, error) -> {
            if (error != null) {
                this.reportFetchError(rootCauseMessage(error));
                return;
            }
            this.logger.info("Resource-pack manifest re-read ({}).", reason);
            this.applyManifest(fetched);
        });
    }

    /** Retries a missing manifest at most once every {@value #RETRY_INTERVAL_MILLIS} ms. */
    private void refreshIfStale(String reason) {
        long now = System.currentTimeMillis();
        long previous = this.lastRecoveryAttempt;
        if (now - previous < RETRY_INTERVAL_MILLIS) {
            return;
        }
        if (RECOVERY_ATTEMPT.compareAndSet(this, previous, now)) {
            this.refresh(reason);
        }
    }

    /**
     * Blocks a player's first backend connection until the pack is applied.
     *
     * <p>The recheck after registering the gate is not redundant: the client can acknowledge
     * the pack between the first check and the gate being stored, which would otherwise leave
     * the join hanging until the timeout.
     */
    public CompletableFuture<Boolean> awaitInitialPack(Player player) {
        if (!this.isEnabled()
                || !this.config.resourcePack.waitBeforeInitialServer
                || !this.isPendingForCurrentPack(player.getUniqueId())) {
            return CompletableFuture.completedFuture(true);
        }

        UUID playerId = player.getUniqueId();
        CompletableFuture<Boolean> created = new CompletableFuture<>();
        CompletableFuture<Boolean> existing = this.gates.putIfAbsent(playerId, created);
        if (existing != null) {
            return existing;
        }

        this.proxy.getScheduler()
                .buildTask(this.plugin, () -> this.timeoutGate(playerId, created))
                .delay(this.timeoutSeconds(), TimeUnit.SECONDS)
                .schedule();

        this.offer(player, false);

        if (!this.isPendingForCurrentPack(playerId)) {
            this.openGate(playerId, true);
        }
        return created;
    }

    public void onPostLogin(Player player) {
        if (this.isEnabled()) {
            this.offer(player, false);
        }
    }

    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        ResourcePackManifest current = this.manifest.get();
        UUID packId = event.getPackId();
        if (current == null || packId == null || !current.id().equals(packId)) {
            return;
        }

        switch (event.getStatus()) {
            case SUCCESSFUL -> this.complete(event.getPlayer(), current);
            case DECLINED -> this.handleFailure(event.getPlayer(), current, true);
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD ->
                    this.handleFailure(event.getPlayer(), current, false);
            default -> {
                // ACCEPTED and DOWNLOADED are intermediate; DISCARDED does not prove the
                // current pack was applied, so the gate stays shut until SUCCESSFUL or timeout.
            }
        }
    }

    /** Refuses a backend's own offer so a player does not download the pack twice. */
    public void onBackendOffer(ServerResourcePackSendEvent event) {
        if (!this.isEnabled() || !this.config.resourcePack.blockBackendOffers) {
            return;
        }

        event.setResult(ResultedEvent.GenericResult.denied());
        this.logger.warn(
                "Blocked a duplicate resource-pack offer from backend {}; turn pack sending off there.",
                event.getServerConnection().getServerInfo().getName());
    }

    public void onPluginMessage(PluginMessageEvent event) {
        if (!ResourcePackProtocol.CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!this.isEnabled() || !(event.getSource() instanceof ServerConnection source)) {
            return;
        }

        OptionalInt action = ResourcePackProtocol.decodeBackendAction(event.getData());
        if (action.isEmpty()) {
            return;
        }

        Player player = source.getPlayer();
        if (player.getCurrentServer().filter(current -> current == source).isEmpty()) {
            return;
        }

        if (action.getAsInt() == ResourcePackProtocol.RESEND_REQUEST) {
            this.offer(player, true);
        }
        else if (action.getAsInt() == ResourcePackProtocol.REFRESH_MANIFEST) {
            this.refresh("backend request");
        }
    }

    public void onDisconnect(UUID playerId) {
        this.states.remove(playerId);
        this.openGate(playerId, false);
    }

    @Override
    public void close() {
        this.closed = true;
        // Let anyone still waiting through rather than leaving their join hanging on shutdown.
        this.gates.forEach((playerId, gate) -> gate.complete(true));
        this.gates.clear();
        this.states.clear();
    }

    private void applyManifest(ResourcePackManifest next) {
        ResourcePackManifest previous = this.manifest.getAndSet(next);

        if (this.lastFetchError != null) {
            this.logger.info("Resource-pack manifest is reachable again.");
            this.lastFetchError = null;
        }
        if (next.samePack(previous)) {
            return;
        }

        this.logger.info("Loaded resource-pack manifest {} ({}).", next.sha1(), next.packId());

        if (previous == null || next.resendAfterRebuild()) {
            this.proxy.getAllPlayers().forEach(player -> this.offer(player, false));
            return;
        }
        // A rebuild the pack itself says needs no resend: mark everyone as holding it.
        this.proxy.getAllPlayers().forEach(player -> this.complete(player, next));
    }

    private void offer(Player player, boolean forceResend) {
        ResourcePackManifest current = this.manifest.get();
        if (current == null) {
            // With polling gone, a join is the only thing left that can recover a failed
            // startup fetch - but a busy proxy joins players faster than an outage recovers,
            // so the retry is throttled rather than tied one-to-one to logins.
            this.refreshIfStale("missing manifest");
            return;
        }
        if (!player.isActive()) {
            return;
        }

        DeliveryState existing = this.states.get(player.getUniqueId());
        if (!forceResend && existing != null && existing.matches(current)) {
            return;
        }
        this.sendOffer(player, current, 1);
    }

    private void sendOffer(Player player, ResourcePackManifest current, int attempt) {
        if (!player.isActive() || !this.isCurrent(current)) {
            return;
        }

        String host = player.getVirtualHost().map(address -> address.getHostString()).orElse(null);
        try {
            player.sendResourcePackOffer(this.proxy.createResourcePackBuilder(current.url(host))
                    .setId(current.id())
                    .setHash(current.hash())
                    .setPrompt(this.formatter.format(current.prompt()))
                    // Only force on the last attempt, so a transient failure gets a retry first.
                    .setShouldForce(current.required() && attempt >= current.maxAttempts())
                    .build());
        }
        catch (RuntimeException exception) {
            this.logger.warn(
                    "Could not offer the resource pack to {}: {}",
                    player.getUsername(), exception.getMessage());
            this.states.remove(player.getUniqueId());

            if (current.required()) {
                player.disconnect(this.formatter.format(current.downloadFailedKickMessage()));
                this.openGate(player.getUniqueId(), false);
            }
            else {
                this.complete(player, current);
            }
            return;
        }

        this.states.put(player.getUniqueId(), DeliveryState.pending(current, attempt));
        this.sendBackendState(player, true);
    }

    private void handleFailure(Player player, ResourcePackManifest current, boolean declined) {
        DeliveryState state = this.states.get(player.getUniqueId());
        if (state == null || !state.matches(current) || state.stage() != DeliveryStage.PENDING) {
            return;
        }

        if (!current.required()) {
            this.complete(player, current);
            return;
        }

        if (state.attempt() < current.maxAttempts()) {
            DeliveryState retry = state.retryScheduled();
            if (!this.states.replace(player.getUniqueId(), state, retry)) {
                return;
            }
            if (!current.retryMessage().isBlank()) {
                player.sendMessage(this.formatter.format(current.retryMessage()));
            }

            UUID playerId = player.getUniqueId();
            this.proxy.getScheduler()
                    .buildTask(this.plugin, () -> this.retry(playerId, current, retry))
                    .delay(current.retryDelayMillis(), TimeUnit.MILLISECONDS)
                    .schedule();
            return;
        }

        this.states.remove(player.getUniqueId(), state);
        player.disconnect(this.formatter.format(
                declined ? current.declinedKickMessage() : current.downloadFailedKickMessage()));
        this.openGate(player.getUniqueId(), false);
    }

    private void retry(UUID playerId, ResourcePackManifest expected, DeliveryState expectedState) {
        if (!this.isCurrent(expected)
                || !this.states.replace(
                        playerId, expectedState, DeliveryState.pending(expected, expectedState.attempt() + 1))) {
            return;
        }
        this.proxy.getPlayer(playerId)
                .ifPresent(player -> this.sendOffer(player, expected, expectedState.attempt() + 1));
    }

    private void complete(Player player, ResourcePackManifest current) {
        this.states.put(player.getUniqueId(), DeliveryState.complete(current));
        this.openGate(player.getUniqueId(), true);
        this.sendBackendState(player, false);
    }

    private void timeoutGate(UUID playerId, CompletableFuture<Boolean> expected) {
        if (!this.gates.remove(playerId, expected)) {
            return;
        }

        this.proxy.getPlayer(playerId).ifPresent(player -> {
            if (player.isActive()) {
                this.logger.warn("{} did not load the resource pack in time.", player.getUsername());
                player.disconnect(this.formatter.format(this.config.resourcePack.waitTimeoutMessage));
            }
        });
        expected.complete(false);
    }

    private void openGate(UUID playerId, boolean allow) {
        CompletableFuture<Boolean> gate = this.gates.remove(playerId);
        if (gate != null) {
            gate.complete(allow);
        }
    }

    private boolean isPendingForCurrentPack(UUID playerId) {
        ResourcePackManifest current = this.manifest.get();
        if (current == null) {
            // Nothing to download, so nothing to wait for. Answering "pending" here would make
            // an unreachable manifest endpoint hold every joining player at the gate for the
            // full timeout and then kick them - the network would be down because a pack host
            // is down. The gate exists to wait for a *known* pack, not for one to appear.
            return false;
        }
        DeliveryState state = this.states.get(playerId);
        return state == null || !state.matches(current) || state.stage() != DeliveryStage.COMPLETE;
    }

    /** Tells the backend whether this player is still downloading, so it can wait too. */
    private void sendBackendState(Player player, boolean pending) {
        if (!player.isActive()) {
            return;
        }

        ServerConnection connection = player.getCurrentServer().orElse(null);
        if (connection == null) {
            return;
        }

        try {
            connection.sendPluginMessage(
                    ResourcePackProtocol.CHANNEL, ResourcePackProtocol.deliveryState(pending));
        }
        catch (IllegalStateException exception) {
            // The backend connection closed between the lookup and the send; the next
            // ServerConnectedEvent re-synchronises.
        }
    }

    private boolean isCurrent(ResourcePackManifest candidate) {
        return candidate != null && candidate.samePack(this.manifest.get());
    }

    private int timeoutSeconds() {
        return Math.max(10, Math.min(300, this.config.resourcePack.waitTimeoutSeconds));
    }

    /** Logs a fetch failure once per distinct cause, so an outage does not fill the log. */
    private void reportFetchError(String message) {
        if (!message.equals(this.lastFetchError)) {
            this.lastFetchError = message;
            this.logger.warn(
                    "Resource-pack manifest unavailable at {}: {} - no pack will be offered",
                    this.config.resourcePack.manifestUrl,
                    message);
        }
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    private enum DeliveryStage {
        PENDING,
        RETRY_SCHEDULED,
        COMPLETE
    }

    private record DeliveryState(UUID packId, String sha1, int attempt, DeliveryStage stage) {

        private static DeliveryState pending(ResourcePackManifest manifest, int attempt) {
            return new DeliveryState(manifest.id(), manifest.sha1(), attempt, DeliveryStage.PENDING);
        }

        private static DeliveryState complete(ResourcePackManifest manifest) {
            return new DeliveryState(manifest.id(), manifest.sha1(), 0, DeliveryStage.COMPLETE);
        }

        private DeliveryState retryScheduled() {
            return new DeliveryState(this.packId, this.sha1, this.attempt, DeliveryStage.RETRY_SCHEDULED);
        }

        private boolean matches(ResourcePackManifest manifest) {
            return this.packId.equals(manifest.id()) && this.sha1.equals(manifest.sha1());
        }
    }
}
