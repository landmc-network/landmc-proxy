package pl.landmc.proxy.live;

import com.google.gson.JsonArray;
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
 * Asks Kick whether a channel is on air.
 *
 * <p>Unlike Twitch, Kick answers for a channel whether or not it is streaming, and the answer
 * carries an {@code is_live} flag - so an empty array here means "no such channel", which is a
 * different thing from "not live" and is reported as unknown rather than offline. Telling
 * somebody they are not streaming when the truth is that their slug is wrong sends them looking
 * for the wrong problem.
 */
public final class KickStatusClient implements StreamStatusClient {

    private static final URI TOKEN_ENDPOINT = URI.create("https://id.kick.com/oauth/token");
    private static final String CHANNELS = "https://api.kick.com/public/v1/channels?slug=";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final OAuthTokenSource tokens;
    private final ProxyConfig config;
    private final Logger logger;

    public KickStatusClient(HttpClient http, ProxyConfig config, Logger logger) {
        this.http = Objects.requireNonNull(http, "http");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.tokens = new OAuthTokenSource(http, TOKEN_ENDPOINT, "Kick", logger);
    }

    @Override
    public StreamPlatform platform() {
        return StreamPlatform.KICK;
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
                    this.logger.warn("Could not check Kick for {}: {}", identifier, throwable.toString());
                    return StreamStatus.UNKNOWN;
                });
    }

    private CompletableFuture<StreamStatus> query(String identifier, String token) {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create(CHANNELS + URLEncoder.encode(identifier, StandardCharsets.UTF_8)))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 401) {
                        this.tokens.invalidate();
                        return StreamStatus.UNKNOWN;
                    }
                    if (response.statusCode() != 200) {
                        this.logger.warn("Kick answered HTTP {} for {}", response.statusCode(), identifier);
                        return StreamStatus.UNKNOWN;
                    }

                    JsonArray data = JsonParser.parseString(response.body())
                            .getAsJsonObject()
                            .getAsJsonArray("data");

                    if (data == null || data.isEmpty()) {
                        return StreamStatus.UNKNOWN;
                    }

                    JsonObject channel = data.get(0).getAsJsonObject();
                    JsonObject stream = channel.getAsJsonObject("stream");

                    return stream != null
                            && stream.has("is_live")
                            && stream.get("is_live").getAsBoolean()
                            ? StreamStatus.LIVE
                            : StreamStatus.OFFLINE;
                });
    }

    private String clientId() {
        return this.config.live.kick.clientId.trim();
    }

    private String clientSecret() {
        return this.config.live.kick.clientSecret.trim();
    }
}
