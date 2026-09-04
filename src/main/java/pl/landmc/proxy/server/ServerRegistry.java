package pl.landmc.proxy.server;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Looks up backend servers by id.
 *
 * <p>Velocity's {@code ProxyServer#getServer(String)} is already an indexed lookup, so this is
 * not a cache over it - wrapping it in a map of our own would only add something to keep in
 * sync with {@code registerServer} and {@code unregisterServer}. What this adds is the two
 * things the proxy actually needs on top: case-insensitive ids, because players type
 * {@code /server Lobby-1}, and a single place to ask instead of Velocity's {@code Optional}
 * spread across every command.
 *
 * <p>Nothing here scans {@link #all()} to find one server.
 */
public final class ServerRegistry {

    private final ProxyServer proxy;

    public ServerRegistry(ProxyServer proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
    }

    /**
     * The backend with this id, ignoring case.
     *
     * <p>Velocity keys its own registry case-sensitively, so an exact hit is tried first and
     * the lower-case form second; only a genuinely unknown name falls through to empty.
     */
    public Optional<RegisteredServer> get(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return Optional.empty();
        }

        Optional<RegisteredServer> exact = this.proxy.getServer(serverId);
        if (exact.isPresent()) {
            return exact;
        }
        return this.proxy.getServer(serverId.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String serverId) {
        return this.get(serverId).isPresent();
    }

    /** Every backend Velocity knows about. */
    public Collection<RegisteredServer> all() {
        return this.proxy.getAllServers();
    }

    /** Backend names, sorted, for {@code /server} without an argument and for tab completion. */
    public List<String> names() {
        return this.proxy.getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .sorted()
                .toList();
    }

    public int count() {
        return this.proxy.getAllServers().size();
    }
}
