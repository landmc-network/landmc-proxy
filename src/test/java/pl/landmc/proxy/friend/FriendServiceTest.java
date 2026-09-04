package pl.landmc.proxy.friend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import pl.landmc.platform.database.DatabaseConfig;
import pl.landmc.platform.database.DatabaseService;
import pl.landmc.platform.database.DatabaseType;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.friend.FriendRepository.AcceptOutcome;
import pl.landmc.proxy.friend.FriendService.RequestResult;
import pl.landmc.proxy.vanish.VanishProvider;

/**
 * Covers the decisions the service makes on top of the repository, against a real database.
 *
 * <p>The proxy itself is stubbed down to the two things the service asks it: who is online, and
 * what their name is. Everything else - the ordering of checks, what happens when two players
 * invite each other, whether an offline player can be invited at all - is the behaviour under
 * test.
 */
class FriendServiceTest {

    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOREK = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private final Map<String, Player> online = new HashMap<>();

    private DatabaseService database;
    private FriendRepository repository;
    private FriendService service;
    private ProxyConfig config;

    @BeforeEach
    void openDatabase(@TempDir Path directory) {
        this.config = new ProxyConfig();
        this.config.friends.enabled = true;
        this.config.friends.maxFriends = 3;
        this.config.vanish.enabled = false;

        DatabaseConfig databaseConfig = this.config.database;
        databaseConfig.type = DatabaseType.H2;
        databaseConfig.fileName = "friends-service-test";
        databaseConfig.poolSize = 2;

        this.database = new DatabaseService(
                "friend-service-test", databaseConfig, directory, NOPLogger.NOP_LOGGER);
        this.database.enable();

        this.repository = new FriendRepository(this.database);
        ProxyServer proxy = this.stubProxy();

        this.service = new FriendService(
                proxy,
                this.repository,
                this.database,
                this.config,
                VanishProvider.create(proxy, this.config, NOPLogger.NOP_LOGGER),
                NOPLogger.NOP_LOGGER);
        this.service.start();
    }

    @AfterEach
    void closeDatabase() {
        if (this.database != null) {
            this.database.close();
        }
    }

    @Test
    @DisplayName("an invitation reaches a player who has never been online, by stored name")
    void invitesAnOfflinePlayer() throws Exception {
        this.repository.touchProfile(BOREK, "Borek", 1_000L);

        FriendService.RequestOutcome outcome = await(this.service.invite(player(ANNA, "Anna"), "borek"));

        assertEquals(RequestResult.SENT, outcome.result());
        assertEquals(BOREK, outcome.target().playerId());
        assertTrue(this.repository.hasRequest(ANNA, BOREK));
    }

    @Test
    @DisplayName("a player nobody has ever seen cannot be invited")
    void refusesAnUnknownName() throws Exception {
        assertEquals(
                RequestResult.UNKNOWN_PLAYER,
                await(this.service.invite(player(ANNA, "Anna"), "NigdyTuNieByl")).result());
    }

    @Test
    @DisplayName("inviting somebody who already invited you makes you friends instead")
    void acceptsAMutualInvitation() throws Exception {
        this.repository.touchProfile(ANNA, "Anna", 1_000L);
        this.repository.touchProfile(BOREK, "Borek", 1_000L);
        this.repository.createRequest(BOREK, ANNA, 1_000L);

        FriendService.RequestOutcome outcome = await(this.service.invite(player(ANNA, "Anna"), "Borek"));

        assertEquals(RequestResult.ACCEPTED_INSTEAD, outcome.result());
        assertTrue(this.repository.areFriends(ANNA, BOREK));
        assertFalse(this.repository.hasRequest(BOREK, ANNA));
    }

    @Test
    @DisplayName("inviting yourself is refused before anything is written")
    void refusesToInviteYourself() throws Exception {
        this.repository.touchProfile(ANNA, "Anna", 1_000L);

        assertEquals(RequestResult.SELF, await(this.service.invite(player(ANNA, "Anna"), "Anna")).result());
        assertEquals(0L, this.repository.countFriends(ANNA));
    }

    @Test
    @DisplayName("an already-full list refuses the invitation rather than the acceptance")
    void refusesToInviteWhenTheListIsFull() throws Exception {
        this.config.friends.maxFriends = 1;
        this.repository.touchProfile(BOREK, "Borek", 1_000L);
        UUID other = UUID.randomUUID();
        this.repository.touchProfile(other, "Trzeci", 1_000L);
        this.repository.createRequest(ANNA, other, 1_000L);
        this.repository.acceptRequest(ANNA, other, 1, 2_000L);

        assertEquals(
                RequestResult.LIST_FULL,
                await(this.service.invite(player(ANNA, "Anna"), "Borek")).result());
        assertFalse(this.repository.hasRequest(ANNA, BOREK));
    }

    @Test
    @DisplayName("an online player is resolved by their current name, not a stale stored one")
    void prefersTheOnlineName() throws Exception {
        // Stored under an old name; the player is connected under a new one.
        this.repository.touchProfile(BOREK, "StaryNick", 1_000L);
        this.online.put("nowynick", player(BOREK, "NowyNick"));

        FriendService.RequestOutcome outcome = await(this.service.invite(player(ANNA, "Anna"), "NowyNick"));

        assertEquals(RequestResult.SENT, outcome.result());
        assertEquals("NowyNick", outcome.target().name());
    }

    @Test
    @DisplayName("accepting an invitation that was never sent says so instead of failing")
    void reportsAMissingInvitation() throws Exception {
        this.repository.touchProfile(BOREK, "Borek", 1_000L);

        assertEquals(
                AcceptOutcome.NO_REQUEST,
                await(this.service.accept(player(ANNA, "Anna"), "Borek")).outcome());
    }

    @Test
    @DisplayName("declining and removing report whether there was anything to act on")
    void reportsWhetherThereWasAnythingToRemove() throws Exception {
        this.repository.touchProfile(BOREK, "Borek", 1_000L);
        this.repository.createRequest(BOREK, ANNA, 1_000L);

        assertTrue(await(this.service.decline(player(ANNA, "Anna"), "Borek")).isPresent());
        assertFalse(await(this.service.decline(player(ANNA, "Anna"), "Borek")).isPresent());
        assertFalse(await(this.service.remove(player(ANNA, "Anna"), "Borek")).isPresent());
    }

    @Test
    @DisplayName("expired invitations are gone by the time the service is up")
    void sweepsExpiredInvitationsOnStart() throws Exception {
        this.repository.createRequest(BOREK, ANNA, 1_000L);
        this.config.friends.requestExpiryDays = 1;

        this.service.start();

        assertFalse(this.repository.hasRequest(BOREK, ANNA));
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    private Player player(UUID playerId, String name) {
        return (Player) Proxy.newProxyInstance(
                FriendServiceTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "getUsername" -> name;
                    case "isActive" -> true;
                    case "getCurrentServer" -> Optional.empty();
                    case "toString" -> name;
                    default -> null;
                });
    }

    /** A proxy that knows only the players this test put online. */
    private ProxyServer stubProxy() {
        return (ProxyServer) Proxy.newProxyInstance(
                FriendServiceTest.class.getClassLoader(),
                new Class<?>[] {ProxyServer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPlayer") && args != null && args.length == 1) {
                        if (args[0] instanceof String name) {
                            return Optional.ofNullable(
                                    this.online.get(name.toLowerCase(java.util.Locale.ROOT)));
                        }
                        return this.online.values().stream()
                                .filter(player -> player.getUniqueId().equals(args[0]))
                                .findFirst();
                    }
                    return method.getReturnType() == Optional.class ? Optional.empty() : null;
                });
    }
}
