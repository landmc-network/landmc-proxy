package pl.landmc.proxy.resourcepack;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * Fetches the resource-pack manifest over HTTP.
 *
 * <p>Only on demand: once when the proxy starts, and again when the builder announces a rebuild
 * or a backend asks for a refresh. There is no timer here - that is the point of the change.
 *
 * <p>Separated from delivery so the two can be reasoned about apart: this one does I/O and
 * knows nothing about players, and the service that offers packs to players does no I/O.
 */
public final class ManifestSource {

    /** A manifest is a small document; anything larger is a wrong endpoint, not a big pack. */
    private static final int MAX_MANIFEST_CHARACTERS = 65_536;

    private final ProxyConfig config;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public ManifestSource(ProxyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, config.resourcePack.requestTimeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Reads the manifest.
     *
     * <p>The future fails rather than returning null, so the caller decides what an unreachable
     * builder means - at startup it is a warning and the pack is simply not offered yet, which
     * is better than refusing to start a proxy over a cosmetic feature.
     */
    public CompletableFuture<ResourcePackManifest> fetch() {
        ProxyConfig.ResourcePackSection settings = this.config.resourcePack;

        HttpRequest.Builder request;
        try {
            request = HttpRequest.newBuilder(URI.create(settings.manifestUrl))
                    .timeout(Duration.ofSeconds(Math.max(1, settings.requestTimeoutSeconds)))
                    .GET();
        }
        catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Invalid manifest URL: " + settings.manifestUrl, exception));
        }

        if (!settings.manifestToken.isBlank()) {
            request.header("X-Manifest-Token", settings.manifestToken);
        }

        return this.httpClient
                .sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(ManifestSource::requireOk)
                .thenApply(body -> ResourcePackManifest.parse(this.gson, body));
    }

    private static String requireOk(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Manifest endpoint returned HTTP " + response.statusCode());
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Manifest endpoint returned an empty body");
        }
        if (body.length() > MAX_MANIFEST_CHARACTERS) {
            throw new IllegalStateException("Manifest is larger than " + MAX_MANIFEST_CHARACTERS + " characters");
        }
        return body;
    }
}
