package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.debug.JoinTraceRegistry;

/**
 * Logs a login attempt from the first packet through to the disconnect.
 *
 * <p>"A player cannot join" is the report that arrives with no usable detail, and by the time
 * anyone looks the connection is long gone. This writes down what the proxy decided at each
 * step - whether the pre-login was allowed, which profile arrived, which server was chosen,
 * what a backend kick actually said - so the answer is in the log rather than in an attempt to
 * reproduce it.
 *
 * <p>Registered only when switched on in the configuration; the per-event check stays anyway,
 * since it costs nothing.
 *
 * <p>The pre-login is subscribed twice, at both ends of the priority range, to record what the
 * proxy was asked and what every other listener made of it.
 *
 * <p>UUIDs, client addresses and backend kick reasons are switchable one by one. An address is
 * personal data and a kick reason can carry a punishment message, so neither belongs in a log
 * by default just because a diagnostic feature is on.
 */
public final class JoinDebugListener {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    /** A kick reason can be a whole book; the log gets the beginning of it. */
    private static final int MAX_DETAIL_LENGTH = 500;

    private final Logger logger;
    private final ProxyConfig config;
    private final JoinTraceRegistry traces = new JoinTraceRegistry();

    public JoinDebugListener(Logger logger, ProxyConfig config) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Subscribe(priority = Short.MAX_VALUE)
    public void onPreLoginStart(PreLoginEvent event) {
        if (!this.enabled()) {
            return;
        }

        JoinTraceRegistry.JoinTrace trace = this.traces.start(event.getUsername());
        this.log(trace, "PRE_LOGIN_START", event.getUsername(), event.getUniqueId(), event.getConnection(),
                "result=pending");
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onPreLoginResult(PreLoginEvent event) {
        if (!this.enabled()) {
            return;
        }

        JoinTraceRegistry.JoinTrace trace = this.traces.pending(event.getUsername());
        String reason = event.getResult().getReasonComponent().map(this::plain).orElse("<none>");
        this.log(trace, "PRE_LOGIN_RESULT", event.getUsername(), event.getUniqueId(), event.getConnection(),
                "allowed=" + event.getResult().isAllowed()
                        + " onlineMode=" + event.getResult().isOnlineModeAllowed()
                        + " forceOffline=" + event.getResult().isForceOfflineMode()
                        + " reason=" + reason);
        if (!event.getResult().isAllowed()) {
            this.traces.reject(event.getUsername());
        }
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onGameProfile(GameProfileRequestEvent event) {
        if (!this.enabled()) {
            return;
        }

        JoinTraceRegistry.JoinTrace trace = this.traces.pending(event.getUsername());
        this.log(trace, "GAME_PROFILE", event.getUsername(), event.getGameProfile().getId(), event.getConnection(),
                "onlineMode=" + event.isOnlineMode()
                        + " properties=" + event.getGameProfile().getProperties().size());
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onLogin(LoginEvent event) {
        if (!this.enabled()) {
            return;
        }

        Player player = event.getPlayer();
        JoinTraceRegistry.JoinTrace trace = this.trace(player);
        String reason = event.getResult().getReasonComponent().map(this::plain).orElse("<none>");
        this.log(trace, "LOGIN_RESULT", player, "allowed=" + event.getResult().isAllowed() + " reason=" + reason);
        if (!event.getResult().isAllowed()) {
            this.traces.finish(player.getUniqueId());
        }
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onPostLogin(PostLoginEvent event) {
        if (!this.enabled()) {
            return;
        }

        Player player = event.getPlayer();
        this.log(this.trace(player), "POST_LOGIN", player,
                "onlineMode=" + player.isOnlineMode() + " currentServer=" + this.currentServer(player));
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onInitialServer(PlayerChooseInitialServerEvent event) {
        if (!this.enabled()) {
            return;
        }

        Player player = event.getPlayer();
        this.log(this.trace(player), "INITIAL_SERVER", player,
                "selected=" + event.getInitialServer().map(this::serverName).orElse("<none>"));
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (!this.enabled()) {
            return;
        }

        Player player = event.getPlayer();
        this.log(this.trace(player), "SERVER_PRE_CONNECT", player,
                "original=" + this.serverName(event.getOriginalServer())
                        + " previous=" + this.serverName(event.getPreviousServer())
                        + " allowed=" + event.getResult().isAllowed()
                        + " selected=" + event.getResult().getServer().map(this::serverName).orElse("<none>"));
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onServerConnected(ServerConnectedEvent event) {
        if (!this.enabled()) {
            return;
        }

        Player player = event.getPlayer();
        this.log(this.trace(player), "SERVER_CONNECTED", player,
                "server=" + this.serverName(event.getServer())
                        + " previous=" + event.getPreviousServer().map(this::serverName).orElse("<none>"));
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onServerPostConnect(ServerPostConnectEvent event) {
        if (!this.enabled()) {
            return;
        }

        Player player = event.getPlayer();
        this.log(this.trace(player), "SERVER_POST_CONNECT", player,
                "current=" + this.currentServer(player)
                        + " previous=" + this.serverName(event.getPreviousServer()));
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onKicked(KickedFromServerEvent event) {
        if (!this.enabled()) {
            return;
        }

        Player player = event.getPlayer();
        String reason = this.config.joinDebug.includeKickReason
                ? event.getServerKickReason().map(this::plain).orElse("<none>")
                : "<hidden>";
        this.log(this.trace(player), "SERVER_KICK", player,
                "server=" + this.serverName(event.getServer())
                        + " duringConnect=" + event.kickedDuringServerConnect()
                        + " result=" + event.getResult().getClass().getSimpleName()
                        + " reason=" + reason);
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onDisconnect(DisconnectEvent event) {
        if (!this.enabled()) {
            return;
        }

        Player player = event.getPlayer();
        this.log(this.trace(player), "DISCONNECT", player,
                "loginStatus=" + event.getLoginStatus()
                        + " currentServer=" + this.currentServer(player)
                        + " active=" + player.isActive());
        this.traces.finish(player.getUniqueId());
    }

    private JoinTraceRegistry.JoinTrace trace(Player player) {
        return this.traces.attach(player.getUsername(), player.getUniqueId());
    }

    private void log(JoinTraceRegistry.JoinTrace trace, String phase, Player player, String details) {
        this.log(trace, phase, player.getUsername(), player.getUniqueId(), player, details);
    }

    private void log(
            JoinTraceRegistry.JoinTrace trace,
            String phase,
            String username,
            UUID playerId,
            InboundConnection connection,
            String details) {

        ProtocolVersion protocol = connection.getProtocolVersion();
        String uuid = this.config.joinDebug.includeUuid && playerId != null ? playerId.toString() : "<hidden>";
        String remoteAddress = this.config.joinDebug.includeRemoteAddress
                ? String.valueOf(connection.getRemoteAddress())
                : "<hidden>";

        this.logger.info(
                "[join-debug #{} +{}ms] phase={} player={} uuid={} protocol={}({}) state={} remote={} {}",
                trace.id(),
                this.traces.elapsedMillis(trace),
                phase,
                username,
                uuid,
                String.join("/", protocol.getVersionsSupportedBy()),
                protocol.getProtocol(),
                connection.getProtocolState(),
                remoteAddress,
                this.limit(details));
    }

    private String currentServer(Player player) {
        return player.getCurrentServer()
                .map(connection -> this.serverName(connection.getServer()))
                .orElse("<none>");
    }

    private String serverName(RegisteredServer server) {
        return server == null ? "<none>" : server.getServerInfo().getName();
    }

    private String plain(Component component) {
        return this.limit(PLAIN_TEXT.serialize(component));
    }

    private String limit(String value) {
        if (value == null) {
            return "<none>";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= MAX_DETAIL_LENGTH
                ? singleLine
                : singleLine.substring(0, MAX_DETAIL_LENGTH) + "...";
    }

    private boolean enabled() {
        return this.config.joinDebug.enabled;
    }
}
