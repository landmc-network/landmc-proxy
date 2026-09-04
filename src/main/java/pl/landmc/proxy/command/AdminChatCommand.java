package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.join.Join;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.util.Objects;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.rank.RankProvider;

/**
 * {@code /ac <wiadomość>} - staff chat spanning the whole network.
 *
 * <p>The sender's rank prefix comes from {@link RankProvider}, which is the only place that
 * knows LuckPerms. The original called {@code LuckPermsProvider.get()} inside the command, which
 * meant the command could not exist on a proxy without LuckPerms installed.
 */
@Command(name = "adminchat", aliases = "ac")
@Permission("landmc.command.adminchat")
public class AdminChatCommand {

    /** Who sees the channel. */
    public static final String SPY_PERMISSION = "landmc.adminchat.spy";

    private final VelocityNoticeService<ProxyMessages> notices;
    private final RankProvider ranks;

    public AdminChatCommand(VelocityNoticeService<ProxyMessages> notices, RankProvider ranks) {
        this.notices = Objects.requireNonNull(notices, "notices");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
    }

    @Execute
    void execute(@Context CommandSource sender, @Join("wiadomość") String message) {
        if (message.isBlank()) {
            return;
        }

        String name = sender instanceof Player player ? player.getUsername() : "Konsola";
        String prefix = sender instanceof Player player ? this.ranks.prefixOf(player) : "";

        this.notices.create()
                .onlinePlayers(SPY_PERMISSION)
                .console()
                .notice(messages -> messages.adminChatFormat)
                .formatter(new Formatter()
                        .register("{PLAYER}", name)
                        .register("{PREFIX}", prefix)
                        .register("{MESSAGE}", message))
                .send();
    }
}
