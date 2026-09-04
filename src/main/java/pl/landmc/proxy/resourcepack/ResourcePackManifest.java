package pl.landmc.proxy.resourcepack;

import com.google.gson.Gson;
import java.net.IDN;
import java.net.URI;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ResourcePackManifest(
        int version,
        String packId,
        String sha1,
        String urlTemplate,
        boolean required,
        String prompt,
        int maxAttempts,
        long retryDelayMillis,
        String retryMessage,
        String declinedKickMessage,
        String downloadFailedKickMessage,
        long sendDelayMillis,
        boolean resendAfterRebuild) {

    public static final int CURRENT_VERSION = 1;

    public static ResourcePackManifest parse(Gson gson, String json) {
        ResourcePackManifest manifest = gson.fromJson(json, ResourcePackManifest.class);
        if (manifest == null) {
            throw new IllegalArgumentException("Resource-pack manifest is empty");
        }
        manifest.validate();
        return manifest;
    }

    public UUID id() {
        return UUID.fromString(this.packId);
    }

    public byte[] hash() {
        return HexFormat.of().parseHex(this.sha1);
    }

    public String url(String connectionHost) {
        String url = this.urlTemplate.replace("{hash}", this.sha1);
        if (url.contains("{host}")) {
            url = url.replace("{host}", normalizeUrlHost(connectionHost));
        }
        return url;
    }

    public boolean samePack(ResourcePackManifest other) {
        return other != null
                && this.id().equals(other.id())
                && this.sha1.equals(other.sha1);
    }

    private void validate() {
        if (this.version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported resource-pack manifest version: " + this.version);
        }
        UUID.fromString(requireText(this.packId, "packId"));
        if (!requireText(this.sha1, "sha1").matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Manifest sha1 must contain 40 lowercase hex characters");
        }
        String template = requireText(this.urlTemplate, "urlTemplate");
        URI uri = URI.create(template
                .replace("{hash}", this.sha1)
                .replace("{host}", "example.invalid"));
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("Manifest URL must use HTTP or HTTPS and contain a host");
        }
        if (!template.contains("{hash}") && !template.contains(this.sha1)) {
            throw new IllegalArgumentException("Manifest URL must contain {hash} or the current sha1");
        }
        Objects.requireNonNull(this.prompt, "prompt");
        Objects.requireNonNull(this.retryMessage, "retryMessage");
        Objects.requireNonNull(this.declinedKickMessage, "declinedKickMessage");
        Objects.requireNonNull(this.downloadFailedKickMessage, "downloadFailedKickMessage");
        if (this.maxAttempts < 1 || this.maxAttempts > 5) {
            throw new IllegalArgumentException("Manifest maxAttempts must be between 1 and 5");
        }
        if (this.retryDelayMillis < 0L || this.retryDelayMillis > 10_000L) {
            throw new IllegalArgumentException("Manifest retryDelayMillis is outside the allowed range");
        }
        if (this.sendDelayMillis < 0L || this.sendDelayMillis > 60_000L) {
            throw new IllegalArgumentException("Manifest sendDelayMillis is outside the allowed range");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Manifest " + name + " cannot be blank");
        }
        return value;
    }

    private static String normalizeUrlHost(String connectionHost) {
        if (connectionHost == null || connectionHost.isBlank()) {
            throw new IllegalArgumentException("Player connection host is unavailable");
        }
        String host = connectionHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.indexOf(':') >= 0) {
            if (!host.matches("[0-9a-fA-F:.]+")) {
                throw new IllegalArgumentException("Player connection host contains invalid IPv6 characters");
            }
            return '[' + host + ']';
        }
        String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        if (!asciiHost.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("Player connection host contains invalid characters");
        }
        return asciiHost;
    }
}
