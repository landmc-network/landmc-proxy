package pl.landmc.proxy.cooldown;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Objects;
import java.util.Optional;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Keeps the backend servers and the proxy agreeing about a player's cooldown.
 *
 * <p>The cooldown is global: clicking through a menu on one server and running a command on
 * another share it. The proxy owns the state because it is the only process that sees both, and
 * this exchanges it with the backends over a plugin message channel.
 *
 * <p>Backends report what a player did ({@code MARK_GUI}, {@code MARK_COMMAND}) and whether a
 * menu is open; the proxy answers with the remaining time so the backend can enforce it locally
 * without a round trip per click.
 */
public final class CooldownMessenger {

    private final GlobalCooldownService cooldowns;
    private final ProxyConfig config;

    public CooldownMessenger(GlobalCooldownService cooldowns, ProxyConfig config) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Handles one plugin message from a backend. */
    public void handle(PluginMessageEvent event) {
        if (!CooldownProtocol.CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        // Never forward this channel onward; it is between the proxy and its backends.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection source)) {
            return;
        }

        Optional<CooldownProtocol.BackendMessage> decoded =
                CooldownProtocol.decodeBackendMessage(event.getData());
        if (decoded.isEmpty()) {
            return;
        }

        Player player = source.getPlayer();
        // Ignore a message from a backend the player has already left, so a slow server cannot
        // set a cooldown for a session that moved on.
        if (player.getCurrentServer().map(connection -> connection != source).orElse(true)) {
            return;
        }

        long now = System.currentTimeMillis();
        ProxyConfig.CooldownSection settings = this.config.cooldown;

        switch (decoded.get().action()) {
            case CooldownProtocol.MARK_GUI -> {
                if (settings.enabled) {
                    this.cooldowns.markGui(player.getUniqueId(), now, settings.guiCooldownMillis);
                }
            }
            case CooldownProtocol.MARK_COMMAND -> {
                if (settings.enabled) {
                    this.cooldowns.markCommand(player.getUniqueId(), now, settings.commandCooldownMillis);
                }
            }
            case CooldownProtocol.GUI_OPEN -> this.cooldowns.setGuiOpen(player.getUniqueId(), true);
            case CooldownProtocol.GUI_CLOSE -> this.cooldowns.setGuiOpen(player.getUniqueId(), false);
            default -> {
                // SYNC_REQUEST and anything else: just answer with the current state below.
            }
        }

        this.sendState(player);
    }

    public void sendState(Player player) {
        this.sendState(player, null);
    }

    /**
     * Sends the player's remaining cooldown to the backend they are on.
     *
     * @param expectedServer when set, only sends if the player is still on that backend - used
     *     right after a server switch, where the event may fire after another one already moved
     *     them
     */
    public void sendState(Player player, RegisteredServer expectedServer) {
        ProxyConfig.CooldownSection settings = this.config.cooldown;
        if (!settings.enabled || !player.isActive()) {
            return;
        }

        ServerConnection connection = player.getCurrentServer().orElse(null);
        if (connection == null || (expectedServer != null && connection.getServer() != expectedServer)) {
            return;
        }

        long now = System.currentTimeMillis();
        GlobalCooldownService.CooldownState state = this.cooldowns.state(player.getUniqueId(), now);

        byte[] payload = CooldownProtocol.encodeState(
                player.getUniqueId(),
                Math.max(0L, state.commandUntil() - now),
                Math.max(0L, state.guiUntil() - now),
                Math.max(0L, settings.commandCooldownMillis),
                Math.max(0L, settings.guiCooldownMillis));

        try {
            connection.sendPluginMessage(CooldownProtocol.CHANNEL, payload);
        }
        catch (IllegalStateException exception) {
            // Velocity can close the backend connection between getCurrentServer() and the send.
            // The next ServerConnectedEvent re-synchronises, so there is nothing to recover here.
        }
    }
}
