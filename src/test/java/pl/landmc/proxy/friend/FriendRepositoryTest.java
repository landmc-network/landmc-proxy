package pl.landmc.proxy.friend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import pl.landmc.platform.database.DatabaseConfig;
import pl.landmc.platform.database.DatabaseService;
import pl.landmc.platform.database.DatabaseType;
import pl.landmc.proxy.friend.FriendRepository.AcceptOutcome;
import pl.landmc.proxy.friend.FriendRepository.FriendProfile;

/**
 * Runs against a real embedded database rather than a stand-in.
 *
 * <p>The things worth testing here are what the database enforces - that a friendship is one row
 * whichever way round it is written, that accepting is atomic, that a limit holds - and a fake
 * repository would only test the fake.
 */
class FriendRepositoryTest {

    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOREK = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID CELINA = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private DatabaseService database;
    private FriendRepository repository;

    @BeforeEach
    void openDatabase(@TempDir Path directory) {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseType.H2;
        config.fileName = "friends-test";
        config.poolSize = 2;

        this.database = new DatabaseService("friend-test", config, directory, NOPLogger.NOP_LOGGER);
        this.database.enable();

        this.repository = new FriendRepository(this.database);
        this.repository.createTables();
    }

    @AfterEach
    void closeDatabase() {
        if (this.database != null) {
            this.database.close();
        }
    }

    @Test
    @DisplayName("a friendship is one row, whichever way round it was created")
    void storesAFriendshipOnce() throws SQLException {
        this.repository.createRequest(ANNA, BOREK, 1_000L);

        assertEquals(AcceptOutcome.ACCEPTED, this.repository.acceptRequest(ANNA, BOREK, 10, 2_000L));

        assertTrue(this.repository.areFriends(ANNA, BOREK));
        assertTrue(this.repository.areFriends(BOREK, ANNA), "the friendship is not symmetric");
        assertEquals(1L, this.repository.countFriends(ANNA));
        assertEquals(1L, this.repository.countFriends(BOREK));
    }

    @Test
    @DisplayName("both sides see the other in their list")
    void listsTheFriendFromEitherSide() throws SQLException {
        this.repository.touchProfile(ANNA, "Anna", 1_000L);
        this.repository.touchProfile(BOREK, "Borek", 1_000L);
        this.repository.createRequest(ANNA, BOREK, 1_000L);
        this.repository.acceptRequest(ANNA, BOREK, 10, 2_000L);

        assertEquals(List.of("Borek"), this.repository.listFriends(ANNA).stream()
                .map(FriendProfile::displayName)
                .toList());
        assertEquals(List.of("Anna"), this.repository.listFriends(BOREK).stream()
                .map(FriendProfile::displayName)
                .toList());
    }

    @Test
    @DisplayName("a friend nobody has ever recorded is still listed, by id")
    void fallsBackToTheIdForAnUnknownName() throws SQLException {
        this.repository.createRequest(ANNA, BOREK, 1_000L);
        this.repository.acceptRequest(ANNA, BOREK, 10, 2_000L);

        assertEquals(BOREK.toString(), this.repository.listFriends(ANNA).getFirst().displayName());
    }

    @Test
    @DisplayName("accepting an invitation that is not there changes nothing")
    void refusesToAcceptAnInvitationThatDoesNotExist() throws SQLException {
        assertEquals(AcceptOutcome.NO_REQUEST, this.repository.acceptRequest(ANNA, BOREK, 10, 1_000L));
        assertFalse(this.repository.areFriends(ANNA, BOREK));
    }

    @Test
    @DisplayName("the same invitation cannot be sent twice")
    void refusesADuplicateInvitation() throws SQLException {
        assertTrue(this.repository.createRequest(ANNA, BOREK, 1_000L));
        assertFalse(this.repository.createRequest(ANNA, BOREK, 2_000L));
    }

    @Test
    @DisplayName("a stale invitation between players who are already friends is dropped, not doubled")
    void doesNotCreateASecondFriendship() throws SQLException {
        this.repository.createRequest(ANNA, BOREK, 1_000L);
        this.repository.acceptRequest(ANNA, BOREK, 10, 2_000L);
        this.repository.createRequest(ANNA, BOREK, 3_000L);

        assertEquals(AcceptOutcome.ALREADY_FRIENDS, this.repository.acceptRequest(ANNA, BOREK, 10, 4_000L));
        assertEquals(1L, this.repository.countFriends(ANNA));
        assertFalse(this.repository.hasRequest(ANNA, BOREK), "the stale invitation was left behind");
    }

