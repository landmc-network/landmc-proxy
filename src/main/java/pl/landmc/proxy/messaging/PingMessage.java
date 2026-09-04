package pl.landmc.proxy.messaging;

import pl.landmc.platform.messaging.message.NetworkMessage;

/**
 * A request that asks a node to prove it is listening.
 *
 * <p>Its only job is to make the Proxy → Redis → Paper → Proxy path visible before any feature
 * depends on it. {@code /testmessage <server>} sends one and waits for {@link PongMessage}.
 *
 * <p><strong>Where this belongs:</strong> both ends of the network need this class, so once a
 * Paper project answers it, the pair moves into a shared network API module that both depend
 * on. It is here rather than in {@code platform-messaging} because the platform provides the
 * bus, not the traffic - putting concrete messages there would make every future project
 * inherit vocabulary it does not use.
 *
 * @param sentAt epoch millis on the sender, echoed back so the caller can report a round trip
 */
public record PingMessage(String from, long sentAt) implements NetworkMessage {

    public static final String TYPE = "test.ping";

    @Override
    public String type() {
        return TYPE;
    }
}
