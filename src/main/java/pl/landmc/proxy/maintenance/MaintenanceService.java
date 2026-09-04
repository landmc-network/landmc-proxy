package pl.landmc.proxy.maintenance;

import com.velocitypowered.api.proxy.Player;
import java.util.Objects;
import pl.landmc.platform.config.ConfigService;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Whether the network is closed, and to whom.
 *
 * <p>The state is one flag plus a bypass permission, and it lives in {@code config.yml} so it
 * survives a proxy restart - a maintenance window that silently ends because someone restarted
 * the proxy is worse than no maintenance mode at all.
 *
 * <p>No scheduling, no countdowns, no planned windows. Those need a policy nobody has decided
 * yet, and the flag is what the login check actually reads.
 *
 * <p>Read on every login and written only by an operator, so the flag is volatile and nothing
 * more elaborate is needed.
 */
public final class MaintenanceService {

    private final ProxyConfig config;
    private final ConfigService configs;

    private volatile boolean enabled;

    public MaintenanceService(ProxyConfig config, ConfigService configs) {
        this.config = Objects.requireNonNull(config, "config");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.enabled = config.maintenance.enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Turns maintenance on or off and writes it back to {@code config.yml}.
     *
     * @return true when the state actually changed
     */
    public boolean setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return false;
        }

        this.enabled = enabled;
        this.config.maintenance.enabled = enabled;
        // Through the platform rather than Okaeri's own save(): the service writes atomically,
        // so a crash mid-write cannot leave a truncated config.yml behind.
        this.configs.save(this.config);
        return true;
    }

    /** Whether this player may enter while maintenance is on. */
    public boolean canBypass(Player player) {
        Objects.requireNonNull(player, "player");
        return player.hasPermission(this.config.maintenance.bypassPermission);
    }

    /** Whether this player must be turned away right now. */
    public boolean shouldReject(Player player) {
        return this.enabled && !this.canBypass(player);
    }

    public String bypassPermission() {
        return this.config.maintenance.bypassPermission;
    }

    /** Re-reads the flag after the configuration was reloaded from disk. */
    public void reload() {
        this.configs.reloadAll();
        this.enabled = this.config.maintenance.enabled;
    }
}
