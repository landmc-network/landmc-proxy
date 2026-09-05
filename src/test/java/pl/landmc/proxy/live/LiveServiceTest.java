package pl.landmc.proxy.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import pl.landmc.platform.database.DatabaseService;
import pl.landmc.platform.database.DatabaseType;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * The three gates in front of an announcement, against a real embedded database.
 *
 * <p>The one worth the most here is the cooldown, because it is the gate the previous
 * implementation did not have: everything else limits who may announce, and only this limits how
 * often, which is the difference between a feature and a way to spam everybody online.
 */
class LiveServiceTest {

    private DatabaseService database;
    private ProxyConfig config;
    private LiveService live;
    private FakeStatusClient twitch;

    @BeforeEach
    void openDatabase(@TempDir Path directory) {
        this.config = new ProxyConfig();
        this.config.database.type = DatabaseType.H2;
        this.config.database.fileName = "live-test";
        this.config.database.poolSize = 2;
        this.config.live.cooldownMinutes = 30;

        this.database = new DatabaseService(
                "live-test", this.config.database, directory, NOPLogger.NOP_LOGGER);
        this.database.enable();

        this.twitch = new FakeStatusClient(StreamPlatform.TWITCH);
        this.live = new LiveService(
                new LiveRepository(this.database), this.config, List.of(this.twitch));
        this.live.createTables();
    }

    @AfterEach
    void closeDatabase() {
        if (this.database != null) {
            this.database.close();
        }
    }

    @Test
    @DisplayName("a registered profile comes back, whatever case the name is asked in")
    void storesAProfile() throws Exception {
        await(this.live.register("Crispi", StreamProfile.twitch("crispi"), "Admin"));

        StreamProfile stored = await(this.live.profile("CRISPI")).orElseThrow();
        assertEquals(StreamPlatform.TWITCH, stored.platform());
        assertEquals("crispi", stored.identifier());
        assertEquals("https://www.twitch.tv/crispi", stored.url());
    }

    @Test
    @DisplayName("registering again replaces the profile rather than adding a second")
    void replacesAProfile() throws Exception {
        await(this.live.register("Crispi", StreamProfile.twitch("crispi"), "Admin"));
        await(this.live.register("Crispi", StreamProfile.kick("crispi-kick"), "Admin"));

        assertEquals(StreamPlatform.KICK, await(this.live.profile("Crispi")).orElseThrow().platform());
        assertEquals(1, await(this.live.list()).size());
    }

    @Test
    @DisplayName("removing says whether there was anything to remove")
    void removesAProfile() throws Exception {
        await(this.live.register("Crispi", StreamProfile.twitch("crispi"), "Admin"));

        assertTrue(await(this.live.unregister("Crispi")));
        assertFalse(await(this.live.unregister("Crispi")));
        assertTrue(await(this.live.profile("Crispi")).isEmpty());
    }

    @Test
    @DisplayName("the listing is by name and carries the profile back")
    void listsProfiles() throws Exception {
        await(this.live.register("Zenon", StreamProfile.twitch("zenon"), "Admin"));
        await(this.live.register("Anna", StreamProfile.kick("anna"), "Admin"));

        List<LiveRepository.Entry> entries = await(this.live.list());

        assertEquals(2, entries.size());
        assertEquals("Anna", entries.get(0).playerName());
        assertEquals(StreamPlatform.KICK, entries.get(0).profile().platform());
        assertEquals("Zenon", entries.get(1).playerName());
    }

    @Test
    @DisplayName("the cooldown counts down and is forgotten when the player leaves")
    void appliesACooldown() {
        UUID playerId = UUID.randomUUID();

        assertEquals(0L, this.live.remainingCooldownSeconds(playerId));

        this.live.startCooldown(playerId);
        long remaining = this.live.remainingCooldownSeconds(playerId);
        assertTrue(remaining > 0, "no cooldown after announcing");
        assertTrue(remaining <= 30 * 60, "the cooldown outlasts what was configured");

        this.live.onDisconnect(playerId);
        assertEquals(0L, this.live.remainingCooldownSeconds(playerId));
    }

    @Test
    @DisplayName("a cooldown of zero minutes lets somebody announce again immediately")
    void honoursADisabledCooldown() {
        this.config.live.cooldownMinutes = 0;
        UUID playerId = UUID.randomUUID();

        this.live.startCooldown(playerId);

        assertEquals(0L, this.live.remainingCooldownSeconds(playerId));
    }

    @Test
    @DisplayName("the platform's answer is passed through as it came")
    void reportsWhatThePlatformSaid() throws Exception {
        this.twitch.configured = true;

        this.twitch.answer = StreamStatus.LIVE;
        assertEquals(StreamStatus.LIVE, await(this.live.status(StreamProfile.twitch("crispi"))));

        this.twitch.answer = StreamStatus.OFFLINE;
        assertEquals(StreamStatus.OFFLINE, await(this.live.status(StreamProfile.twitch("crispi"))));
    }

    @Test
    @DisplayName("a platform with no credentials is unknown, never offline")
    void doesNotClaimOfflineWhenItCannotAsk() throws Exception {
        this.twitch.configured = false;
        this.twitch.answer = StreamStatus.LIVE;

        // Telling a streamer they are offline because the server has no API key would send them
        // to check their own stream, which is working.
        assertEquals(StreamStatus.UNKNOWN, await(this.live.status(StreamProfile.twitch("crispi"))));
        assertFalse(this.live.canVerify(StreamPlatform.TWITCH));
    }

    @Test
    @DisplayName("a platform nothing can check is unknown rather than assumed live")
    void doesNotGuessForAnUncheckablePlatform() throws Exception {
        assertEquals(StreamStatus.UNKNOWN, await(this.live.status(StreamProfile.tikTok("crispi"))));
        assertFalse(this.live.canVerify(StreamPlatform.TIKTOK));
        assertFalse(this.live.canVerify(StreamPlatform.KICK), "there is no Kick client in this test");
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(20, TimeUnit.SECONDS);
    }

    /** A platform that answers whatever the test says, without leaving the machine. */
    private static final class FakeStatusClient implements StreamStatusClient {

        private final StreamPlatform platform;
        private boolean configured = true;
        private StreamStatus answer = StreamStatus.OFFLINE;

        private FakeStatusClient(StreamPlatform platform) {
            this.platform = platform;
        }

        @Override
        public StreamPlatform platform() {
            return this.platform;
        }

        @Override
        public boolean isConfigured() {
            return this.configured;
        }

        @Override
        public CompletableFuture<StreamStatus> check(String identifier) {
            return CompletableFuture.completedFuture(this.answer);
        }
    }
}
