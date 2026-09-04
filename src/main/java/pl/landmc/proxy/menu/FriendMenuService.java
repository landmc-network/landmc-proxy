package pl.landmc.proxy.menu;

import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import pl.landmc.menus.protocol.MenuPayload;
import pl.landmc.proxy.friend.FriendRepository.FriendProfile;
import pl.landmc.proxy.friend.FriendService;

/**
 * Builds the friends menu.
 *
 * <p>Whether a friend counts as online, and which server they are named as being on, is settled
 * here and travels as a plain flag. The backend is not told enough to work it out for itself,
 * and that is the point: vanish is decided on the proxy, and a menu that looked players up on
 * its own would be a way to find a hidden administrator.
 */
public final class FriendMenuService {

    private final FriendService friends;

    public FriendMenuService(FriendService friends) {
        this.friends = Objects.requireNonNull(friends, "friends");
    }

    /**
     * Reads the list and the pending invitations, and turns them into a payload.
     *
     * <p>Two queries, both already asynchronous, waited on together rather than one after the
     * other - a menu that takes two round trips to open feels like a menu that is broken.
     */
    public CompletableFuture<MenuPayload.Friends> payload(Player viewer) {
        CompletableFuture<List<FriendProfile>> list = this.friends.list(viewer.getUniqueId());
        CompletableFuture<List<FriendProfile>> pending =
                this.friends.pendingRequests(viewer.getUniqueId());

        return list.thenCombine(pending, (friends, requests) ->
                new MenuPayload.Friends(this.entries(viewer, friends), requests.size()));
    }

    private List<MenuPayload.Friends.Friend> entries(Player viewer, List<FriendProfile> friends) {
        List<MenuPayload.Friends.Friend> entries = new ArrayList<>(friends.size());

        for (FriendProfile friend : friends) {
            boolean online = this.friends.isVisiblyOnline(viewer, friend.playerId());

            entries.add(new MenuPayload.Friends.Friend(
                    friend.displayName(),
                    online,
                    online ? this.friends.serverOf(viewer, friend.playerId()).orElse("") : ""));
        }

        // Online first, then alphabetically. A friend list is read to find somebody to play
        // with, and the ones who are here are the answer to that.
        entries.sort(Comparator
                .comparing(MenuPayload.Friends.Friend::online).reversed()
                .thenComparing(entry -> entry.name().toLowerCase(java.util.Locale.ROOT)));

        return entries;
    }
}
