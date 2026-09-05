package pl.landmc.proxy.live;

import java.util.concurrent.CompletableFuture;

/** Asks a platform whether a channel is on air. */
public interface StreamStatusClient {

    StreamPlatform platform();

    /** Whether this client has what it needs to ask at all - an API key, usually. */
    boolean isConfigured();

    /**
     * Checks one channel.
     *
     * <p>Never fails: a network error, a rate limit or a malformed answer all come back as
     * {@link StreamStatus#UNKNOWN}. A failed future here would only be turned into that by
     * every caller anyway.
     */
    CompletableFuture<StreamStatus> check(String identifier);
}
