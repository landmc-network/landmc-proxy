package pl.landmc.proxy.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Ported from skytop-velocity-commons, where this decision already had a test worth keeping.
 *
 * <p>The cases that matter are the negative ones: a ban must not be turned into a quiet trip to
 * the lobby.
 */
class FallbackPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "Server closed",
        "SERVER IS SHUTTING DOWN",
        "Restart za 5 sekund",
        "Serwer zamknięty",
        "Serwer jest wyłączany",
    })
    void redirectsWhenTheBackendIsGoingAway(String reason) {
        assertTrue(FallbackPolicy.shouldRedirect(true, false, "skyblock-1", "lobby-1", reason));
    }

    @Test
    void redirectsWhenTheBackendGaveNoReasonAtAll() {
        assertTrue(FallbackPolicy.shouldRedirect(true, false, "skyblock-1", "lobby-1", null));
        assertTrue(FallbackPolicy.shouldRedirect(true, false, "skyblock-1", "lobby-1", "  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Zostałeś zbanowany na zawsze",
        "You are not whitelisted on this server",
        "Wyrzucony przez administratora",
        "AFK",
    })
    void keepsTheDisconnectScreenForADecisionAboutThePlayer(String reason) {
        assertFalse(
                FallbackPolicy.shouldRedirect(true, false, "skyblock-1", "lobby-1", reason),
                "a kick aimed at the player must reach them, not be swallowed by a redirect");
    }

    @Test
    void doesNothingWhenDisabled() {
        assertFalse(FallbackPolicy.shouldRedirect(false, false, "skyblock-1", "lobby-1", "restart"));
    }

    @Test
    void doesNotRedirectAKickWhileConnecting() {
        // The backend never accepted the player; bouncing them onward would loop.
        assertFalse(FallbackPolicy.shouldRedirect(true, true, "skyblock-1", "lobby-1", "restart"));
    }

    @Test
    void doesNotRedirectBackToTheServerThatKicked() {
        assertFalse(FallbackPolicy.shouldRedirect(true, false, "lobby-1", "lobby-1", "restart"));
        assertFalse(FallbackPolicy.shouldRedirect(true, false, "LOBBY-1", "lobby-1", "restart"));
    }

    @Test
    void doesNothingWithoutAConfiguredFallback() {
        assertFalse(FallbackPolicy.shouldRedirect(true, false, "skyblock-1", null, "restart"));
        assertFalse(FallbackPolicy.shouldRedirect(true, false, "skyblock-1", "", "restart"));
    }
}
