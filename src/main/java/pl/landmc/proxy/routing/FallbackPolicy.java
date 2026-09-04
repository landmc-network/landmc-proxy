package pl.landmc.proxy.routing;

import java.util.Locale;

/**
 * Decides whether a player kicked from a backend should be moved to the fallback rather than
 * disconnected.
 *
 * <p>Not every kick is a server going away. A ban, a whitelist rejection or a moderation kick
 * has to reach the player as a disconnect screen; silently dropping them into the lobby would
 * hide it. Only a backend that is restarting or shutting down is worth catching, and the reason
 * text is the only signal Velocity gives us for telling those apart.
 *
 * <p>Matching on message text is admittedly fragile - it is why the list covers both English and
 * Polish phrasings, and why the default is to disconnect when the reason is unrecognised. A
 * missed redirect is a player who has to reconnect; a wrong one hides a ban.
 *
 * <p>Pure and static so it can be tested without a proxy: this is a decision, not an action.
 */
public final class FallbackPolicy {

    private FallbackPolicy() {
    }

    /**
     * @param enabled whether fallback redirection is switched on at all
     * @param kickedDuringConnect a kick while connecting means the backend never accepted the
     *     player, and bouncing them onward would loop
     * @param sourceServer the backend that kicked them
     * @param fallbackServer the configured fallback
     * @param reason the kick message, may be null
     */
    public static boolean shouldRedirect(
            boolean enabled,
            boolean kickedDuringConnect,
            String sourceServer,
            String fallbackServer,
            String reason) {

        if (!enabled || kickedDuringConnect || fallbackServer == null || fallbackServer.isBlank()) {
            return false;
        }

        // Sending them back to the server that just kicked them is a loop.
        if (sourceServer != null && sourceServer.equalsIgnoreCase(fallbackServer)) {
            return false;
        }

        // No reason at all is what a backend that died mid-session looks like.
        if (reason == null || reason.isBlank()) {
            return true;
        }

        String normalized = reason.toLowerCase(Locale.ROOT);
        for (String phrase : RESTART_PHRASES) {
            if (normalized.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    /** Phrases a restarting or closing backend puts in its kick message. */
    private static final String[] RESTART_PHRASES = {
        "restart",
        "server closed",
        "server is closed",
        "server shutting down",
        "server is shutting down",
        "serwer zamkniety",
        "serwer zamknięty",
        "serwer jest zamkniety",
        "serwer jest zamknięty",
        "serwer zostal zamkniety",
        "serwer został zamknięty",
        "serwer jest wylaczany",
        "serwer jest wyłączany",
    };
}