    @Test
    @DisplayName("a full list refuses the friendship instead of growing past the limit")
    void enforcesTheLimit() throws SQLException {
        this.repository.createRequest(ANNA, BOREK, 1_000L);
        this.repository.acceptRequest(ANNA, BOREK, 1, 2_000L);

        this.repository.createRequest(ANNA, CELINA, 3_000L);

        assertEquals(AcceptOutcome.REQUESTER_FULL, this.repository.acceptRequest(ANNA, CELINA, 1, 4_000L));
        assertFalse(this.repository.areFriends(ANNA, CELINA));
        // The invitation survives, so it works once somebody is removed.
        assertTrue(this.repository.hasRequest(ANNA, CELINA));
    }

    @Test
    @DisplayName("a refused acceptance leaves nothing half-written")
    void leavesNothingBehindWhenAcceptanceFails() throws SQLException {
        this.repository.createRequest(ANNA, BOREK, 1_000L);
        this.repository.acceptRequest(ANNA, BOREK, 1, 2_000L);
        this.repository.createRequest(CELINA, ANNA, 3_000L);

        assertEquals(AcceptOutcome.ACCEPTER_FULL, this.repository.acceptRequest(CELINA, ANNA, 1, 4_000L));

        assertTrue(this.repository.hasRequest(CELINA, ANNA), "the invitation was consumed by a failed accept");
        assertEquals(1L, this.repository.countFriends(ANNA));
    }

    @Test
    @DisplayName("removing works from either side and reports whether there was anything to remove")
    void removesFromEitherSide() throws SQLException {
        this.repository.createRequest(ANNA, BOREK, 1_000L);
        this.repository.acceptRequest(ANNA, BOREK, 10, 2_000L);

        assertTrue(this.repository.removeFriend(BOREK, ANNA));
        assertFalse(this.repository.areFriends(ANNA, BOREK));
        assertFalse(this.repository.removeFriend(BOREK, ANNA), "removing twice reported success twice");
        assertFalse(this.repository.removeFriend(ANNA, CELINA), "removing a stranger reported success");
    }

    @Test
    @DisplayName("names are matched however they are capitalised")
    void findsAProfileWhateverTheCase() throws SQLException {
        this.repository.touchProfile(ANNA, "AnnaXD", 1_000L);

        assertEquals(Optional.of(ANNA), this.repository.findProfileByName("annaxd").map(FriendProfile::playerId));
        assertEquals(Optional.of(ANNA), this.repository.findProfileByName("ANNAXD").map(FriendProfile::playerId));
        assertTrue(this.repository.findProfileByName("Nieznany").isEmpty());
    }

    @Test
    @DisplayName("a renamed player is recorded under the new name, not added twice")
    void updatesAProfileOnRename() throws SQLException {
        this.repository.touchProfile(ANNA, "StaryNick", 1_000L);
        this.repository.touchProfile(ANNA, "NowyNick", 2_000L);

        assertEquals(Optional.of(ANNA), this.repository.findProfileByName("NowyNick").map(FriendProfile::playerId));
    }

    @Test
    @DisplayName("invitations older than the limit are swept, newer ones are left alone")
    void sweepsExpiredInvitations() throws SQLException {
        this.repository.createRequest(ANNA, BOREK, 1_000L);
        this.repository.createRequest(CELINA, BOREK, 9_000L);

        assertEquals(1, this.repository.deleteExpiredRequests(5_000L));

        assertFalse(this.repository.hasRequest(ANNA, BOREK));
        assertTrue(this.repository.hasRequest(CELINA, BOREK));
    }

    @Test
    @DisplayName("pending invitations are listed with the names that sent them")
    void listsPendingInvitations() throws SQLException {
        this.repository.touchProfile(ANNA, "Anna", 1_000L);
        this.repository.createRequest(ANNA, BOREK, 1_000L);

        assertEquals(List.of("Anna"), this.repository.listIncomingRequests(BOREK).stream()
                .map(FriendProfile::displayName)
                .toList());
        assertEquals(List.of(), this.repository.listIncomingRequests(ANNA));
    }

    @Test
    @DisplayName("accepting clears the invitation in both directions")
    void clearsTheMirroredInvitation() throws SQLException {
        this.repository.createRequest(ANNA, BOREK, 1_000L);
        this.repository.createRequest(BOREK, ANNA, 1_500L);

        assertEquals(AcceptOutcome.ACCEPTED, this.repository.acceptRequest(ANNA, BOREK, 10, 2_000L));

        assertFalse(this.repository.hasRequest(ANNA, BOREK));
        assertFalse(this.repository.hasRequest(BOREK, ANNA), "the mirrored invitation was left pending");
    }
}
