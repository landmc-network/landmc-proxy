package pl.landmc.proxy.rank;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.jspecify.annotations.Nullable;

/**
 * The one class in the proxy that mentions a LuckPerms type.
 *
 * <p>Kept apart from {@link RankProvider} so that nothing loads it unless LuckPerms is actually
 * installed - see the note there about class verification.
 *
 * <p>Prefix and group reads come from LuckPerms' own cache, so they are memory access rather
 * than I/O and are safe to call while handling a chat message. Assigning a rank is storage
 * access and never blocks: LuckPerms does the work on its own threads and the caller is handed
 * a future.
 */
final class LuckPermsRankProvider implements RankProvider {

    private final LuckPerms luckPerms;

    private LuckPermsRankProvider(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
    }

    /** @throws IllegalStateException when LuckPerms is present but has not started yet */
    static RankProvider bind() {
        return new LuckPermsRankProvider(LuckPermsProvider.get());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String prefixOf(Player player) {
        Objects.requireNonNull(player, "player");

        return this.metaOf(player)
                .map(CachedMetaData::getPrefix)
                .filter(prefix -> !prefix.isBlank())
                .orElse("");
    }

    @Override
    public String groupOf(Player player) {
        Objects.requireNonNull(player, "player");

        return this.metaOf(player)
                .map(CachedMetaData::getPrimaryGroup)
                .filter(group -> !group.isBlank())
                .orElse("");
    }

    @Override
    public CompletableFuture<RankAssignment> assign(
            ProxyServer proxy, String targetName, String groupName, @Nullable Duration duration) {

        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(groupName, "groupName");

        Group group = this.luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return CompletableFuture.completedFuture(RankAssignment.groupNotFound());
        }

        return this.findUniqueId(proxy, targetName).thenCompose(playerId -> playerId == null
                ? CompletableFuture.completedFuture(RankAssignment.playerNotFound())
                : this.applyGroup(playerId, group, duration));
    }

    private CompletableFuture<RankAssignment> applyGroup(
            UUID playerId, Group group, @Nullable Duration duration) {

        return this.luckPerms.getUserManager().loadUser(playerId).thenCompose(user -> {
            InheritanceNode.Builder node = InheritanceNode.builder(group);
            if (duration != null) {
                node.expiry(duration);
            }

            // Replacing rather than adding: this is "set the rank", so a player promoted twice
            // does not end up inheriting both groups.
            user.data().clear(existing -> existing instanceof InheritanceNode);
            user.data().add(node.build());
            user.setPrimaryGroup(group.getName());

            return this.luckPerms.getUserManager()
                    .saveUser(user)
                    .thenApply(ignored -> RankAssignment.assigned(group.getName(), nameOf(user, playerId)));
        });
    }

    /**
     * The player's id, from the online players when possible and from storage otherwise, so
     * promoting somebody standing in front of you costs no lookup at all.
     */
    private CompletableFuture<UUID> findUniqueId(ProxyServer proxy, String targetName) {
        Optional<Player> online = proxy.getPlayer(targetName);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online.get().getUniqueId());
        }

        return this.luckPerms.getUserManager().lookupUniqueId(targetName);
    }

    private Optional<CachedMetaData> metaOf(Player player) {
        return Optional.ofNullable(this.luckPerms.getUserManager().getUser(player.getUniqueId()))
                .map(user -> user.getCachedData().getMetaData());
    }

    private static String nameOf(User user, UUID playerId) {
        String username = user.getUsername();
        return username == null ? playerId.toString() : username;
    }
}
