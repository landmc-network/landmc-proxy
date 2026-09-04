package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.util.Objects;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.maintenance.MaintenanceService;

/**
 * {@code /maintenance on|off|status}.
 *
 * <p>Three subcommands declared as three methods; LiteCommands parses the literal, so there is
 * no hand-written argument switch here.
 */
@Command(name = "maintenance")
@Permission("landmc.command.maintenance")
public class MaintenanceCommand {

    private final MaintenanceService maintenance;
    private final VelocityNoticeService<ProxyMessages> notices;

    public MaintenanceCommand(MaintenanceService maintenance, VelocityNoticeService<ProxyMessages> notices) {
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute(name = "on")
    void on(@Context CommandSource sender) {
        this.maintenance.setEnabled(true);
        this.notices.viewer(sender, messages -> messages.maintenanceEnabled);
    }

    @Execute(name = "off")
    void off(@Context CommandSource sender) {
        this.maintenance.setEnabled(false);
        this.notices.viewer(sender, messages -> messages.maintenanceDisabled);
    }

    @Execute(name = "status")
    void status(@Context CommandSource sender) {
        this.notices.viewer(
                sender,
                messages -> messages.maintenanceStatus,
                new Formatter().register("{STATE}", this.maintenance.isEnabled() ? "włączony" : "wyłączony"));
    }
}
