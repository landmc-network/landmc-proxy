package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import java.util.Objects;
import pl.landmc.proxy.maintenance.MaintenanceService;
import pl.landmc.proxy.motd.MotdService;

/**
 * Answers the server list.
 *
 * <p>Thin on purpose: the event says who is asking and the service decides what they see. What
 * this contributes is the one fact the service cannot know, which is whether the network is
 * currently closed.
 *
 * <p>Whoever is pinging has not connected and has no identity yet, so nobody can be exempted
 * here - a member of staff with the bypass permission still sees the maintenance list, and then
 * gets in anyway. That is the right way round: the list is a sign on the door, not the lock.
 */
public final class MotdListener {

    private final MotdService motd;
    private final MaintenanceService maintenance;

    public MotdListener(MotdService motd, MaintenanceService maintenance) {
        this.motd = Objects.requireNonNull(motd, "motd");
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        event.setPing(this.motd.apply(event.getPing(), this.maintenance.isEnabled()));
    }
}
