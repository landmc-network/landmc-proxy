package pl.landmc.proxy.live;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A client-credentials access token, fetched once and reused until it expires.
 *
 * <p>Twitch and Kick both work this way, so this is written once rather than twice - the
 * previous implementation had the same caching, the same expiry arithmetic and the same
 * single-flight lock copied into two classes, which is two places to fix a token bug in.
 *
 * <p>Two things it does that a naive version would not. It refreshes early, because a token that
 * expires between the check and the request is a failed request for no reason. And a request in
 * flight is shared: ten players running the command at once produce one token request, not ten -
 * which matters because the token endpoint is rate-limited and answering it ten times would be a
 * good way to be locked out of the one that answers the actual question.
 */
public final class OAuthTokenSource {

    /** Renew this long before expiry, so a token is never spent on the request that finds it dead. */
    private static final Duration EARLY_REFRESH = Duration.ofMinutes(5);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final URI endpoint;
    private final String name;
    private final Logger logger;

    private final Object lock = new Object();
    private @Nullable Token cached;
    private @Nullable CompletableFuture<Token> inFlight;

    public OAuthTokenSource(HttpClient http, URI endpoint, String name, Logger logger) {
        this.http = Objects.requireNonNull(http, "http");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.name = Objects.requireNonNull(name, "name");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** A usable token, from the cache or freshly requested. Fails when the platform refuses. */
    public CompletableFuture<String> token(String clientId, String clientSecret) {
        synchronized (this.lock) {
            if (this.cached != null && this.cached.isUsable(clientId)) {
                return CompletableFuture.completedFuture(this.cached.value());
            }
            if (this.inFlight != null) {
                return this.inFlight.thenApply(Token::value);
            }

            CompletableFuture<Token> request = this.request(clientId, clientSecret);
            this.inFlight = request;

            request.whenComplete((token, throwable) -> {
                synchronized (this.lock) {
                    if (throwable == null) {
                        this.cached = token;
                    }
                    if (this.inFlight == request) {
                        this.inFlight = null;
                    }
                }
            });

            return request.thenApply(Token::value);
        }
    }

    /** Throws away the cached token, for when the API says it is no longer good. */
    public void invalidate() {
        synchronized (this.lock) {
            this.cached = null;
        }
    }

    private CompletableFuture<Token> request(String clientId, String clientSecret) {
        String body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&grant_type=client_credentials";

        HttpRequest request = HttpRequest.newBuilder(this.endpoint)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        // Deliberately without the body: a token endpoint's error can echo back
                        // what was sent to it, and what was sent to it is the client secret.
                        throw new IllegalStateException(
                                this.name + " refused the token request with HTTP " + response.statusCode());
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String value = json.get("access_token").getAsString();
                    long seconds = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;

                    this.logger.debug("Got a {} token, good for {}s.", this.name, seconds);
                    return new Token(value, clientId, System.currentTimeMillis() + seconds * 1000L);
                });
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * @param clientId what the token was issued for, so changing the credentials in the
     *     configuration cannot leave a token from the old ones in use
     */
    private record Token(String value, String clientId, long expiresAt) {

        boolean isUsable(String forClientId) {
            return this.clientId.equals(forClientId)
                    && System.currentTimeMillis() < this.expiresAt - EARLY_REFRESH.toMillis();
        }
    }
}
