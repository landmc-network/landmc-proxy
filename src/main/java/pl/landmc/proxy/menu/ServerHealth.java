package pl.landmc.proxy.menu;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Whether each backend can be reached, checked on a timer rather than when somebody asks.
 *
 * <p>Two things follow from that. A menu opens immediately, because the answer is already a map
 * lookup instead of a network round trip a player waits through. And the backends are probed a
 * fixed number of times a minute however many people open the menu, rather than once per player
 * per click.
 *
 * <p>It opens a socket rather than sending a Minecraft ping. A backend on this network runs with
 * {@code enable-status=false} - a server nobody is meant to reach directly has no business
 * answering a server list ping - so pinging one reports every healthy backend as dead. A socket
 * asks the question that actually matters: whether a player sent there would arrive.
 *
 * <p>The connect blocks, which is why this runs on the proxy's scheduler and not on the thread
 * that is serving somebody's connection.
 */
public final class ServerHealth {

    private final ProxyServer proxy;
    private final ProxyConfig config;
    private final Logger logger;

    /** Server id to whether it answered last time. Absent means it has not been checked yet. */
    private final Map<String, Boolean> reachable = new ConcurrentHashMap<>();

    private ScheduledTask task;

    public ServerHealth(ProxyServer proxy, ProxyConfig config, Logger logger) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Starts checking, and checks once straight away so the first menu is not blank. */
    public void start(Object plugin) {
        Duration interval = Duration.ofSeconds(Math.max(5, this.config.menus.healthIntervalSeconds));

        this.task = this.proxy.getScheduler()
                .buildTask(plugin, this::check)
                .repeat(interval)
                .schedule();
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    /**
     * What the last check found.
     *
     * <p>A server that has never been checked counts as reachable. Being wrong that way costs a
     * player one failed connection with a message; being wrong the other way hides a server that
     * is perfectly fine, which is worse and harder to notice.
     */
    public boolean isReachable(String serverId) {
        return this.reachable.getOrDefault(serverId, Boolean.TRUE);
    }

    void check() {
        // Both menus, because both draw a server as unavailable and a hub that is down is worth
        // knowing about for exactly the same reason a mode that is down is.
        List<ProxyConfig.MenuServer> configured = new ArrayList<>(this.config.menus.servers);
        configured.addAll(this.config.menus.lobbies);

        for (ProxyConfig.MenuServer entry : configured) {
            RegisteredServer server = this.proxy.getServer(entry.id).orElse(null);
            if (server == null) {
                this.reachable.remove(entry.id);
                continue;
            }

            boolean answered = connects(
                    server.getServerInfo().getAddress(),
                    (int) Math.max(1L, this.config.menus.reachabilityTimeoutMillis));

            Boolean previous = this.reachable.put(entry.id, answered);
            if (previous != null && previous != answered) {
                // Worth a line: a backend going away and coming back is exactly what somebody
                // reading the log after a complaint about the menu is looking for.
                this.logger.info(
                        "Server {} is now {}.", entry.id, answered ? "reachable" : "unreachable");
            }
        }
    }

    /**
     * Opens a connection and closes it again.
     *
     * <p>The address is resolved here rather than trusted to be resolved already: Velocity keeps
     * what {@code velocity.toml} said, and on this network that is a container name whose
     * address changes every time the container is recreated.
     */
    private static boolean connects(SocketAddress address, int timeoutMillis) {
        if (!(address instanceof InetSocketAddress configured)) {
            return false;
        }

        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(configured.getHostString(), configured.getPort()),
                    timeoutMillis);
            return true;
        }
        catch (IOException | RuntimeException exception) {
            return false;
        }
    }
}
