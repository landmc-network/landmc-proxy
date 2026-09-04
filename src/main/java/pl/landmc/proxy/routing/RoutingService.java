package pl.landmc.proxy.routing;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.server.ServerRegistry;

/**
 * Moves players between backends, and answers where a player should go when nothing else
 * decided.
 *
 * <p>One responsibility on purpose. It does not know about maintenance, does not send
 * messages and does not decide whether a command was allowed - it connects a player to a
 * server and reports how that went. Whoever called it turns {@link TransferResult} into a
 * message.
 *
 * <p>Threading follows Velocity's model: {@code connect()} returns a future that Velocity
 * completes on its own event thread, and nothing here adds an executor hop of its own.
 */
public final class RoutingService {

    private final ServerRegistry servers;
    private final ProxyConfig config;

    public RoutingService(ServerRegistry servers, ProxyConfig config) {
        this.servers = Objects.requireNonNull(servers, "servers");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** The configured fallback backend, empty when it is not registered on this proxy. */
    public Optional<RegisteredServer> fallback() {
        return this.servers.get(this.config.routing.fallbackServer);
    }

    public String fallbackName() {
        return this.config.routing.fallbackServer;
    }

    /**
     * Connects a player to a backend.
     *
     * <p>The returned future never fails: a refused or broken connection arrives as a
     * {@link TransferResult}, because "the backend is down" is an expected outcome here rather
     * than an exception.
     */
    public CompletableFuture<TransferResult> connect(Player player, RegisteredServer target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");

        String targetName = target.getServerInfo().getName();

        if (isAlreadyOn(player, targetName)) {
            return CompletableFuture.completedFuture(TransferResult.ALREADY_CONNECTED);
        }

        return player.createConnectionRequest(target)
                .connect()
                .handle((result, error) -> {
                    if (error != null) {
                        return TransferResult.FAILED;
                    }
                    if (result.isSuccessful()) {
                        return TransferResult.SUCCESS;
                    }
                    return result.getStatus() == ConnectionRequestBuilder.Status.ALREADY_CONNECTED
                            ? TransferResult.ALREADY_CONNECTED
                            : TransferResult.FAILED;
                });
    }

    /**
     * Connects a player to the fallback backend.
     *
     * <p>Used when a player joins the network and when a backend drops them. There is
     * deliberately no failover chain across several lobbies yet - the configuration describes
     * one fallback, and inventing an order over the remaining servers would be guessing at a
     * policy nobody has stated.
     */
    public CompletableFuture<TransferResult> connectToFallback(Player player) {
        Optional<RegisteredServer> fallback = this.fallback();
        if (fallback.isEmpty()) {
            return CompletableFuture.completedFuture(TransferResult.NO_SUCH_SERVER);
        }
        return this.connect(player, fallback.get());
    }

    private static boolean isAlreadyOn(Player player, String serverName) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(serverName))
                .orElse(false);
    }

    /** How a transfer ended, in terms the caller can turn into a message. */
    public enum TransferResult {

        SUCCESS,

        /** The player is already on that backend; not an error, but worth telling them. */
        ALREADY_CONNECTED,

        /** No backend with that id is registered on this proxy. */
        NO_SUCH_SERVER,

        /** The backend refused the connection or is unreachable. */
        FAILED
    }
}
