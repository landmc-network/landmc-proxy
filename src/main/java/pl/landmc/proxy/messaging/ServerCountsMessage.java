package pl.landmc.proxy.messaging;

import java.util.List;
import java.util.Objects;
import pl.landmc.platform.messaging.message.NetworkMessage;

/**
 * How many players are on each server, told to everybody who is listening.
 *
 * <p>Only the proxy knows this. A backend sees the people standing on it and nothing else, so a
 * sign in the lobby saying how busy SkyBlock is has to be told - and asking for it would be a
 * round trip every two seconds, per server, forever. Broadcasting it costs one message either
 * way and arrives whether anybody asked or not.
 *
 * <p>Sent on a timer rather than when somebody joins or leaves. The number on a sign is read by
 * people walking past it, and a burst of a hundred messages when a full server restarts would
 * buy nothing that two seconds of patience does not.
 *
 * <p><strong>Where this belongs:</strong> deliberately identical to {@code landmc-lobby}'s copy
 * - same wire type, same fields. Two projects need it and there is no shared network API module,
 * so for now it exists twice and the wire format is what keeps them compatible. When a third
 * needs it, the pair moves into a shared module; that is the point at which duplicating stops
 * being cheaper than sharing.
 *
 * @param servers one entry per server the proxy has, whether anybody is on it or not
 * @param sentAt epoch millis, so a listener can tell stale counts from a quiet network
 */
public record ServerCountsMessage(List<Server> servers, long sentAt) implements NetworkMessage {

    public static final String TYPE = "network.server-counts";

    public ServerCountsMessage {
        servers = List.copyOf(Objects.requireNonNull(servers, "servers"));
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * One server's state.
     *
     * @param id the name the proxy knows it by, which is what a sign asks for
     * @param online how many are connected to it right now
     * @param reachable whether the last health check got an answer, so a sign can say the mode
     *     is down rather than say nought players and look merely quiet
     */
    public record Server(String id, int online, boolean reachable) {

        public Server {
            Objects.requireNonNull(id, "id");
        }
    }
}
