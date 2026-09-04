package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.util.Objects;
import java.util.Optional;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.routing.RoutingService;
import pl.landmc.proxy.server.ServerRegistry;

/**
 * {@code /send <gracz|all> <serwer>} - moves someone else, or everyone, to a backend.
 *
 * <p>Ported from skytop-velocity-commons, but the permission check, the argument parsing and the
 * usage message are gone: LiteCommands does all three, and its failures already route to the
 * platform's shared messages. What is left is the decision and the transfer.
 */
@Command(name = "send")
@Permission("landmc.command.send")
public class SendCommand {

    private static final String EVERYONE = "all";

    private final ProxyServer proxy;
    private final ServerRegistry servers;
    private final RoutingService routing;
    private final VelocityNoticeService<ProxyMessages> notices;

    public SendCommand(
            ProxyServer proxy,
            ServerRegistry servers,
            RoutingService routing,
            VelocityNoticeService<ProxyMessages> notices) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.servers = Objects.requireNonNull(servers, "servers");
        this.routing = Objects.requireNonNull(routing, "routing");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    void execute(
            @Context CommandSource sender,
            @Arg("gracz") String targetName,
            @Arg("serwer") String serverId) {

        Optional<RegisteredServer> target = this.servers.get(serverId);
        if (target.isEmpty()) {
            this.notices.viewer(
                    sender,
                    messages -> messages.serverNotFound,
                    new Formatter().register("{SERVER}", serverId));
            return;
        }

        RegisteredServer destination = target.get();
        String serverName = destination.getServerInfo().getName();

        if (EVERYONE.equalsIgnoreCase(targetName)) {
            this.sendEveryone(sender, destination, serverName);
            return;
        }

        // Velocity indexes players by name, so this is a lookup rather than a scan.
        Optional<Player> player = this.proxy.getPlayer(targetName);
        if (player.isEmpty()) {
            this.notices.viewer(
                    sender,
                    messages -> messages.playerNotFound,
                    new Formatter().register("{PLAYER}", targetName));
            return;
        }

        Player moved = player.get();
        this.routing.connect(moved, destination).thenAccept(result -> this.notices.viewer(
                sender,
                messages -> result == RoutingService.TransferResult.SUCCESS
                        ? messages.sendSuccess
                        : messages.sendFailed,
                new Formatter()
                        .register("{PLAYER}", moved.getUsername())
                        .register("{SERVER}", serverName)));
    }

    /**
     * Moves everyone at once.
     *
     * <p>The one place in the proxy that legitimately iterates every player - the command means
     * "all of them". Each transfer is fired without waiting, so a slow backend does not hold the
     * loop, and the sender is told how many were moved rather than getting one message per
     * player.
     */
    private void sendEveryone(CommandSource sender, RegisteredServer destination, String serverName) {
        int moved = 0;
        for (Player player : this.proxy.getAllPlayers()) {
            player.createConnectionRequest(destination).fireAndForget();
            moved++;
        }

        this.notices.viewer(
                sender,
                messages -> messages.sendSuccessAll,
                new Formatter()
                        .register("{SERVER}", serverName)
                        .register("{COUNT}", moved));
    }
}
