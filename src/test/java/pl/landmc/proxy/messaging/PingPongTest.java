package pl.landmc.proxy.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.landmc.platform.messaging.MessageBus;
import pl.landmc.platform.messaging.message.MessageTarget;
import pl.landmc.platform.messaging.request.RequestTimeoutException;
import pl.landmc.platform.messaging.serialization.MessageRegistry;
import pl.landmc.platform.messaging.serialization.MessageSerializer;
import pl.landmc.platform.messaging.transport.LocalMessageTransport;

/**
 * The {@code test.ping -> test.pong} round trip from the first milestone.
 *
 * <p>Runs over the platform's in-process transport, so it needs no Redis and runs in CI. What
 * it covers is everything above the wire: the message types the proxy registers, the request
 * being correlated to its response, and the handler the bootstrap installs. Swapping in
 * {@code RedisMessageTransport} changes only how the bytes travel, and that layer has its own
 * test in the platform.
 */
class PingPongTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PingPongTest.class);

    private final List<MessageBus> buses = new ArrayList<>();

    @AfterEach
    void closeBuses() {
        this.buses.forEach(MessageBus::close);
        this.buses.clear();
    }

    @Test
    void proxyGetsAPongFromAPaperNode() throws Exception {
        LocalMessageTransport proxyWire = new LocalMessageTransport("proxy-1");
        LocalMessageTransport paperWire = LocalMessageTransport.joining(proxyWire, "skyblock-1");

        MessageBus proxy = this.bus("proxy-1", proxyWire);
        MessageBus paper = this.bus("skyblock-1", paperWire);

        // What a Paper consumer will register on its side.
        paper.subscribe(PingMessage.class, (message, context) ->
                context.reply(new PongMessage(paper.serverId(), message.sentAt())));

        proxy.enable();
        paper.enable();

        long sentAt = Instant.now().toEpochMilli();
        PongMessage pong = proxy
                .request(
                        MessageTarget.server("skyblock-1"),
                        new PingMessage(proxy.serverId(), sentAt),
                        PongMessage.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals("skyblock-1", pong.from());
        assertEquals(sentAt, pong.sentAt(), "the ping's timestamp must come back for the round trip");
        assertEquals(0, proxy.pendingRequests());
    }

    /**
     * The proxy answers its own ping, which is what makes {@code /testmessage <this proxy>} a
     * complete round trip before any Paper node exists.
     */
    @Test
    void proxyAnswersItsOwnPing() throws Exception {
        LocalMessageTransport wire = new LocalMessageTransport("proxy-1");
        MessageBus proxy = this.bus("proxy-1", wire);

        proxy.subscribe(PingMessage.class, (message, context) ->
                context.reply(new PongMessage(proxy.serverId(), message.sentAt())));
        proxy.enable();

        PongMessage pong = proxy
                .request(
                        MessageTarget.server("proxy-1"),
                        new PingMessage("proxy-1", Instant.now().toEpochMilli()),
                        PongMessage.class)
                .get(5, TimeUnit.SECONDS);

        assertEquals("proxy-1", pong.from());
    }

    @Test
    void aSilentNodeFailsTheRequestInsteadOfHanging() {
        LocalMessageTransport proxyWire = new LocalMessageTransport("proxy-1");
        LocalMessageTransport.joining(proxyWire, "skyblock-1");

        MessageBus proxy = this.bus("proxy-1", proxyWire);
        proxy.enable();

        CompletableFuture<PongMessage> answer = proxy.request(
                MessageTarget.server("skyblock-1"),
                new PingMessage("proxy-1", Instant.now().toEpochMilli()),
                PongMessage.class,
                Duration.ofMillis(150));

        ExecutionException failure =
                assertThrows(ExecutionException.class, () -> answer.get(5, TimeUnit.SECONDS));

        assertInstanceOf(RequestTimeoutException.class, failure.getCause());
    }

    private MessageBus bus(String serverId, LocalMessageTransport transport) {
        MessageRegistry registry = new MessageRegistry()
                .register(PingMessage.TYPE, PingMessage.class)
                .register(PongMessage.TYPE, PongMessage.class);

        MessageBus bus = MessageBus
                .builder(serverId, transport, new MessageSerializer(registry), LOGGER)
                .build();
        this.buses.add(bus);
        return bus;
    }
}
