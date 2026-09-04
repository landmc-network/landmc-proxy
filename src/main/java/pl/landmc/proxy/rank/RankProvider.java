package pl.landmc.proxy.rank;

import com.velocitypowered.api.proxy.Player;
import java.util.Objects;
import java.util.Optional;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.slf4j.Logger;

/**
 * Reads a player's rank prefix from LuckPerms.
 *
 * <p>The one place in the proxy that touches LuckPerms. Everything else asks this, so a feature
 * that wants a prefix does not have to care whether LuckPerms is installed - and the proxy
 * starts on a server without it, which the original could not: it called
 * {@code LuckPermsProvider.get()} in a command constructor, so the whole plugin failed to load.
 *
 * <p>Reads come from LuckPerms' own cache, so this is memory access rather than I/O and is safe
 * to call while handling a chat message.
 */
public final class RankProvider {

    private final LuckPerms luckPerms;

    private RankProvider(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /**
     * Binds to LuckPerms when it is installed, otherwise returns a provider that yields no
     * prefixes. Never throws: a missing optional integration must not stop the proxy.
     */
    public static RankProvider create(Logger logger) {
        Objects.requireNonNull(logger, "logger");

        try {
            RankProvider provider = new RankProvider(LuckPermsProvider.get());
            logger.info("LuckPerms found; rank prefixes are available.");
            return provider;
        }
        catch (IllegalStateException | NoClassDefFoundError exception) {
            logger.info("LuckPerms is not installed; rank prefixes stay empty.");
            return new RankProvider(null);
        }
    }

    public boolean isAvailable() {
        return this.luckPerms != null;
    }

    /** The player's rank prefix, or an empty string when there is none. */
    public String prefixOf(Player player) {
        Objects.requireNonNull(player, "player");

        return this.metaOf(player)
                .map(CachedMetaData::getPrefix)
                .filter(prefix -> !prefix.isBlank())
                .orElse("");
    }

    /** The player's primary group, or an empty string when LuckPerms is absent. */
    public String groupOf(Player player) {
        Objects.requireNonNull(player, "player");

        return this.metaOf(player)
                .map(CachedMetaData::getPrimaryGroup)
                .filter(group -> !group.isBlank())
                .orElse("");
    }

    private Optional<CachedMetaData> metaOf(Player player) {
        if (this.luckPerms == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(this.luckPerms.getUserManager().getUser(player.getUniqueId()))
                .map(user -> user.getCachedData().getMetaData());
    }
}
