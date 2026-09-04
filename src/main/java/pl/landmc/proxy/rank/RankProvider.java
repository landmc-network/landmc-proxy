package pl.landmc.proxy.rank;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Everything the proxy asks about ranks.
 *
 * <p>The seam that keeps LuckPerms optional, in the same shape as the one around PacketEvents.
 * No class that mentions a LuckPerms type is named here: {@link #UNAVAILABLE} answers when
 * LuckPerms is absent, and {@link LuckPermsRankProvider} is only loaded once it is known to be
 * present. That distinction is not academic - a plugin class is verified when it is first
 * loaded, and verifying one that refers to a missing library throws {@code NoClassDefFoundError}
 * before any {@code try} block inside its methods can catch it, which is exactly how the proxy
 * failed to start on a server without LuckPerms.
 *
 * <p>The original went further wrong in the same direction: it called
 * {@code LuckPermsProvider.get()} in a command constructor, so the whole plugin failed to load.
 */
public interface RankProvider {

    /** Answers as though every player were unranked; installed when LuckPerms is absent. */
    RankProvider UNAVAILABLE = new RankProvider() {

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String prefixOf(Player player) {
            return "";
        }

        @Override
        public String groupOf(Player player) {
            return "";
        }

        @Override
        public CompletableFuture<RankAssignment> assign(
                ProxyServer proxy, String targetName, String groupName, @Nullable Duration duration) {

            return CompletableFuture.completedFuture(RankAssignment.unavailable());
        }
    };

    /**
     * Binds to LuckPerms when it is installed, otherwise returns {@link #UNAVAILABLE}.
     *
     * <p>Never throws: a missing optional integration must not stop the proxy.
     */
    static RankProvider create(Logger logger) {
        Objects.requireNonNull(logger, "logger");

        try {
            RankProvider provider = LuckPermsRankProvider.bind();
            logger.info("LuckPerms found; rank prefixes and /setrank are available.");
            return provider;
        }
        catch (IllegalStateException | NoClassDefFoundError exception) {
            logger.info("LuckPerms is not installed; rank prefixes stay empty and /setrank is not registered.");
            return UNAVAILABLE;
        }
    }

    boolean isAvailable();

    /** The player's rank prefix, or an empty string when there is none. */
    String prefixOf(Player player);

    /** The player's primary group, or an empty string when LuckPerms is absent. */
    String groupOf(Player player);

    /**
     * Puts a player in a group, replacing whatever groups they inherited before.
     *
     * @param duration how long the group lasts, or null for permanently
     * @return what happened; only a genuine storage failure completes exceptionally, an
     *     ordinary "no such group" is an outcome
     */
    CompletableFuture<RankAssignment> assign(
            ProxyServer proxy, String targetName, String groupName, @Nullable Duration duration);

    /** How {@link #assign} ended, and the names needed to say so to the sender. */
    record RankAssignment(Outcome outcome, String group, String player) {

        public enum Outcome {
            /** LuckPerms is not installed, so ranks cannot be changed from here. */
            UNAVAILABLE,
            NO_SUCH_GROUP,
            NO_SUCH_PLAYER,
            ASSIGNED
        }

        static RankAssignment unavailable() {
            return new RankAssignment(Outcome.UNAVAILABLE, "", "");
        }

        static RankAssignment groupNotFound() {
            return new RankAssignment(Outcome.NO_SUCH_GROUP, "", "");
        }

        static RankAssignment playerNotFound() {
            return new RankAssignment(Outcome.NO_SUCH_PLAYER, "", "");
        }

        static RankAssignment assigned(String group, String player) {
            return new RankAssignment(Outcome.ASSIGNED, group, player);
        }
    }
}
