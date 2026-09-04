package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.player.PlayerPresenceService;
import pl.landmc.proxy.routing.RoutingService;

/**
 * Keeps the presence index current and decides where a player goes.
 *
 * <p>Every handler touches exactly one entry - the player the event is about. Nothing here
 * iterates over the online players or the server list.
 */
public final class PlayerRoutingListener {

    private final RoutingService routing;
    private final PlayerPresenceService presence;
    private final ProxyMessages messages;
    private final ComponentFormatter formatter;
    private final Logger logger;

    public PlayerRoutingListener(
            RoutingService routing,
            PlayerPresenceService presence,
            ProxyMessages messages,
            ComponentFormatter formatter,
            Logger logger) {

        this.routing = Objects.requireNonNull(routing, "routing");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Sends a joining player to the configured fallback.
     *
     * <p>Velocity would otherwise use the first entry of its own {@code try} list, which is a
     * second place to keep the network's entry point configured. Leaving the event's choice
     * alone when the fallback is missing means a misconfigured id degrades to Velocity's
     * default rather than to no server at all.
     */
    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Optional<RegisteredServer> fallback = this.routing.fallback();
        if (fallback.isEmpty()) {
            this.logger.warn(
                    "Fallback server '{}' is not registered on this proxy; leaving the initial server to Velocity",
                    this.routing.fallbackName());
            return;
        }

        event.setInitialServer(fallback.get());
    }

    /** Records the backend a player just landed on. */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        this.presence.onConnected(
                event.getPlayer().getUniqueId(), event.getServer().getServerInfo().getName());
    }

    /**
     * Moves a player to the fallback when a backend drops them.
     *
     * <p>Only when they are not already being sent somewhere and the fallback is not the server
     * that just kicked them - redirecting a player back to the server that refused them would
     * loop.
     */
    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Optional<RegisteredServer> fallback = this.routing.fallback();
        String kickedFrom = event.getServer().getServerInfo().getName();

        if (fallback.isEmpty() || fallback.get().getServerInfo().getName().equalsIgnoreCase(kickedFrom)) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(
                    this.formatter.format(this.messages.noFallbackKick)));
            return;
        }

        event.setResult(KickedFromServerEvent.RedirectPlayer.create(fallback.get()));
    }

    /** Forgets a player that left the proxy. */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.presence.onDisconnected(event.getPlayer().getUniqueId());
    }
}
