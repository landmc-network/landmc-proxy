package pl.landmc.proxy.bootstrap;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.rollczi.litecommands.LiteCommands;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import pl.landmc.platform.api.ModuleLifecycle;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.platform.config.ConfigService;
import pl.landmc.platform.messaging.MessageBus;
import pl.landmc.platform.notice.AudienceNoticeService;
import pl.landmc.platform.notice.NoticeServiceProvider;
import pl.landmc.platform.proxy.command.VelocityCommands;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.platform.proxy.packet.VelocityPacketEvents;
import pl.landmc.proxy.command.AdminChatCommand;
import pl.landmc.proxy.command.HelpOpCommand;
import pl.landmc.proxy.command.LobbyCommand;
import pl.landmc.proxy.command.MaintenanceCommand;
import pl.landmc.proxy.command.PrivateMessageCommands;
import pl.landmc.proxy.command.SendCommand;
import pl.landmc.proxy.command.ServerCommand;
import pl.landmc.proxy.command.TestMessageCommand;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.cooldown.CooldownMessenger;
import pl.landmc.proxy.cooldown.CooldownProtocol;
import pl.landmc.proxy.cooldown.GlobalCooldownService;
import pl.landmc.proxy.cooldown.GuiPacketInterceptor;
import pl.landmc.proxy.cooldown.PacketEventsGuiInterceptor;
import pl.landmc.proxy.listener.CooldownListener;
import pl.landmc.proxy.listener.MaintenanceListener;
import pl.landmc.proxy.listener.PlayerRoutingListener;
import pl.landmc.proxy.listener.PlayerSessionListener;
import pl.landmc.proxy.listener.ResourcePackListener;
import pl.landmc.proxy.maintenance.MaintenanceService;
import pl.landmc.proxy.messaging.PingMessage;
import pl.landmc.proxy.messaging.PongMessage;
import pl.landmc.proxy.messaging.ProxyMessaging;
import pl.landmc.proxy.player.PlayerPresenceService;
import pl.landmc.proxy.privatemessage.IgnoreStorage;
import pl.landmc.proxy.privatemessage.PrivateMessageService;
import pl.landmc.proxy.rank.RankProvider;
import pl.landmc.proxy.resourcepack.ManifestSource;
import pl.landmc.proxy.resourcepack.ResourcePackRebuiltMessage;
import pl.landmc.proxy.resourcepack.ResourcePackService;
import pl.landmc.proxy.routing.RoutingService;
import pl.landmc.proxy.server.ServerRegistry;

/**
 * Builds the proxy out of platform pieces and takes it down again.
 *
 * <p>Everything the plugin owns is constructed here, in the order its dependencies require, and
 * released in the reverse order on shutdown. The plugin class only calls {@link #start()} and
 * {@link #stop()}; the services themselves know nothing about how they were assembled.
 *
 * <p>Startup fails loudly. A proxy that came up without its configuration, or without the
 * messaging it was told to use, would hand players a broken network while reporting success.
 */
public final class ProxyBootstrap {

    private final ProxyServer proxy;
    private final PluginContainer container;
    private final Logger logger;
    private final Path dataDirectory;

    private final ModuleLifecycle lifecycle;

    /**
     * Read by the notice service's translation provider. Assigned during {@link #start()},
     * before anything can send a message, which is what lets the notice service exist before
     * the configuration it reads - the serdes pack it provides is needed to load that very file.
     */
    private ProxyMessages messages;

    private ConfigService configs;
    private ProxyConfig config;
    private VelocityPacketEvents packetEvents;
    private LiteCommands<CommandSource> commands;
    private PlayerPresenceService presence;
    private PrivateMessageService privateMessages;
    private GuiPacketInterceptor guiInterceptor = GuiPacketInterceptor.DISABLED;
    private ResourcePackService resourcePack;
    private MessageBus bus;

