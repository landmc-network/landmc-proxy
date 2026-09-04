package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import java.util.Objects;
import pl.landmc.proxy.privatemessage.PrivateMessageService;

/**
 * Clears the per-session state a leaving player leaves behind.
 *
 * <p>Small on purpose, and separate from routing: forgetting a player is not a routing decision,
 * and the two would otherwise grow into one listener that does everything.
 */
public final class PlayerSessionListener {

    private final PrivateMessageService privateMessages;

    public PlayerSessionListener(PrivateMessageService privateMessages) {
        this.privateMessages = Objects.requireNonNull(privateMessages, "privateMessages");
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.privateMessages.onDisconnect(event.getPlayer().getUniqueId());
    }
}
