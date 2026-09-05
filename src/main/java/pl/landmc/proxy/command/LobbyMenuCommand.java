package pl.landmc.proxy.command;

import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import java.util.Objects;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.menu.MenuBridge;
import pl.landmc.proxy.menu.ServerMenuService;

/**
 * {@code /podserwery} - which copy of the lobby to stand in.
 *
 * <p>A different list from {@code /serwery} and deliberately so, as it was on the old server:
 * the compass leads to the servers you play on, this one to the hubs. With a single lobby it
 * shows a single entry saying you are already there, which is what the original did too.
 */
@Command(name = "podserwery", aliases = {"podserwer", "lobbies", "huby"})
public final class LobbyMenuCommand {

    private final ServerMenuService servers;
    private final MenuBridge bridge;
    private final VelocityNoticeService<ProxyMessages> notices;

    public LobbyMenuCommand(
            ServerMenuService servers,
            MenuBridge bridge,
            VelocityNoticeService<ProxyMessages> notices) {

        this.servers = Objects.requireNonNull(servers, "servers");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    void execute(@Context Player player) {
        // No I/O: the lobby list is proxy state and each one's health was checked on a timer.
        if (this.bridge.send(player, this.servers.lobbies(player))) {
            return;
        }

        this.notices.viewer(player, messages -> messages.menuUnavailable);
    }
}
