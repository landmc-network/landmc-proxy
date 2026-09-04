package pl.landmc.proxy.menu;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import pl.landmc.menus.protocol.MenuAction;
import pl.landmc.menus.protocol.MenuKind;
import pl.landmc.menus.protocol.MenuPayload;
import pl.landmc.menus.protocol.MenuProtocol;
import pl.landmc.menus.protocol.MenuProtocolException;

/**
 * Carries a menu's contents out to the server a player is standing on, and carries their clicks
 * back.
 *
 * <p>The proxy owns the lists a menu shows and has no inventories; the backend has inventories
 * and owns nothing. This is the seam between the two.
 *
 * <p>An action is only accepted from a {@link ServerConnection}. That single check is what makes
 * the return path trustworthy: a message a client writes itself reaches the proxy as a
 * {@code Player}, and one it sends to its backend is ignored there, so the only way an action
 * arrives here is that somebody actually clicked a menu. Even then it is treated as a request -
 * every handler decides for itself whether that player may do what was asked, exactly as if they
 * had typed the command.
 */
public final class MenuBridge {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(MenuProtocol.CHANNEL);

    private final ProxyServer proxy;
    private final Logger logger;
    private final Map<MenuKind, MenuActionHandler> handlers = new EnumMap<>(MenuKind.class);

    public MenuBridge(ProxyServer proxy, Logger logger) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Starts listening. The event listener is registered by the caller. */
    public void register() {
        this.proxy.getChannelRegistrar().register(CHANNEL);
    }

    public void unregister() {
        this.proxy.getChannelRegistrar().unregister(CHANNEL);
    }

    /** Says who deals with clicks from a given menu. */
    public void handler(MenuKind kind, MenuActionHandler handler) {
        this.handlers.put(
                Objects.requireNonNull(kind, "kind"), Objects.requireNonNull(handler, "handler"));
    }

    /**
     * Sends a menu to the server the player is on.
     *
     * @return false when they are between servers, or the backend has no plugin listening - in
     *     which case the caller should fall back to answering in chat rather than leaving the
     *     command looking as though it did nothing
     */
    public boolean send(Player player, MenuPayload payload) {
        Optional<ServerConnection> connection = player.getCurrentServer();
        if (connection.isEmpty()) {
            return false;
        }

        try {
            return connection.get().sendPluginMessage(CHANNEL, MenuProtocol.encode(payload));
        }
        catch (IllegalStateException exception) {
            // The connection went away between the check and the write, which is a player who
            // is changing servers - not a failure worth a stack trace.
            return false;
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        // Never let this reach the client: the payload half of this channel names which server
        // each of a player's friends is on.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection connection)) {
            // A client writing to the channel directly. Nothing here is a command it could not
            // already run, but there is no reason to read it either.
            return;
        }

        byte[] message = event.getData();
        if (!MenuProtocol.isAction(message)) {
            return;
        }

        MenuAction action;
        try {
            action = MenuProtocol.decodeAction(message);
        }
        catch (MenuProtocolException exception) {
            this.logger.debug("Unreadable menu action from {}: {}",
                    connection.getServerInfo().getName(), exception.getMessage());
            return;
        }

        MenuActionHandler handler = this.handlers.get(action.menu());
        if (handler == null) {
            this.logger.debug("No handler for a {} menu action", action.menu());
            return;
        }

        Player player = connection.getPlayer();
        try {
            handler.handle(player, action);
        }
        catch (RuntimeException exception) {
            this.logger.error(
                    "Menu action {} from {} failed", action.action(), player.getUsername(), exception);
        }
    }

    /** What deals with a click from one menu. */
    @FunctionalInterface
    public interface MenuActionHandler {

        void handle(Player player, MenuAction action);
    }
}
