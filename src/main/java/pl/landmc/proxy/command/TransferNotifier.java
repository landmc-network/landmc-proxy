package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.routing.RoutingService;

/**
 * Turns a {@link RoutingService.TransferResult} into the message the player sees.
 *
 * <p>Exists so {@code /server}, {@code /lobby} and the fallback listener report a transfer the
 * same way. {@code RoutingService} stays free of messages, and the two commands do not each
 * carry their own copy of this switch.
 *
 * <p>The continuation runs on the thread Velocity completes the connection on. Adventure is
 * safe to use from there, so nothing is scheduled back onto another executor - Velocity has no
 * single main thread that this would need to return to.
 */
final class TransferNotifier {

    private TransferNotifier() {
    }

    static void connect(
            Player player,
            RegisteredServer target,
            RoutingService routing,
            VelocityNoticeService<ProxyMessages> notices) {

        String serverName = target.getServerInfo().getName();

        notices.viewer(
                player,
                messages -> messages.connecting,
                new Formatter().register("{SERVER}", serverName));

        routing.connect(player, target).thenAccept(result -> report(player, serverName, result, notices));
    }

    static void report(
            Player player,
            String serverName,
            RoutingService.TransferResult result,
            VelocityNoticeService<ProxyMessages> notices) {

        Formatter server = new Formatter().register("{SERVER}", serverName);

        switch (result) {
            // Velocity switches the player over; it already tells them where they are.
            case SUCCESS -> {
            }
            case ALREADY_CONNECTED -> notices.viewer(player, messages -> messages.alreadyConnected, server);
            case NO_SUCH_SERVER -> notices.viewer(player, messages -> messages.serverNotFound, server);
            case FAILED -> notices.viewer(player, messages -> messages.transferFailed, server);
        }
    }
}
