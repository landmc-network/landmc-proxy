package pl.landmc.proxy.live;

/** What a platform said about a channel. */
public enum StreamStatus {

    LIVE,

    OFFLINE,

    /**
     * The platform could not be asked.
     *
     * <p>Not configured, unreachable, rate-limiting, or answering something unexpected. Kept
     * apart from {@link #OFFLINE} because the two deserve different answers: "you are not
     * streaming" is a correction, and "we could not check" is an apology. Merging them tells a
     * streamer they are offline while they are visibly on air, which is the single most
     * annoying way this feature can fail.
     */
    UNKNOWN
}
