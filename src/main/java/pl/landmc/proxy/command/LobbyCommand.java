package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import java.util.Objects;
import java.util.Optional;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.routing.RoutingService;

/**
 * {@code /lobby}, with {@code /hub} as an alias.
 *
 * <p>One class, not two: the commands do the same thing, and LiteCommands models "the same
 * command under another name" as an alias. A second class would be a copy waiting to drift.
 */
@Command(name = "lobby", aliases = "hub")
public class LobbyCommand {

    private final RoutingService routing;
    private final VelocityNoticeService<ProxyMessages> notices;

    public LobbyCommand(RoutingService routing, VelocityNoticeService<ProxyMessages> notices) {
        this.routing = Objects.requireNonNull(routing, "routing");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    void execute(@Context Player player) {
        Optional<RegisteredServer> fallback = this.routing.fallback();
        if (fallback.isEmpty()) {
            // Configured but not registered on this proxy - an operator error worth naming.
            this.notices.viewer(
                    player,
                    messages -> messages.serverNotFound,
                    new Formatter().register("{SERVER}", this.routing.fallbackName()));
            return;
        }

        TransferNotifier.connect(player, fallback.get(), this.routing, this.notices);
    }
}
