package pl.landmc.proxy.bootstrap;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.argument.resolver.standard.DurationArgumentResolver;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import pl.landmc.platform.api.ModuleLifecycle;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.platform.config.ConfigPlaceholders;
import pl.landmc.platform.config.ConfigService;
import pl.landmc.platform.database.DatabaseService;
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
import pl.landmc.proxy.command.ReportCommand;
import pl.landmc.proxy.command.SendCommand;
import pl.landmc.proxy.command.ServerCommand;
import pl.landmc.proxy.command.LiveCommand;
import pl.landmc.proxy.command.ProfileCommand;
import pl.landmc.proxy.command.LobbyMenuCommand;
import pl.landmc.proxy.command.StatisticsCommand;
import pl.landmc.proxy.command.ServerMenuCommand;
import pl.landmc.proxy.command.TestMessageCommand;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.cooldown.CooldownMessenger;
import pl.landmc.proxy.cooldown.CooldownProtocol;
import pl.landmc.proxy.help.HelpProgressProtocol;
import pl.landmc.proxy.cooldown.GlobalCooldownService;
import pl.landmc.proxy.cooldown.GuiPacketInterceptor;
import pl.landmc.proxy.cooldown.PacketEventsGuiInterceptor;
import pl.landmc.proxy.listener.CommandExecuteListener;
import pl.landmc.proxy.listener.CooldownListener;
import pl.landmc.proxy.listener.JoinDebugListener;
import pl.landmc.proxy.listener.MaintenanceListener;
import pl.landmc.proxy.listener.MotdListener;
import pl.landmc.proxy.listener.PlayerRoutingListener;
import pl.landmc.proxy.listener.PlayerSessionListener;
import pl.landmc.proxy.listener.ResourcePackListener;
import pl.landmc.proxy.maintenance.MaintenanceService;
import pl.landmc.proxy.motd.MotdService;
import pl.landmc.proxy.messaging.PingMessage;
import pl.landmc.proxy.messaging.PongMessage;
import pl.landmc.proxy.messaging.ProxyMessaging;
import pl.landmc.proxy.player.PlayerPresenceService;
import pl.landmc.proxy.privatemessage.IgnoreStorage;
import pl.landmc.proxy.privatemessage.PrivateMessageService;
import pl.landmc.proxy.friend.FriendRepository;
import pl.landmc.proxy.friend.FriendService;
import pl.landmc.proxy.menu.FriendMenuService;
import pl.landmc.proxy.menu.MenuActions;
import pl.landmc.proxy.menu.MenuBridge;
import pl.landmc.proxy.menu.ServerHealth;
import pl.landmc.proxy.messaging.ServerCountBroadcaster;
import pl.landmc.proxy.live.KickStatusClient;
import pl.landmc.proxy.live.LiveRepository;
import pl.landmc.proxy.live.LiveService;
import pl.landmc.proxy.live.TwitchStatusClient;
import pl.landmc.proxy.menu.ProfileMenuService;
import pl.landmc.proxy.menu.StatisticsMenuService;
import pl.landmc.proxy.menu.ServerMenuService;
import pl.landmc.proxy.rank.RankProvider;
import pl.landmc.proxy.voucher.VoucherService;
import pl.landmc.proxy.command.BroadcastCommand;
import pl.landmc.proxy.command.FriendCommand;
import pl.landmc.proxy.command.VoucherCommands;
import pl.landmc.proxy.command.RankCommand;
import pl.landmc.proxy.command.SkinCommand;
import pl.landmc.proxy.resourcepack.ManifestSource;
import pl.landmc.proxy.resourcepack.ResourcePackRebuiltMessage;
import pl.landmc.proxy.resourcepack.ResourcePackService;
import pl.landmc.proxy.report.ReportService;
import pl.landmc.proxy.routing.RoutingService;
import pl.landmc.proxy.skin.SkinService;
import pl.landmc.proxy.vanish.VanishProvider;
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
    private SkinService skins;
    private DatabaseService database;
    private FriendService friends;
    private MenuBridge menuBridge;
    private ServerHealth serverHealth;
    private ServerCountBroadcaster serverCounts;
    private VoucherService vouchers;
    private LiveService live;
    private ReportService reports;

    /** Commands that are always present; the optional ones are counted alongside them. */
    private static final int CORE_COMMAND_COUNT = 13;
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

        this.configs = new ConfigService(
                ConfigPlaceholders.forPlugin(this.dataDirectory), notices.okaeriSerdes());
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

        // The database is registered before the bus so it is closed after it: a handler still
        // draining a Redis message must not find the connection pool already shut.
        if (this.config.friends.enabled || this.config.vouchers.enabled || this.config.live.enabled) {
            this.database = new DatabaseService(
                    "landmc-proxy", this.config.database, this.dataDirectory, this.logger);
            this.lifecycle.register(this.database);
        }
        this.lifecycle.register(this.bus).enableAll();
        this.logger.info(
                "Messaging connected ({}).",
                this.config.messaging.enabled ? "Redis" : "in-process, Redis disabled");

        this.startPacketEvents();

        RankProvider ranks = RankProvider.create(this.logger);

        VanishProvider vanish = VanishProvider.create(this.proxy, this.config, this.logger);
        this.startFriends(vanish);
        this.startVouchers();
        this.startLive();

        IgnoreStorage ignores = this.configs.load(this.dataDirectory, "ignores.yml", IgnoreStorage.class);
        this.privateMessages = new PrivateMessageService(
                this.proxy, notices, platformNotices, ignores, this.configs, vanish);

        // The seam between the proxy, which owns what a menu shows, and the backend, which
        // owns the inventory it is drawn in.
        this.menuBridge = new MenuBridge(this.proxy, this.logger);
        this.menuBridge.register();
        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(), this.menuBridge);

        this.serverHealth = new ServerHealth(this.proxy, this.config, this.logger);
        if (this.config.menus.serversEnabled) {
            this.serverHealth.start(this.container.getInstance().orElseThrow());
        }

        // Only the proxy can count the people on another server, so the signs in the lobby
        // are told rather than left to guess.
        if (this.config.serverCounts.enabled) {
            this.serverCounts = new ServerCountBroadcaster(
                    this.proxy,
                    this.container.getInstance().orElseThrow(),
                    this.bus,
                    this.serverHealth,
                    java.time.Duration.ofSeconds(
                            Math.max(1L, this.config.serverCounts.intervalSeconds)));
            this.serverCounts.start();
        }

        ServerMenuService serverMenu =
                new ServerMenuService(this.proxy, this.config, this.serverHealth);

        // Reports are a menu and a command over the same service, so it is built here and the
        // command picks it up below; switched off, neither the handler nor the command exists.
        this.reports = this.config.report.enabled
                ? new ReportService(this.config, notices, ranks, () -> this.messages)
                : null;

        new MenuActions(
                this.proxy, this.friends, routing, serverMenu, this.reports, notices, this.logger)
                .registerOn(this.menuBridge);

        Object[] optional = this.optionalCommands(notices, serverMenu, ranks);

        // LiteCommands ships a Duration resolver but does not register it; /setrank's optional
        // time argument is the reason this proxy wants one.
        this.commands = VelocityCommands.builder(this.proxy, formatter, platformNotices, this.logger)
                .argument(Duration.class, new DurationArgumentResolver<>())
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
                        new BroadcastCommand(notices, this.logger),
                        new TestMessageCommand(this.bus, this.config, notices))
                .commands(optional)
                .build();
        this.logger.info("Registered {} commands.", CORE_COMMAND_COUNT + optional.length);

        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new MaintenanceListener(maintenance, this.messages, formatter));

        MotdService motd = new MotdService(
                this.config, formatter, this.dataDirectory, this.logger);
        if (motd.isEnabled()) {
            this.proxy.getEventManager().register(
                    this.container.getInstance().orElseThrow(),
                    new MotdListener(motd, maintenance));
        }
        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new PlayerRoutingListener(routing, this.presence, this.config, this.messages, formatter, this.logger));
        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new PlayerSessionListener(
                        this.privateMessages, this.skins, this.friends, this.vouchers, this.live));

        this.startCooldown(notices);
        this.startResourcePack(formatter);
        this.startJoinDebug();

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

        this.friends = null;

        if (this.serverCounts != null) {
            this.serverCounts.stop();
            this.serverCounts = null;
        }

        if (this.serverHealth != null) {
            this.serverHealth.stop();
            this.serverHealth = null;
        }

        if (this.menuBridge != null) {
            this.menuBridge.unregister();
            this.menuBridge = null;
        }

        if (this.resourcePack != null) {
            this.resourcePack.close();
            this.resourcePack = null;
        }

        this.proxy.getChannelRegistrar().unregister(HelpProgressProtocol.CHANNEL);

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

        // One listener for both: the proxy reacts to a command either to throttle it or to
        // tell the backend it happened, and splitting that across two would mean parsing the
        // command line twice on every command from every player.
        if (this.config.helpProgress.enabled) {
            this.proxy.getChannelRegistrar().register(HelpProgressProtocol.CHANNEL);
        }
        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new CommandExecuteListener(cooldowns, this.config, notices));

        this.logger.info(
                "Global cooldown {} (command {}ms {}, GUI {}ms).",
                this.config.cooldown.enabled ? "enabled" : "disabled",
                this.config.cooldown.commandCooldownMillis,
                this.config.cooldown.enforceCommandsOnProxy
                        ? "enforced on the proxy"
                        : "synced to backends only",
                this.config.cooldown.guiCooldownMillis);
    }

    /**
     * The commands whose integrations may be absent.
     *
     * <p>Registering a command that answers "this feature is unavailable" trains players to
     * ignore it. If LuckPerms or SkinsRestorer is not installed, the command does not exist and
     * the proxy says so once, in the log.
     */
    private Object[] optionalCommands(
            VelocityNoticeService<ProxyMessages> notices,
            ServerMenuService serverMenu,
            RankProvider ranks) {

        java.util.List<Object> optional = new java.util.ArrayList<>(6);

        if (ranks.isAvailable()) {
            optional.add(new RankCommand(this.proxy, ranks, notices, this.logger));
        }

        if (this.friends != null) {
            optional.add(new FriendCommand(
                    this.friends,
                    new FriendMenuService(this.friends),
                    this.menuBridge,
                    notices,
                    this.config,
                    this.logger));
        }

        if (this.config.menus.serversEnabled) {
            optional.add(new ServerMenuCommand(serverMenu, this.menuBridge, notices));
        }

        optional.add(new ProfileCommand(
                new ProfileMenuService(this.friends, ranks), this.menuBridge, notices, this.logger));
        if (this.config.menus.lobbiesEnabled) {
            optional.add(new LobbyMenuCommand(serverMenu, this.menuBridge, notices));
        }
        if (this.reports != null) {
            optional.add(new ReportCommand(this.reports, this.menuBridge, notices));
        }

        optional.add(new StatisticsCommand(
                new StatisticsMenuService(this.friends, ranks, () -> this.messages),
                this.menuBridge, notices, this.logger));

        if (this.live != null) {
            optional.add(new LiveCommand(
                    this.live, this.proxy, notices, ComponentFormatter.standard(),
                    this.config, ranks, this.logger));
        }

        if (this.vouchers != null) {
            optional.add(new VoucherCommands.Redeem(this.vouchers, this.proxy, notices, this.logger));
            optional.add(new VoucherCommands.Generate(this.vouchers, notices, this.config, this.logger));
        }

        if (this.config.skin.enabled && SkinService.isAvailable(this.logger)) {
            this.skins = new SkinService(
                    this.proxy, this.container.getInstance().orElseThrow(), this.config, this.logger);
            optional.add(new SkinCommand(this.skins, notices));
        }

        return optional.toArray();
    }

    /**
     * Brings the friends list up on the database opened with the other platform modules.
     *
     * <p>The only part of the proxy that outlives a session, and the only reason there is a
     * database here at all - so the pool is only opened when the feature is on. The connection
     * itself is established earlier, with the other platform modules; this creates the tables
     * and sweeps stale invitations once it is up.
     *
     * @throws pl.landmc.platform.api.PlatformException when the database cannot be opened; a
     *     friends list that silently forgets everything is worse than a proxy that says why
     */
    private void startFriends(VanishProvider vanish) {
        if (this.database == null) {
            this.logger.info("Friends are disabled in config.yml; no database connection is opened.");
            return;
        }

        FriendRepository repository = new FriendRepository(this.database);
        this.friends = new FriendService(
                this.proxy, repository, this.database, this.config, vanish, this.logger);
        this.friends.start();

        this.logger.info(
                "Friends ready on {} (limit {}, requests expire after {} day(s)).",
                this.config.database.type,
                this.config.friends.maxFriends,
                this.config.friends.requestExpiryDays);
    }

    /**
     * Brings vouchers up on the database opened with the other platform modules.
     *
     * <p>Shares that database with the friends list rather than opening a second pool: two
     * features on one proxy, one connection pool.
     */
    /**
     * Brings /live up.
     *
     * <p>The HTTP client is built here and shared by both platform clients: one connection pool
     * and one set of threads for a feature that makes a request when somebody types a command,
     * rather than two of each sitting idle.
     */
    private void startLive() {
        if (!this.config.live.enabled) {
            return;
        }
        if (this.database == null) {
            this.logger.error("Live needs a database but none was opened; check config.yml.");
            return;
        }

        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        TwitchStatusClient twitch = new TwitchStatusClient(http, this.config, this.logger);
        KickStatusClient kick = new KickStatusClient(http, this.config, this.logger);

        this.live = new LiveService(
                new LiveRepository(this.database), this.config, java.util.List.of(twitch, kick));
        this.live.createTables();

        // Said once, at startup, rather than discovered by a streamer whose announcement was
        // refused: a platform with no credentials cannot be checked, and a stream on it can
        // never be announced.
        if (!twitch.isConfigured()) {
            this.logger.warn("Twitch has no client id/secret; /live cannot verify a Twitch stream.");
        }
        if (!kick.isConfigured()) {
            this.logger.warn("Kick has no client id/secret; /live cannot verify a Kick stream.");
        }

        this.logger.info(
                "Live ready (cooldown {} min, Twitch {}, Kick {}).",
                this.config.live.cooldownMinutes,
                twitch.isConfigured() ? "on" : "off",
                kick.isConfigured() ? "on" : "off");
    }

    private void startVouchers() {
        if (!this.config.vouchers.enabled) {
            return;
        }
        if (this.database == null) {
            this.logger.error("Vouchers need a database but none was opened; check config.yml.");
            return;
        }

        this.vouchers = new VoucherService(this.database, this.config);
        this.vouchers.createTables();

        this.logger.info("Vouchers ready ({} reward type(s)).", this.config.vouchers.types.size());
    }

    /** Registers the login tracer, but only while it is switched on. */
    private void startJoinDebug() {
        if (!this.config.joinDebug.enabled) {
            return;
        }

        this.proxy.getEventManager().register(
                this.container.getInstance().orElseThrow(),
                new JoinDebugListener(this.logger, this.config));
        this.logger.warn(
                "Join debugging is ON - every login writes a dozen lines to the console."
                        + " Switch join-debug off in config.yml once the problem is found.");
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
