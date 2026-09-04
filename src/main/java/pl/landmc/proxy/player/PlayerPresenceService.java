package pl.landmc.proxy.player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pl.landmc.platform.messaging.PlayerLocator;

/**
 * Tracks which backend each player connected to this proxy is on.
 *
 * <p>This is the index the platform's player-targeted messaging was designed around. Without
 * it, a message aimed at one player goes to every node and each answers "is this player here?"
 * locally; with it, the proxy resolves the player to a backend before publishing and the
 * message wakes exactly one server. Implementing {@link PlayerLocator} is what plugs it in -
 * the proxy is the only process that observes every server switch, so it is the natural owner
 * of that answer.
 *
 * <p>Deliberately local: it knows about players on <em>this</em> proxy. A network with several
 * proxies needs the index in Redis instead, and that is a decision to make when a second proxy
 * exists. The interface stays the same either way, so nothing above it has to change.
 *
 * <p>Updated from Velocity's connection events, one entry at a time. Nothing here iterates over
 * all players.
 *
 * <p>It holds no reference to {@code ProxyServer}: looking a player up by UUID is already a
 * direct lookup on Velocity's own API, and a method here forwarding to it would be a wrapper
 * that hides nothing.
 */
public final class PlayerPresenceService implements PlayerLocator {

    /** Written on connect and disconnect, read by the message bus from its own thread. */
    private final Map<UUID, String> serverByPlayer = new ConcurrentHashMap<>();

    /** Records that a player is now on a backend. */
    public void onConnected(UUID playerId, String serverId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(serverId, "serverId");
        this.serverByPlayer.put(playerId, serverId);
    }

    /** Forgets a player that left the proxy. */
    public void onDisconnected(UUID playerId) {
        this.serverByPlayer.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public Optional<String> serverOf(UUID playerId) {
        return Optional.ofNullable(this.serverByPlayer.get(Objects.requireNonNull(playerId, "playerId")));
    }

    /** Number of players whose backend is currently known. */
    public int tracked() {
        return this.serverByPlayer.size();
    }

    /** Drops every entry; used on shutdown so a reload does not observe stale state. */
    public void clear() {
        this.serverByPlayer.clear();
    }
}
