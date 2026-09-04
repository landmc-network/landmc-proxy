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
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.player.PlayerPresenceService;
import pl.landmc.proxy.routing.FallbackPolicy;
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
    private final ProxyConfig config;
    private final ProxyMessages messages;
    private final ComponentFormatter formatter;
    private final Logger logger;

    public PlayerRoutingListener(
            RoutingService routing,
            PlayerPresenceService presence,
            ProxyConfig config,
            ProxyMessages messages,
            ComponentFormatter formatter,
            Logger logger) {

        this.routing = Objects.requireNonNull(routing, "routing");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.config = Objects.requireNonNull(config, "config");
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
     * Moves a player to the fallback when a backend drops them - but only when the kick looks
     * like a restart rather than a decision about that player.
     *
     * <p>{@link FallbackPolicy} makes that call; a ban or a moderation kick keeps its disconnect
     * screen, because quietly dropping such a player into the lobby would hide it from them.
     */
    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Optional<RegisteredServer> fallback = this.routing.fallback();
        String kickedFrom = event.getServer().getServerInfo().getName();
        String reason = event.getServerKickReason().map(this.formatter::plain).orElse(null);

        boolean redirect = fallback.isPresent()
                && FallbackPolicy.shouldRedirect(
                        this.config.fallback.enabled,
                        event.kickedDuringServerConnect(),
                        kickedFrom,
                        fallback.get().getServerInfo().getName(),
                        reason);

        if (!redirect) {
            // Leave the server's own kick screen alone when there is one; only replace it when
            // the backend gave no reason and we still cannot move the player anywhere.
            if (event.getServerKickReason().isEmpty()) {
                event.setResult(KickedFromServerEvent.DisconnectPlayer.create(
                        this.formatter.format(this.messages.noFallbackKick)));
            }
            return;
        }

        this.logger.info(
                "Moving {} from {} to the fallback after a backend kick",
                event.getPlayer().getUsername(), kickedFrom);
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(fallback.get()));
    }

    /** Forgets a player that left the proxy. */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.presence.onDisconnected(event.getPlayer().getUniqueId());
    }
}
