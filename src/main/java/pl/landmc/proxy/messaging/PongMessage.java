package pl.landmc.proxy.messaging;

import pl.landmc.platform.messaging.message.NetworkMessage;

/**
 * The answer to a {@link PingMessage}, carrying the id of the node that replied.
 *
 * <p>Moves into the shared network API module alongside {@code PingMessage} once a Paper
 * project answers it.
 *
 * @param sentAt the ping's timestamp, echoed so the caller measures the round trip without
 *     depending on the two clocks agreeing
 */
public record PongMessage(String from, long sentAt) implements NetworkMessage {

    public static final String TYPE = "test.pong";

    @Override
    public String type() {
        return TYPE;
    }
}
