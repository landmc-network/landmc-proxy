package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Objects;
import java.util.function.Supplier;
import pl.landmc.platform.component.ComponentFormatter;
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
    private final ProxyServer proxy;
    private final ComponentFormatter formatter;
    private final Supplier<ProxyMessages> messages;

    public MaintenanceCommand(
            MaintenanceService maintenance,
            VelocityNoticeService<ProxyMessages> notices,
            ProxyServer proxy,
            ComponentFormatter formatter,
            Supplier<ProxyMessages> messages) {

        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Closes the network, and empties it.
     *
     * <p>Both, because the reason to close it is usually that something is about to be
     * restarted under whoever is standing on it - stopping new logins and leaving the current
     * ones in place is half a maintenance mode. Staff who can bypass stay.
     */
    @Execute(name = "on")
    void on(@Context CommandSource sender) {
        this.maintenance.setEnabled(true);

        int sent = this.maintenance.disconnectEveryone(
                this.proxy, this.formatter.format(this.messages.get().maintenanceKick));

        this.notices.viewer(
                sender,
                messages -> messages.maintenanceEnabled,
                new Formatter().register("{COUNT}", Integer.toString(sent)));
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
