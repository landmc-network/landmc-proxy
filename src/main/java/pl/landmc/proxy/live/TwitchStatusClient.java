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
import org.slf4j.Logger;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Asks Twitch whether a channel is on air.
 *
 * <p>{@code helix/streams} answers with an array of the streams matching the query, so a channel
 * that is live has one entry and one that is not has none. There is no "is live" field to read:
 * the presence of the entry is the answer.
 */
public final class TwitchStatusClient implements StreamStatusClient {

    private static final URI TOKEN_ENDPOINT = URI.create("https://id.twitch.tv/oauth2/token");
    private static final String STREAMS = "https://api.twitch.tv/helix/streams?user_login=";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final OAuthTokenSource tokens;
    private final ProxyConfig config;
    private final Logger logger;

    public TwitchStatusClient(HttpClient http, ProxyConfig config, Logger logger) {
        this.http = Objects.requireNonNull(http, "http");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.tokens = new OAuthTokenSource(http, TOKEN_ENDPOINT, "Twitch", logger);
    }

    @Override
    public StreamPlatform platform() {
        return StreamPlatform.TWITCH;
    }

    @Override
    public boolean isConfigured() {
        return !this.clientId().isEmpty() && !this.clientSecret().isEmpty();
    }

    @Override
    public CompletableFuture<StreamStatus> check(String identifier) {
        if (!this.isConfigured()) {
            return CompletableFuture.completedFuture(StreamStatus.UNKNOWN);
        }

        return this.tokens.token(this.clientId(), this.clientSecret())
                .thenCompose(token -> this.query(identifier, token))
                .exceptionally(throwable -> {
                    this.logger.warn("Could not check Twitch for {}: {}", identifier, throwable.toString());
                    return StreamStatus.UNKNOWN;
                });
    }

    private CompletableFuture<StreamStatus> query(String identifier, String token) {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create(STREAMS + URLEncoder.encode(identifier, StandardCharsets.UTF_8)))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Client-Id", this.clientId())
                .GET()
                .build();

        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 401) {
                        // The token was rejected; throw it away so the next check asks for a
                        // new one rather than repeating the same rejection for an hour.
                        this.tokens.invalidate();
                        return StreamStatus.UNKNOWN;
                    }
                    if (response.statusCode() != 200) {
                        this.logger.warn("Twitch answered HTTP {} for {}", response.statusCode(), identifier);
                        return StreamStatus.UNKNOWN;
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.getAsJsonArray("data").isEmpty()
                            ? StreamStatus.OFFLINE
                            : StreamStatus.LIVE;
                });
    }

    private String clientId() {
        return this.config.live.twitch.clientId.trim();
    }

    private String clientSecret() {
        return this.config.live.twitch.clientSecret.trim();
    }
}
