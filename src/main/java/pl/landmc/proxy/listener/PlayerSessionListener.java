package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import pl.landmc.proxy.privatemessage.PrivateMessageService;
import pl.landmc.proxy.skin.SkinService;

/**
 * Clears the per-session state a leaving player leaves behind.
 *
 * <p>Small on purpose, and separate from routing: forgetting a player is not a routing decision,
 * and the two would otherwise grow into one listener that does everything.
 */
public final class PlayerSessionListener {

    private final PrivateMessageService privateMessages;
    private final @Nullable SkinService skins;

    /** @param skins null when SkinsRestorer is absent and there are no skin cooldowns to clear */
    public PlayerSessionListener(PrivateMessageService privateMessages, @Nullable SkinService skins) {
        this.privateMessages = Objects.requireNonNull(privateMessages, "privateMessages");
        this.skins = skins;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        this.privateMessages.onDisconnect(playerId);
        if (this.skins != null) {
            this.skins.onDisconnect(playerId);
        }
    }
}
