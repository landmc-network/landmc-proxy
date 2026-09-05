package pl.landmc.proxy.menu;

import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import pl.landmc.menus.protocol.MenuPayload;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.friend.FriendService;
import pl.landmc.proxy.rank.RankProvider;

/**
 * The statistics tab, filled with what this proxy actually knows.
 *
 * <p>Which is not much yet, and that is the honest state of the network rather than a gap in
 * this class: there are no islands, no jobs and no minigames to count. What is here is real -
 * the rank LuckPerms resolves, the server they are standing on, how many friends they have -
 * and the tab says so plainly when there is nothing else.
 *
 * <p>Every entry carries its own slot and material, because the menu drawing them knows nothing
 * about statistics. Adding one when SkyBlock has something worth counting is a line here, or a
 * line in whichever plugin owns that number - not a change to the wire format.
 */
public final class StatisticsMenuService {

    /** Where the entries sit: the middle row of a six-row menu, spread out. */
    private static final int RANK_SLOT = 20;
    private static final int SERVER_SLOT = 22;
    private static final int FRIENDS_SLOT = 24;

    private final @Nullable FriendService friends;
    private final RankProvider ranks;
    private final Supplier<ProxyMessages> messages;

    public StatisticsMenuService(
            @Nullable FriendService friends,
            RankProvider ranks,
            Supplier<ProxyMessages> messages) {

        this.friends = friends;
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public CompletableFuture<MenuPayload.Statistics> payload(Player player) {
        Objects.requireNonNull(player, "player");

        ProxyMessages messages = this.messages.get();

        String rank = this.ranks.prefixOf(player);
        String server = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("");

        List<MenuPayload.Statistics.Entry> entries = new ArrayList<>();
        entries.add(new MenuPayload.Statistics.Entry(
                messages.statisticsRank,
                rank.isBlank() ? messages.statisticsNoRank : rank,
                "PAPER",
                RANK_SLOT));
        entries.add(new MenuPayload.Statistics.Entry(
                messages.statisticsServer, server, "COMPASS", SERVER_SLOT));

        if (this.friends == null) {
            return CompletableFuture.completedFuture(
                    new MenuPayload.Statistics(player.getUsername(), entries));
        }

        return this.friends.list(player.getUniqueId()).thenApply(list -> {
            entries.add(new MenuPayload.Statistics.Entry(
                    messages.statisticsFriends,
                    Integer.toString(list.size()),
                    "PLAYER_HEAD",
                    FRIENDS_SLOT));

            return new MenuPayload.Statistics(player.getUsername(), entries);
        });
    }
}
