package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent;
import java.util.Objects;
import pl.landmc.proxy.resourcepack.ResourcePackService;

/**
 * Connects resource-pack delivery to Velocity's events.
 *
 * <p>The interesting one is {@link #onServerPreConnect}: it returns an {@link EventTask} so
 * Velocity suspends the connection rather than blocking its event thread while the player
 * downloads. Only the first connection is gated - a later server switch never waits.
 */
public final class ResourcePackListener {

    private final ResourcePackService resourcePack;

    public ResourcePackListener(ResourcePackService resourcePack) {
        this.resourcePack = Objects.requireNonNull(resourcePack, "resourcePack");
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        this.resourcePack.onPostLogin(event.getPlayer());
    }

    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) {
        if (event.getPreviousServer() != null) {
            return null;
        }

        return EventTask.resumeWhenComplete(
                this.resourcePack.awaitInitialPack(event.getPlayer()).thenAccept(allowed -> {
                    if (!allowed) {
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    }
                }));
    }

    @Subscribe
    public void onStatus(PlayerResourcePackStatusEvent event) {
        this.resourcePack.onStatus(event);
    }

    @Subscribe
    public void onBackendOffer(ServerResourcePackSendEvent event) {
        this.resourcePack.onBackendOffer(event);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        this.resourcePack.onPluginMessage(event);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.resourcePack.onDisconnect(event.getPlayer().getUniqueId());
    }
}
