package pl.landmc.proxy.messaging;

import java.util.Objects;
import org.slf4j.Logger;
import pl.landmc.platform.messaging.MessageBus;
import pl.landmc.platform.messaging.PlayerPresence;
import pl.landmc.platform.messaging.redis.RedisMessageTransport;
import pl.landmc.platform.messaging.serialization.MessageRegistry;
import pl.landmc.platform.messaging.serialization.MessageSerializer;
import pl.landmc.platform.messaging.transport.LocalMessageTransport;
import pl.landmc.platform.messaging.transport.MessageTransport;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.player.PlayerPresenceService;

/**
 * Assembles the platform's message bus for this proxy.
 *
 * <p>Everything here is wiring; the bus, the transport, the envelopes and the correlation of
 * requests all come from {@code platform-messaging}. There is no Redis client in this project.
 *
 * <p>Two things worth reading twice:
 *
 * <p>With messaging switched off the bus is still built, over the platform's in-process
 * transport. The proxy then starts without Redis and every call site keeps working - it simply
 * has nobody to talk to. The alternative, a null bus, would put a null check in front of every
 * publish for the sake of a development convenience.
 *
 * <p>The presence service is registered as the bus's {@code PlayerLocator}, which is what makes
 * a player-targeted message reach one backend instead of the whole network. The proxy's
 * {@code PlayerPresence} answers false: a player is connected to the proxy but is *on* a
 * backend, and the backend is what should handle a message aimed at them.
 */
public final class ProxyMessaging {

    private ProxyMessaging() {
    }

    /**
     * Builds the bus and registers the message types this proxy understands.
     *
     * @throws pl.landmc.platform.messaging.MessagingException when Redis is configured but the
     *     transport cannot be created - a proxy that silently loses cross-server messaging is
     *     worse than one that refuses to start
     */
    public static MessageBus create(
            ProxyConfig config, PlayerPresenceService presence, Logger logger) {

        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(presence, "presence");
        Objects.requireNonNull(logger, "logger");

        String serverId = config.proxy.serverId;

        MessageRegistry registry = new MessageRegistry()
                .register(PingMessage.TYPE, PingMessage.class)
                .register(PongMessage.TYPE, PongMessage.class);

        MessageSerializer serializer = new MessageSerializer(registry);
        MessageTransport transport = transport(config, serverId, serializer, logger);

        return MessageBus.builder(serverId, transport, serializer, logger)
                .playerPresence(PlayerPresence.NONE)
                .playerLocator(presence)
                .build();
    }

    private static MessageTransport transport(
            ProxyConfig config, String serverId, MessageSerializer serializer, Logger logger) {

        if (!config.messaging.enabled) {
            logger.warn(
                    "Messaging is disabled in config.yml - {} will not see other instances", serverId);
            return new LocalMessageTransport(serverId);
        }

        return new RedisMessageTransport(config.messaging.redis, serverId, serializer, logger);
    }
}
