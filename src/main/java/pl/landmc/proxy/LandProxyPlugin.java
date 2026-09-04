package pl.landmc.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.slf4j.Logger;
import pl.landmc.proxy.bootstrap.ProxyBootstrap;

/**
 * The Velocity entry point for the LandMC network.
 *
 * <p>It holds no logic of its own: routing, maintenance, presence and messaging live in their
 * own services, and {@link ProxyBootstrap} is what assembles them. This class exists to receive
 * Velocity's lifecycle events and hand them on.
 *
 * <p>{@code velocity-plugin.json} is generated from the annotation below by Velocity's
 * annotation processor, so the descriptor cannot drift away from the code.
 */
@Plugin(
        id = "landmc-proxy",
        name = "LandMC Proxy",
        version = "1.0.0-SNAPSHOT",
        description = "Warstwa wejściowa sieci LandMC: routing, tryb serwisowy i komunikacja z instancjami Paper.",
        url = "https://github.com/landmc-network/landmc-proxy",
        authors = {"Crispi"},
        // Optional: PacketEvents is initialised for features that will need it, and a proxy
        // without it must still start. Declared so Velocity loads it first when present.
        dependencies = @Dependency(id = "packetevents", optional = true))
public final class LandProxyPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ProxyBootstrap bootstrap;

    @Inject
    public LandProxyPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.bootstrap = new ProxyBootstrap(
                this.proxy,
                this.proxy.getPluginManager().ensurePluginContainer(this),
                this.logger,
                this.dataDirectory);
        this.bootstrap.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.bootstrap != null) {
            this.bootstrap.stop();
            this.bootstrap = null;
        }
    }
}
