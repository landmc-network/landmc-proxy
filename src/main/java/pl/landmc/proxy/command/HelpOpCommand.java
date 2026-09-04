package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.cooldown.Cooldown;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.join.Join;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;

/**
 * {@code /helpop <wiadomość>} - asks staff for help from anywhere in the network.
 *
 * <p>Two things the original did by hand are now the framework's job. The per-player cooldown
 * was a {@code Delay<UUID>} map plus a duration formatter; it is now {@code @Cooldown}, and the
 * message a throttled player sees comes from the platform's shared
 * {@code COMMAND_COOLDOWN}. Delivering to staff was a stream over every online player filtered
 * by permission; the notice service resolves that set itself.
 */
@Command(name = "helpop")
@Permission("landmc.command.helpop")
public class HelpOpCommand {

    /** Who receives the reports. */
    public static final String RECEIVE_PERMISSION = "landmc.command.helpop.receive";

    private final VelocityNoticeService<ProxyMessages> notices;

    public HelpOpCommand(VelocityNoticeService<ProxyMessages> notices) {
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    @Cooldown(key = "landmc-helpop", count = 30, unit = ChronoUnit.SECONDS, bypass = "landmc.command.helpop.nodelay")
    void execute(@Context Player player, @Join("wiadomość") String message) {
        if (message.isBlank()) {
            return;
        }

        Formatter placeholders = new Formatter()
                .register("{PLAYER}", player.getUsername())
                .register("{MESSAGE}", message)
                .register("{SERVER}", currentServerOf(player));

        // The viewer provider resolves the permission itself, so no stream over all players here.
        this.notices.create()
                .onlinePlayers(RECEIVE_PERMISSION)
                .console()
                .notice(messages -> messages.helpOpReport)
                .formatter(placeholders)
                .send();

        this.notices.viewer(player, messages -> messages.helpOpSent);
    }

    private static String currentServerOf(Player player) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("?");
    }
}
