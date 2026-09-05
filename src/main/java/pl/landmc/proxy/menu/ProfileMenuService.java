package pl.landmc.proxy.menu;

import com.velocitypowered.api.proxy.Player;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import pl.landmc.menus.protocol.MenuPayload;
import pl.landmc.proxy.friend.FriendRepository.FriendProfile;
import pl.landmc.proxy.friend.FriendService;
import pl.landmc.proxy.rank.RankProvider;

/**
 * Builds a player's own profile.
 *
 * <p>Only what this proxy owns goes in: the name it knows them by, the rank it can ask
 * LuckPerms for, how many friends they have, and where they are standing. Anything belonging to
 * another plugin is reached through a button rather than read out of its table.
 *
 * <p>Counts, not lists. The friends tile shows a number and opens the friends menu, which builds
 * its own payload - sending the whole list here as well would send it twice for one click.
 */
public final class ProfileMenuService {

    private final @Nullable FriendService friends;
    private final RankProvider ranks;

    public ProfileMenuService(@Nullable FriendService friends, RankProvider ranks) {
        this.friends = friends;
        this.ranks = Objects.requireNonNull(ranks, "ranks");
    }

    public CompletableFuture<MenuPayload.Profile> payload(Player player) {
        String server = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("");

        // The rank is whatever LuckPerms is configured to show; with no rank system installed
        // it is empty, and the menu says so rather than inventing a default.
        String rank = this.ranks.prefixOf(player);

        if (this.friends == null) {
            return CompletableFuture.completedFuture(
                    new MenuPayload.Profile(player.getUsername(), rank, 0, 0, server));
        }

        CompletableFuture<List<FriendProfile>> list = this.friends.list(player.getUniqueId());
        CompletableFuture<List<FriendProfile>> pending =
                this.friends.pendingRequests(player.getUniqueId());

        // Waited on together rather than one after the other: a profile that takes two round
        // trips to open feels like a profile that is broken.
        return list.thenCombine(pending, (friendList, requests) -> new MenuPayload.Profile(
                player.getUsername(), rank, friendList.size(), requests.size(), server));
    }

    /** Whether the friends tile leads anywhere on this proxy. */
    public boolean hasFriends() {
        return this.friends != null;
    }
}
