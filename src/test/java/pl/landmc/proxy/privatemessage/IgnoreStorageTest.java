package pl.landmc.proxy.privatemessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.landmc.platform.config.ConfigService;

/**
 * The ignore list is the only part of private messaging that survives a restart, so the round
 * trip through YAML is what needs pinning - a UUID key that does not deserialise would silently
 * drop everyone's ignores on the next start.
 */
class IgnoreStorageTest {

    @Test
    void survivesARestart(@TempDir Path directory) throws IOException {
        UUID player = UUID.randomUUID();
        UUID ignored = UUID.randomUUID();

        ConfigService configs = new ConfigService();
        IgnoreStorage storage = configs.load(directory, "ignores.yml", IgnoreStorage.class);
        storage.ignoredBy(player).add(ignored);
        configs.save(storage);

        IgnoreStorage reloaded = new ConfigService().load(directory, "ignores.yml", IgnoreStorage.class);

        assertEquals(Set.of(ignored), reloaded.ignoredPlayers.get(player));
        assertTrue(Files.readString(directory.resolve("ignores.yml")).contains("ignored-players:"));
    }

    @Test
    void startsEmpty(@TempDir Path directory) {
        IgnoreStorage storage = new ConfigService().load(directory, "ignores.yml", IgnoreStorage.class);

        assertTrue(storage.ignoredPlayers.isEmpty());
        assertTrue(storage.ignoredBy(UUID.randomUUID()).isEmpty());
    }

    @Test
    void removingTheLastIgnoreLeavesAnEmptySet(@TempDir Path directory) {
        UUID player = UUID.randomUUID();
        UUID ignored = UUID.randomUUID();

        ConfigService configs = new ConfigService();
        IgnoreStorage storage = configs.load(directory, "ignores.yml", IgnoreStorage.class);
        storage.ignoredBy(player).add(ignored);
        storage.ignoredBy(player).remove(ignored);
        configs.save(storage);

        IgnoreStorage reloaded = new ConfigService().load(directory, "ignores.yml", IgnoreStorage.class);

        assertFalse(reloaded.ignoredPlayers.getOrDefault(player, Set.of()).contains(ignored));
    }
}
