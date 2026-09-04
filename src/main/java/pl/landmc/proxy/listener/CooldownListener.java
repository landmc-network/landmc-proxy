package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import java.util.Objects;
import pl.landmc.proxy.cooldown.CooldownMessenger;
import pl.landmc.proxy.cooldown.GlobalCooldownService;
import pl.landmc.proxy.cooldown.GuiPacketInterceptor;

/**
 * Connects the cooldown to Velocity's events.
 *
 * <p>A player arriving on a backend is told their remaining cooldown straight away, so a server
 * switch cannot be used to skip it. Leaving the proxy drops their state - the cooldown is a
 * session concern, not something to persist.
 */
public final class CooldownListener {

    private final GlobalCooldownService cooldowns;
    private final CooldownMessenger messenger;
    private final GuiPacketInterceptor interceptor;

    public CooldownListener(
            GlobalCooldownService cooldowns, CooldownMessenger messenger, GuiPacketInterceptor interceptor) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        this.messenger.handle(event);
    }

    /** Hands the new backend the cooldown the player already carries. */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        this.messenger.sendState(event.getPlayer(), event.getServer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.cooldowns.remove(event.getPlayer().getUniqueId());
        this.interceptor.remove(event.getPlayer().getUniqueId());
    }
}
