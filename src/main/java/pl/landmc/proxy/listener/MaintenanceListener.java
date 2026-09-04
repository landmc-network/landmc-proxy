package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import java.util.Objects;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.maintenance.MaintenanceService;

/**
 * Turns players away while the network is closed.
 *
 * <p>{@code LoginEvent} is the right hook: the player exists, so their permissions can be
 * checked, but they have not been routed to a backend yet. Denying here shows the disconnect
 * screen instead of connecting them and kicking a moment later.
 *
 * <p>One permission check per login, no scans.
 */
public final class MaintenanceListener {

    private final MaintenanceService maintenance;
    private final ProxyMessages messages;
    private final ComponentFormatter formatter;

    public MaintenanceListener(
            MaintenanceService maintenance, ProxyMessages messages, ComponentFormatter formatter) {
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!this.maintenance.shouldReject(event.getPlayer())) {
            return;
        }

        event.setResult(ResultedEvent.ComponentResult.denied(
                this.formatter.format(this.messages.maintenanceKick)));
    }
}
