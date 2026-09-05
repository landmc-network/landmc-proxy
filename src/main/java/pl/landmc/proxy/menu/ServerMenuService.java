package pl.landmc.proxy.menu;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import pl.landmc.menus.protocol.MenuPayload;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Builds the server list a player sees, and finds a server they picked.
 *
 * <p>Only servers named in the configuration appear. Listing whatever is in {@code velocity.toml}
 * would put the limbo in the menu - a world with nothing in it, which a player would click once
 * and then have to be rescued from.
 *
 * <p>Whether a server is up comes from {@link ServerHealth}, which checks on a timer. Building a
 * payload therefore touches no network at all and the menu opens as fast as the command runs.
 */
public final class ServerMenuService {

    private final ProxyServer proxy;
    private final ProxyConfig config;
    private final ServerHealth health;

    public ServerMenuService(ProxyServer proxy, ProxyConfig config, ServerHealth health) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.config = Objects.requireNonNull(config, "config");
        this.health = Objects.requireNonNull(health, "health");
    }

    /** The servers this menu offers, in configured order, skipping any that is not registered. */
    public Map<String, RegisteredServer> listed() {
        return this.listed(this.config.menus.servers);
    }

    /** The lobbies, on the same terms. */
    public Map<String, RegisteredServer> listedLobbies() {
        return this.listed(this.config.menus.lobbies);
    }

    private Map<String, RegisteredServer> listed(Map<String, String> configured) {
        Map<String, RegisteredServer> listed = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : configured.entrySet()) {
            this.proxy.getServer(entry.getKey())
                    .ifPresent(server -> listed.put(entry.getKey(), server));
        }
        return listed;
    }

    /** A server the player may be sent to, or empty when they named one that is not on the menu. */
    public Optional<RegisteredServer> selectable(String serverId) {
        return Optional.ofNullable(this.listed().get(serverId));
    }

    /**
     * A lobby the player may be sent to.
     *
     * <p>Separate from {@link #selectable}, because the two menus offer different lists and a
     * click on one must not reach a server only the other offers.
     */
    public Optional<RegisteredServer> selectableLobby(String serverId) {
        return Optional.ofNullable(this.listedLobbies().get(serverId));
    }

    /** Builds the payload. No I/O: the health of each server was checked in the background. */
    public MenuPayload.Servers payload(Player player) {
        String current = currentServerOf(player);
        return new MenuPayload.Servers(
                current, this.entries(current, this.listed(), this.config.menus.servers));
    }

    /** The same, for the lobbies. */
    public MenuPayload.Lobbies lobbies(Player player) {
        String current = currentServerOf(player);
        return new MenuPayload.Lobbies(
                current,
                this.entries(current, this.listedLobbies(), this.config.menus.lobbies));
    }

    private List<MenuPayload.Servers.Server> entries(
            String current, Map<String, RegisteredServer> listed, Map<String, String> names) {

        List<MenuPayload.Servers.Server> servers = new ArrayList<>(listed.size());
        for (Map.Entry<String, RegisteredServer> entry : listed.entrySet()) {
            // A server the player is standing on is reachable by definition, whatever the last
            // check made of it.
            boolean reachable = entry.getKey().equals(current)
                    || this.health.isReachable(entry.getKey());

            servers.add(new MenuPayload.Servers.Server(
                    entry.getKey(),
                    names.getOrDefault(entry.getKey(), entry.getKey()),
                    entry.getValue().getPlayersConnected().size(),
                    reachable));
        }
        return servers;
    }

    private static String currentServerOf(Player player) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("");
    }
}
