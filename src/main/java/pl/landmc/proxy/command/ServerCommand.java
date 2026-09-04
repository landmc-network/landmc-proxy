package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
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
 * {@code /server} - list the backends, or move to one.
 *
 * <p>Without an argument it prints the list as text. A menu would be nicer and is not the point
 * yet; a player needs to know what they can type before anything else is worth building.
 */
@Command(name = "server")
@Permission("landmc.command.server")
public class ServerCommand {

    private final ServerRegistry servers;
    private final RoutingService routing;
    private final VelocityNoticeService<ProxyMessages> notices;

    public ServerCommand(
            ServerRegistry servers, RoutingService routing, VelocityNoticeService<ProxyMessages> notices) {
        this.servers = Objects.requireNonNull(servers, "servers");
        this.routing = Objects.requireNonNull(routing, "routing");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    void list(@Context CommandSource sender) {
        this.notices.viewer(
                sender,
                messages -> messages.serverList,
                new Formatter().register("{SERVERS}", String.join(", ", this.servers.names())));
    }

    /**
     * Moves the sender to a backend.
     *
     * <p>{@code @Context Player} makes this player-only; LiteCommands rejects a console sender
     * with the platform's {@code COMMAND_PLAYER_ONLY} message, so there is no manual check here.
     */
    @Execute
    void connect(@Context Player player, @Arg("serwer") String serverId) {
        Optional<RegisteredServer> target = this.servers.get(serverId);
        if (target.isEmpty()) {
            this.notices.viewer(
                    player,
                    messages -> messages.serverNotFound,
                    new Formatter().register("{SERVER}", serverId));
            return;
        }

        TransferNotifier.connect(player, target.get(), this.routing, this.notices);
    }
}
