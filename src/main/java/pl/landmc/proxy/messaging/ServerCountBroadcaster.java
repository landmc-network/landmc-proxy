package pl.landmc.proxy.messaging;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.landmc.platform.messaging.MessageBus;
import pl.landmc.proxy.menu.ServerHealth;
import pl.landmc.platform.messaging.message.MessageTarget;

/**
 * Tells the network how busy each server is, on a timer.
 *
 * <p>Every backend gets the same broadcast whether it wants it or not, which is the cheap way
 * round: the alternative is each of them asking, and the number is small, public and the same
 * for everybody. A backend with nothing to show it simply ignores the message.
 *
 * <p>Sent only while somebody could be reading it. A network with nobody on it is a network
 * where every sign says nought, and nobody is standing in front of one - so the broadcast stops
 * rather than filling Redis with the same message all night.
 */
public final class ServerCountBroadcaster {

    private final ProxyServer proxy;
    private final Object plugin;
    private final MessageBus bus;
    private final ServerHealth health;
    private final Duration interval;

    private ScheduledTask task;

    public ServerCountBroadcaster(
            ProxyServer proxy,
            Object plugin,
            MessageBus bus,
            ServerHealth health,
            Duration interval) {

        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.health = Objects.requireNonNull(health, "health");
        this.interval = Objects.requireNonNull(interval, "interval");
    }

    public void start() {
        if (this.task != null) {
            return;
        }

        this.task = this.proxy.getScheduler()
                .buildTask(this.plugin, this::broadcast)
                .repeat(this.interval)
                .schedule();
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void broadcast() {
        if (this.proxy.getPlayerCount() == 0) {
            return;
        }

        List<ServerCountsMessage.Server> servers = new ArrayList<>();
        for (RegisteredServer registered : this.proxy.getAllServers()) {
            String id = registered.getServerInfo().getName();
            servers.add(new ServerCountsMessage.Server(
                    id, registered.getPlayersConnected().size(), this.health.isReachable(id)));
        }

        this.bus.publish(
                MessageTarget.broadcast(),
                new ServerCountsMessage(servers, System.currentTimeMillis()));
    }
}
