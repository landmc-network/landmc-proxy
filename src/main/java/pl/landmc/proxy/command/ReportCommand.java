package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.time.Duration;
import java.util.Objects;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.menu.MenuBridge;
import pl.landmc.proxy.report.ReportService;

/**
 * {@code /zglos <gracz>} - opens the menu of reasons somebody can be reported for.
 *
 * <p>The command only opens the menu; the report itself is a click, handled where the reasons
 * are known. That split is the old server's, and it is also what keeps the target honest: the
 * player being reported is remembered here, on the proxy, and never travels back with the
 * click. A modified client can send any reason it likes and still only report whoever it
 * actually ran the command on.
 *
 * <p>Both refusals - reporting yourself, and reporting the same player twice in a row - happen
 * before the menu opens rather than after a reason is picked, so nobody chooses one for nothing.
 */
@Command(name = "zglos", aliases = {"report", "cheater"})
@Permission("landmc.command.report")
public final class ReportCommand {

    private final ReportService reports;
    private final MenuBridge menus;
    private final VelocityNoticeService<ProxyMessages> notices;

    public ReportCommand(
            ReportService reports,
            MenuBridge menus,
            VelocityNoticeService<ProxyMessages> notices) {

        this.reports = Objects.requireNonNull(reports, "reports");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    void execute(@Context Player player, @Arg("gracz") Player reported) {
        if (player.getUniqueId().equals(reported.getUniqueId())) {
            this.notices.viewer(player, messages -> messages.reportSelf);
            return;
        }

        Duration wait = this.reports.remaining(player.getUniqueId(), reported.getUniqueId());
        if (!wait.isZero()) {
            this.notices.create()
                    .player(player.getUniqueId())
                    .notice(messages -> messages.reportCooldown)
                    .formatter(new Formatter()
                            // Rounded up: "wait 0s" is not an instruction anybody can follow.
                            .register("{TIME}", Long.toString(
                                    Math.max(1L, (wait.toMillis() + 999L) / 1000L))))
                    .send();
            return;
        }

        ReportService.Pending open = this.reports.open(player, reported);

        if (!this.menus.send(player, this.reports.payload(open))) {
            this.reports.forget(player.getUniqueId());
            this.notices.viewer(player, messages -> messages.reportUnavailable);
        }
    }
}
