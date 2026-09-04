package pl.landmc.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import pl.landmc.proxy.friend.FriendService;
import pl.landmc.proxy.privatemessage.PrivateMessageService;
import pl.landmc.proxy.skin.SkinService;

/**
 * Keeps track of what a session starts and what it leaves behind.
 *
 * <p>Small on purpose, and separate from routing: forgetting a player is not a routing decision,
 * and the two would otherwise grow into one listener that does everything.
 */
public final class PlayerSessionListener {

    private final PrivateMessageService privateMessages;
    private final @Nullable SkinService skins;
    private final @Nullable FriendService friends;

    /**
     * @param skins null when SkinsRestorer is absent and there are no skin cooldowns to clear
     * @param friends null when the friends list is switched off and no names need recording
     */
    public PlayerSessionListener(
            PrivateMessageService privateMessages,
            @Nullable SkinService skins,
            @Nullable FriendService friends) {

        this.privateMessages = Objects.requireNonNull(privateMessages, "privateMessages");
        this.skins = skins;
        this.friends = friends;
    }

    /**
     * Records the name a player is using.
     *
     * <p>A friends list is mostly people who are offline, and this is what lets them be shown
     * by name rather than as an id.
     */
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (this.friends != null) {
            this.friends.onJoin(event.getPlayer());
        }
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
