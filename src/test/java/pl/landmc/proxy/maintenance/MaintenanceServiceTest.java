package pl.landmc.proxy.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.landmc.platform.config.ConfigService;
import pl.landmc.proxy.config.ProxyConfig;

class MaintenanceServiceTest {

    @Test
    void startsFromTheConfiguredState(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("config.yml"), "maintenance:\n  enabled: true\n");

        assertTrue(service(directory).isEnabled());
    }

    /**
     * The state has to survive a restart: a maintenance window that quietly ends because someone
     * restarted the proxy would let players in during an upgrade.
     */
    @Test
    void togglingIsWrittenBackToDisk(@TempDir Path directory) throws IOException {
        ConfigService configs = new ConfigService();
        ProxyConfig config = configs.load(directory, "config.yml", ProxyConfig.class);
        MaintenanceService maintenance = new MaintenanceService(config, configs);

        assertTrue(maintenance.setEnabled(true));

        assertTrue(Files.readString(directory.resolve("config.yml")).contains("enabled: true"));
        assertTrue(
                new ConfigService().load(directory, "config.yml", ProxyConfig.class).maintenance.enabled,
                "a restarted proxy must come up still in maintenance");
    }

    @Test
    void togglingToTheSameStateChangesNothing(@TempDir Path directory) {
        MaintenanceService maintenance = service(directory);

        assertFalse(maintenance.setEnabled(false));
        assertTrue(maintenance.setEnabled(true));
        assertFalse(maintenance.setEnabled(true));
    }

    @Test
    void reloadPicksUpAnEditedFile(@TempDir Path directory) throws IOException {
        ConfigService configs = new ConfigService();
        ProxyConfig config = configs.load(directory, "config.yml", ProxyConfig.class);
        MaintenanceService maintenance = new MaintenanceService(config, configs);

        Files.writeString(directory.resolve("config.yml"), "maintenance:\n  enabled: true\n");
        maintenance.reload();

        assertTrue(maintenance.isEnabled());
    }

    @Test
    void exposesTheBypassPermission(@TempDir Path directory) {
        assertEquals("landmc.maintenance.bypass", service(directory).bypassPermission());
    }

    private static MaintenanceService service(Path directory) {
        ConfigService configs = new ConfigService();
        return new MaintenanceService(configs.load(directory, "config.yml", ProxyConfig.class), configs);
    }
}
