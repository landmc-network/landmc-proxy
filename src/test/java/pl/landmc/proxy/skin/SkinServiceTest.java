package pl.landmc.proxy.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.helpers.NOPLogger;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Covers the two things the skin feature decides on its own: which names are worth a lookup,
 * and how long a player waits between them. Both exist to keep the network from making
 * needless requests to Mojang on a player's behalf.
 */
class SkinServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"Crispi", "abc", "a_1", "SixteenCharsLong"})
    @DisplayName("names that could belong to a premium account are accepted")
    void acceptsPlausibleNames(String skinName) {
        assertTrue(SkinService.isValidSkinName(skinName));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ab",
        "SeventeenCharsXXX",
        "has space",
        "kropka.",
        "ukośnik/",
        "<red>",
        ""
    })
    @DisplayName("anything Minecraft could not have as a username is refused before the lookup")
    void refusesImpossibleNames(String skinName) {
        assertFalse(SkinService.isValidSkinName(skinName));
    }

    @Test
    @DisplayName("a null name is refused rather than reaching the lookup")
    void refusesANullName() {
        assertFalse(SkinService.isValidSkinName(null));
    }

    @Test
    @DisplayName("the cooldown counts down and then lets the player through")
    void countsTheCooldownDown() {
        SkinService service = service(30);
        UUID playerId = UUID.randomUUID();
        long now = 1_000_000L;

        assertEquals(0L, service.remainingCooldownSeconds(playerId, now));

        service.setCooldown(playerId, now, 30);

        assertEquals(30L, service.remainingCooldownSeconds(playerId, now));
        // Part of a second still counts as a second, so "wait 0s" is never shown.
        assertEquals(1L, service.remainingCooldownSeconds(playerId, now + 29_500L));
        assertEquals(0L, service.remainingCooldownSeconds(playerId, now + 30_000L));
    }

    @Test
    @DisplayName("a player who leaves is forgotten, so the map cannot grow forever")
    void forgetsAPlayerOnDisconnect() {
        SkinService service = service(30);
        UUID playerId = UUID.randomUUID();
        long now = 1_000_000L;

        service.setCooldown(playerId, now, 30);
        assertTrue(service.remainingCooldownSeconds(playerId, now) > 0L);

        service.onDisconnect(playerId);

        assertEquals(0L, service.remainingCooldownSeconds(playerId, now));
        assertTrue(service.hasNoCooldowns());
    }

    private static SkinService service(int cooldownSeconds) {
        ProxyConfig config = new ProxyConfig();
        config.skin.successCooldownSeconds = cooldownSeconds;

        return new SkinService(
                (ProxyServer) Proxy.newProxyInstance(
                        SkinServiceTest.class.getClassLoader(),
                        new Class<?>[] {ProxyServer.class},
                        (proxy, method, args) -> null),
                new Object(),
                config,
                NOPLogger.NOP_LOGGER);
    }
}
