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
 * {@code /serwery} - the server list, as a menu.
 *
 * <p>Registered on the proxy rather than on each backend, because the proxy is the only process
 * that knows what the other servers are and how many people are on them - and because a command
 * registered here works from every backend without being installed on any of them.
 */
@Command(name = "serwery", aliases = {"servers", "serwer"})
public final class ServerMenuCommand {

    private final ServerMenuService servers;
    private final MenuBridge bridge;
    private final VelocityNoticeService<ProxyMessages> notices;

    public ServerMenuCommand(
            ServerMenuService servers,
            MenuBridge bridge,
            VelocityNoticeService<ProxyMessages> notices) {

        this.servers = Objects.requireNonNull(servers, "servers");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    void execute(@Context Player player) {
        // Nothing here waits on anything: the server list is proxy state and each server's
        // health was checked in the background, so the menu is sent in the same tick.
        if (this.bridge.send(player, this.servers.payload(player))) {
            return;
        }

        // The player is between servers, or their backend has no menu plugin. Either way the
        // command has to say something.
        this.notices.create()
                .viewer(player)
                .notice(messages -> messages.menuUnavailable)
                .send();
    }
}