    public ProxyBootstrap(
            ProxyServer proxy, PluginContainer container, Logger logger, Path dataDirectory) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.container = Objects.requireNonNull(container, "container");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.lifecycle = new ModuleLifecycle(logger);
    }

    /**
     * @throws pl.landmc.platform.api.PlatformException when configuration or messaging cannot
     *     be initialised
     */
    public void start() {
        long startedAt = System.currentTimeMillis();
        this.logger.info("LandMC Proxy starting...");

        ComponentFormatter formatter = ComponentFormatter.standard();

        // The notice service is built first because loading a configuration that contains
        // Notice fields needs the serdes pack it exposes. It reads its translations through a
        // provider, so the config it will use does not have to exist yet.
        VelocityNoticeService<ProxyMessages> notices =
                new VelocityNoticeService<>(this.proxy, locale -> this.messages, formatter);

        this.configs = new ConfigService(notices.okaeriSerdes());
        this.config = this.configs.load(this.dataDirectory, "config.yml", ProxyConfig.class);
        this.messages = this.configs.load(this.dataDirectory, "messages.yml", ProxyMessages.class);
        this.logger.info("Loaded configuration.");

        NoticeServiceProvider<CommandSource> platformNotices =
                new AudienceNoticeService<>(this.messages.platform, formatter);

        ServerRegistry servers = new ServerRegistry(this.proxy);
        RoutingService routing = new RoutingService(servers, this.config);
        MaintenanceService maintenance = new MaintenanceService(this.config, this.configs);
        this.presence = new PlayerPresenceService();

        this.bus = ProxyMessaging.create(this.config, this.presence, this.logger);
        this.registerMessageHandlers();
        this.lifecycle.register(this.bus).enableAll();
        this.logger.info(
                "Messaging connected ({}).",
                this.config.messaging.enabled ? "Redis" : "in-process, Redis disabled");

        this.startPacketEvents();

        RankProvider ranks = RankProvider.create(this.logger);

        IgnoreStorage ignores = this.configs.load(this.dataDirectory, "ignores.yml", IgnoreStorage.class);
        this.privateMessages = new PrivateMessageService(this.proxy, notices, ignores, this.configs);

        this.commands = VelocityCommands.builder(this.proxy, formatter, platformNotices, this.logger)
                .commands(
                        new ServerCommand(servers, routing, notices),
                        new LobbyCommand(routing, notices),
                        new SendCommand(this.proxy, servers, routing, notices),
                        new MaintenanceCommand(maintenance, notices),
                        new HelpOpCommand(notices),
                        new AdminChatCommand(notices, ranks),
                        new PrivateMessageCommands.Message(this.privateMessages),
                        new PrivateMessageCommands.Reply(this.privateMessages),
                        new PrivateMessageCommands.Toggle(this.privateMessages, notices),
                        new PrivateMessageCommands.SocialSpy(this.privateMessages, notices),
                        new PrivateMessageCommands.Ignore(this.privateMessages, notices),
                        new TestMessageCommand(this.bus, this.config, notices))
                .build();
        this.logger.info("Registered 12 commands.");

        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new MaintenanceListener(maintenance, this.messages, formatter));
        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new PlayerRoutingListener(routing, this.presence, this.config, this.messages, formatter, this.logger));
        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new PlayerSessionListener(this.privateMessages));

        this.startCooldown(notices);
        this.startResourcePack(formatter);

        this.logger.info("Registered {} backend servers.", servers.count());
        if (!servers.exists(routing.fallbackName())) {
            this.logger.warn(
                    "Fallback server '{}' is not registered in velocity.toml - joining players will"
                            + " fall back to Velocity's own choice",
                    routing.fallbackName());
        }
        if (maintenance.isEnabled()) {
            this.logger.warn(
                    "Maintenance mode is ON - only players with '{}' can join",
                    maintenance.bypassPermission());
        }

        this.logger.info("LandMC Proxy ready ({} ms).", System.currentTimeMillis() - startedAt);
    }

    /**
     * Stops in the reverse order of {@link #start()}: commands come out first so nothing new
     * arrives, then the message bus and its Redis connection, then PacketEvents.
     */
    public void stop() {
        this.logger.info("LandMC Proxy stopping...");

        if (this.commands != null) {
            this.commands.unregister();
            this.commands = null;
        }

        // Closes the bus: fails the requests still waiting and shuts the transport's threads.
        this.lifecycle.disableAll();

        if (this.resourcePack != null) {
            this.resourcePack.close();
            this.resourcePack = null;
        }

        this.guiInterceptor.close();
        this.guiInterceptor = GuiPacketInterceptor.DISABLED;

        if (this.packetEvents != null) {
            this.packetEvents.disable();
            this.packetEvents = null;
        }

        if (this.presence != null) {
            this.presence.clear();
        }

        this.logger.info("LandMC Proxy stopped.");
    }

    /**
     * Initialises PacketEvents when it is installed.
     *
     * <p>It is an optional plugin dependency, not a shaded library: PacketEvents ships its own
     * Velocity plugin, and a second copy inside this jar would fight the installed one. Nothing
     * in the proxy uses packets yet, so a proxy without it starts normally rather than refusing
     * to come up over an integration that has no callers.
     */
    private void startPacketEvents() {
        if (!this.proxy.getPluginManager().isLoaded("packetevents")) {
            this.logger.info("PacketEvents is not installed; packet integrations stay disabled.");
            return;
        }

        this.packetEvents = new VelocityPacketEvents(
                this.proxy, this.container, this.logger, this.dataDirectory);
        this.packetEvents.load();
        this.packetEvents.enable();
        this.logger.info("PacketEvents ready (owner: {}).", this.packetEvents.isOwner());
    }

    /**
     * Answers {@code test.ping} on the proxy itself.
     *
     * <p>Makes {@code /testmessage <this proxy's id>} a complete round trip through the real
     * transport, so the messaging stack can be verified before any Paper node exists. A Paper
     * consumer registers the same pair on its side and answers for its own id.
     */
    /**
     * Brings the global cooldown up, with the packet-level part only when it can actually work.
     *
     * <p>The command cooldown and the backend synchronisation need nothing but plugin messages.
     * Throttling menu clicks needs PacketEvents, so that half installs only when the plugin is
     * present and the operator asked for it - a proxy without PacketEvents keeps the rest rather
     * than losing the feature or refusing to start.
     */
    private void startCooldown(VelocityNoticeService<ProxyMessages> notices) {
        GlobalCooldownService cooldowns = new GlobalCooldownService();
        CooldownMessenger messenger = new CooldownMessenger(cooldowns, this.config);

        this.proxy.getChannelRegistrar().register(CooldownProtocol.CHANNEL);

        if (this.config.cooldown.enabled && this.config.cooldown.interceptGuiPackets) {
            if (this.packetEvents == null) {
                this.logger.warn(
                        "GUI cooldown is enabled but PacketEvents is not installed;"
                                + " menu clicks are not throttled by the proxy.");
            }
            else {
                this.guiInterceptor = new PacketEventsGuiInterceptor(cooldowns, this.config, notices);
                this.logger.info("GUI cooldown active (packet interception).");
            }
        }

        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new CooldownListener(cooldowns, messenger, this.guiInterceptor));

        this.logger.info(
                "Global cooldown {} (command {}ms, GUI {}ms).",
                this.config.cooldown.enabled ? "enabled" : "disabled",
                this.config.cooldown.commandCooldownMillis,
                this.config.cooldown.guiCooldownMillis);
    }

    /**
     * Brings resource-pack delivery up.
     *
     * <p>The manifest is read once here; after that the builder announces rebuilds over the
     * message bus, which is what replaced polling an HTTP endpoint every fifteen seconds.
     */
    private void startResourcePack(ComponentFormatter formatter) {
        if (!this.config.resourcePack.enabled) {
            this.logger.info("Resource-pack delivery is disabled in config.yml.");
            return;
        }

        this.resourcePack = new ResourcePackService(
                this.proxy,
                this.container,
                new ManifestSource(this.config),
                this.config,
                formatter,
                this.logger);

        this.proxy.getChannelRegistrar().register(
                pl.landmc.proxy.resourcepack.ResourcePackProtocol.CHANNEL);
        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new ResourcePackListener(this.resourcePack));

        this.resourcePack.start();
        this.logger.info(
                "Resource-pack delivery enabled (initial connection {}).",
                this.config.resourcePack.waitBeforeInitialServer ? "gated" : "not gated");
    }

    private void registerMessageHandlers() {
        // A rebuild anywhere on the network reaches every proxy immediately.
        this.bus.subscribe(ResourcePackRebuiltMessage.class, (message, context) -> {
            if (this.resourcePack != null) {
                this.logger.info("Resource pack rebuilt ({}), re-reading the manifest.", message.sha1());
                this.resourcePack.refresh("rebuild announced by " + context.source());
            }
        });

        this.bus.subscribe(PingMessage.class, (message, context) -> {
            this.logger.debug("Ping from {}", context.source());
            context.reply(new PongMessage(this.bus.serverId(), message.sentAt()));
        });
    }
}
