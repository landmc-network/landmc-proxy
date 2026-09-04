package pl.landmc.proxy.cooldown;

import java.util.Locale;

/**
 * Renders a remaining cooldown for a player to read.
 *
 * <p>Shared by the command and the GUI cooldown so the two cannot drift apart: being told
 * "0.75s" in one place and "750ms" in another for the same wait reads like two different
 * features.
 */
public final class CooldownTime {

    private CooldownTime() {
    }

    /** {@code 1.25s} above a second, {@code 750ms} below it, and never {@code 0ms}. */
    public static String format(long remainingMillis) {
        if (remainingMillis >= 1_000L) {
            return String.format(Locale.ROOT, "%.2fs", remainingMillis / 1_000.0D);
        }
        return Math.max(1L, remainingMillis) + "ms";
    }
}
