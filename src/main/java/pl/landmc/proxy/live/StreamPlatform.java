package pl.landmc.proxy.live;

import java.util.Locale;
import java.util.Optional;

/**
 * A place somebody streams from.
 *
 * <p>A closed set, and that is the point. The link on a profile ends up in a clickable message
 * sent to everybody on the network, so "any URL" is an advertising slot rather than a feature.
 * Adding a platform here is a deliberate act; nothing parses its way in.
 */
public enum StreamPlatform {

    /** Verifiable: Twitch says whether a channel is live right now. */
    TWITCH("Twitch", true),

    /** Verifiable, the same way. */
    KICK("Kick", true),

    /**
     * Not verifiable.
     *
     * <p>TikTok has no endpoint that answers "is this account live" without an authenticated
     * session, so a TikTok announcement is taken on the streamer's word. That difference is
     * carried in the type rather than in a comment, because it decides what the command is
     * allowed to do: a platform that cannot be checked is announced only by somebody trusted
     * enough to be given the permission for it.
     */
    TIKTOK("TikTok", false);

    private final String displayName;
    private final boolean verifiable;

    StreamPlatform(String displayName, boolean verifiable) {
        this.displayName = displayName;
        this.verifiable = verifiable;
    }

    public String displayName() {
        return this.displayName;
    }

    /** Whether this platform can be asked, before broadcasting, if the stream is really on. */
    public boolean isVerifiable() {
        return this.verifiable;
    }

    public static Optional<StreamPlatform> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }

        for (StreamPlatform platform : values()) {
            if (platform.name().equalsIgnoreCase(name.trim())) {
                return Optional.of(platform);
            }
        }
        return Optional.empty();
    }

    /** The lower-case form used in configuration keys. */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
