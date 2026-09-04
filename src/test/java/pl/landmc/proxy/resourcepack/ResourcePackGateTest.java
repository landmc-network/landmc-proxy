package pl.landmc.proxy.resourcepack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Covers the one decision in the delivery service that can take the whole network down: whether
 * a joining player is held at the gate.
 *
 * <p>Everything else in the service needs a live proxy - real players, real packet
 * acknowledgements - but this branch is reachable with nothing running, and it is the branch
 * where being wrong means nobody can log in.
 */
class ResourcePackGateTest {

    @Test
    @DisplayName("an unreachable manifest endpoint must not hold joining players at the gate")
    void doesNotGateWhenNoManifestIsLoaded() {
        ProxyConfig config = new ProxyConfig();
        config.resourcePack.enabled = true;
        config.resourcePack.waitBeforeInitialServer = true;
        // Nothing is listening here, so the service starts with no manifest at all - the state
        // a proxy is in when the pack host is down.
        config.resourcePack.manifestUrl = "http://127.0.0.1:1/manifest.json";

        try (ResourcePackService service = service(config)) {
            assertTrue(
                    service.awaitInitialPack(player()).getNow(false),
                    "a player joined while no pack was known and was not let through");
        }
    }

    @Test
    @DisplayName("delivery switched off in config lets every player straight through")
    void doesNotGateWhenDisabled() {
        ProxyConfig config = new ProxyConfig();
        config.resourcePack.enabled = false;

        try (ResourcePackService service = service(config)) {
            assertTrue(service.awaitInitialPack(player()).getNow(false));
        }
    }

    private static ResourcePackService service(ProxyConfig config) {
        return new ResourcePackService(
                stub(ProxyServer.class),
                stub(PluginContainer.class),
                new ManifestSource(config),
                config,
                ComponentFormatter.standard(),
                NOPLogger.NOP_LOGGER);
    }

    private static Player player() {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(
                ResourcePackGateTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getUsername" -> "Crispi";
                    case "isActive" -> true;
                    case "toString" -> "Player(" + id + ")";
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                ResourcePackGateTest.class.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> null);
    }
}
