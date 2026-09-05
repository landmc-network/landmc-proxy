package pl.landmc.proxy.live;

import com.j256.ormlite.dao.Dao;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import pl.landmc.platform.database.DatabaseService;

/**
 * Reads and writes registered streamers. Every method goes to the database, so every method is
 * asynchronous - the callers are commands running on a Netty thread.
 */
public final class LiveRepository {

    private final DatabaseService database;

    public LiveRepository(DatabaseService database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public void createTables() {
        this.database.createTables(LiveProfileEntity.class);
    }

    public CompletableFuture<Optional<StreamProfile>> find(String playerName) {
        String key = key(playerName);

        return this.database.supplyAsync(() -> Optional.ofNullable(this.dao().queryForId(key))
                .flatMap(LiveRepository::toProfile));
    }

    /** Adds or replaces somebody's profile. */
    public CompletableFuture<Void> save(
            String playerName, StreamProfile profile, String addedBy) {

        LiveProfileEntity entity = new LiveProfileEntity();
        entity.name = key(playerName);
        entity.displayName = playerName;
        entity.platform = profile.platform().name();
        entity.identifier = profile.identifier();
        entity.addedBy = addedBy;
        entity.addedAt = Instant.now().toEpochMilli();

        return this.database.runAsync(() -> this.dao().createOrUpdate(entity));
    }

    /** @return false when there was nothing to remove */
    public CompletableFuture<Boolean> remove(String playerName) {
        String key = key(playerName);
        return this.database.supplyAsync(() -> this.dao().deleteById(key) > 0);
    }

    /** Everybody registered, by name. Read by an administrative command, so the whole table. */
    public CompletableFuture<List<Entry>> list() {
        return this.database.supplyAsync(() -> {
            List<Entry> entries = new ArrayList<>();
            for (LiveProfileEntity entity : this.dao().queryBuilder().orderBy("name", true).query()) {
                toProfile(entity).ifPresent(
                        profile -> entries.add(new Entry(entity.displayName, profile)));
            }
            return entries;
        });
    }

    /**
     * Rebuilds a profile from a row.
     *
     * <p>Empty when the stored platform is one this build does not know - a row written by a
     * newer version, or edited by hand. Skipping it keeps one bad row from failing the whole
     * listing.
     */
    private static Optional<StreamProfile> toProfile(LiveProfileEntity entity) {
        return StreamPlatform.byName(entity.platform)
                .map(platform -> StreamProfile.of(platform, entity.identifier));
    }

    private static String key(String playerName) {
        return playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
    }

    private Dao<LiveProfileEntity, String> dao() {
        return this.database.dao(LiveProfileEntity.class);
    }

    /** A registered streamer, as a command shows them. */
    public record Entry(String playerName, StreamProfile profile) {
    }
}
