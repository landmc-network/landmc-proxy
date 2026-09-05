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

    private Map<String, RegisteredServer> listed(List<ProxyConfig.MenuServer> configured) {
        Map<String, RegisteredServer> listed = new LinkedHashMap<>();

        for (ProxyConfig.MenuServer entry : configured) {
            this.proxy.getServer(entry.id).ifPresent(server -> listed.put(entry.id, server));
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
        return new MenuPayload.Servers(current, this.entries(current, this.config.menus.servers));
    }

    /** The same, for the lobbies. */
    public MenuPayload.Lobbies lobbies(Player player) {
        String current = currentServerOf(player);
        return new MenuPayload.Lobbies(current, this.entries(current, this.config.menus.lobbies));
    }

    /**
     * Turns the configured tiles into what the menu draws.
     *
     * <p>A configured server that Velocity does not know is left out rather than drawn dead: it
     * is a typo in the configuration, not a server that happens to be down, and offering it
     * would send whoever clicked it nowhere.
     */
    private List<MenuPayload.Servers.Server> entries(
            String current, List<ProxyConfig.MenuServer> configured) {

        List<MenuPayload.Servers.Server> servers = new ArrayList<>(configured.size());

        for (ProxyConfig.MenuServer entry : configured) {
            Optional<RegisteredServer> registered = this.proxy.getServer(entry.id);
            if (registered.isEmpty()) {
                continue;
            }

            // A server the player is standing on is reachable by definition, whatever the last
            // check made of it.
            boolean reachable = entry.id.equals(current) || this.health.isReachable(entry.id);

            servers.add(new MenuPayload.Servers.Server(
                    entry.id,
                    entry.name.isBlank() ? entry.id : entry.name,
                    registered.get().getPlayersConnected().size(),
                    reachable,
                    entry.slot,
                    entry.material,
                    entry.lore));
        }
        return servers;
    }

    private static String currentServerOf(Player player) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("");
    }
}
