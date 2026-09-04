package pl.landmc.proxy.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The manifest is written by another process and read over the network, so every field in it is
 * untrusted input. These tests pin down what the proxy accepts, because a bad manifest that gets
 * through is not a parse error - it is every player on the network being kicked.
 */
class ResourcePackManifestTest {

    private static final String SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
    private static final Gson GSON = new Gson();

    private static String json(String... overrides) {
        StringBuilder builder = new StringBuilder("{")
                .append("\"version\":1,")
                .append("\"packId\":\"3f2504e0-4f89-11d3-9a0c-0305e82c3301\",")
                .append("\"sha1\":\"").append(SHA1).append("\",")
                .append("\"urlTemplate\":\"https://packs.landmc.pl/{hash}.zip\",")
                .append("\"required\":true,")
                .append("\"prompt\":\"<gray>Pobierz paczke\",")
                .append("\"maxAttempts\":3,")
                .append("\"retryDelayMillis\":1000,")
                .append("\"retryMessage\":\"<gray>Ponawiam\",")
                .append("\"declinedKickMessage\":\"<red>Odrzucono\",")
                .append("\"downloadFailedKickMessage\":\"<red>Blad pobierania\",")
                .append("\"sendDelayMillis\":0,")
                .append("\"resendAfterRebuild\":true");
        for (String override : overrides) {
            builder.append(',').append(override);
        }
        return builder.append('}').toString();
    }

    private static ResourcePackManifest parse(String... overrides) {
        return ResourcePackManifest.parse(GSON, json(overrides));
    }

    private static String messageOf(String... overrides) {
        return assertThrows(RuntimeException.class, () -> parse(overrides)).getMessage();
    }

    @Test
    @DisplayName("a well formed manifest parses and exposes the pack identity")
    void parsesAValidManifest() {
        ResourcePackManifest manifest = parse();

        assertEquals(SHA1, manifest.sha1());
        assertEquals("3f2504e0-4f89-11d3-9a0c-0305e82c3301", manifest.id().toString());
        assertEquals(20, manifest.hash().length);
        assertTrue(manifest.required());
    }

    @Test
    @DisplayName("the hash is substituted into the URL, so a rebuild busts every client cache")
    void substitutesTheHashIntoTheUrl() {
        assertEquals("https://packs.landmc.pl/" + SHA1 + ".zip", parse().url("landmc.pl"));
    }

    @Test
    @DisplayName("{host} follows the address the player actually connected to")
    void substitutesTheConnectionHost() {
        String template = "\"urlTemplate\":\"https://{host}/packs/{hash}.zip\"";

        assertEquals(
                "https://mc.landmc.pl/packs/" + SHA1 + ".zip",
                parse(template).url("MC.LandMC.pl"));
    }

    @Test
    @DisplayName("an IPv6 connection host is bracketed rather than producing a broken URL")
    void bracketsAnIpv6ConnectionHost() {
        String template = "\"urlTemplate\":\"https://{host}/packs/{hash}.zip\"";

        assertEquals(
                "https://[::1]/packs/" + SHA1 + ".zip",
                parse(template).url("::1"));
    }

    @Test
    @DisplayName("a host that could smuggle a path or a second URL is rejected")
    void rejectsAHostileConnectionHost() {
        String template = "\"urlTemplate\":\"https://{host}/packs/{hash}.zip\"";

        assertThrows(IllegalArgumentException.class, () -> parse(template).url("evil.example/../"));
        assertThrows(IllegalArgumentException.class, () -> parse(template).url(""));
    }

    @Test
    @DisplayName("a manifest from a newer builder is refused instead of being half understood")
    void rejectsAnUnknownVersion() {
        assertTrue(messageOf("\"version\":2").contains("version"));
    }

    @Test
    @DisplayName("the hash must be exactly forty lowercase hex characters, as the protocol requires")
    void rejectsAMalformedHash() {
        assertTrue(messageOf("\"sha1\":\"" + SHA1.toUpperCase(java.util.Locale.ROOT) + "\"").contains("sha1"));
        assertTrue(messageOf("\"sha1\":\"abc\"").contains("sha1"));
    }

    @Test
    @DisplayName("a URL without a scheme or host is refused before it reaches a client")
    void rejectsAUrlWithoutSchemeOrHost() {
        assertThrows(RuntimeException.class, () -> parse("\"urlTemplate\":\"/packs/{hash}.zip\""));
        assertThrows(RuntimeException.class, () -> parse("\"urlTemplate\":\"ftp://packs.landmc.pl/{hash}.zip\""));
    }

    @Test
    @DisplayName("a URL that never changes with the hash would serve a stale cached pack forever")
    void rejectsAUrlThatIgnoresTheHash() {
        assertTrue(messageOf("\"urlTemplate\":\"https://packs.landmc.pl/pack.zip\"").contains("hash"));
    }

    @Test
    @DisplayName("attempt and delay limits are bounded, so a typo cannot hang every login")
    void rejectsOutOfRangeLimits() {
        assertTrue(messageOf("\"maxAttempts\":0").contains("maxAttempts"));
        assertTrue(messageOf("\"maxAttempts\":50").contains("maxAttempts"));
        assertTrue(messageOf("\"retryDelayMillis\":600000").contains("retryDelayMillis"));
        assertTrue(messageOf("\"sendDelayMillis\":-1").contains("sendDelayMillis"));
    }

    @Test
    @DisplayName("an empty document is a failure, not a manifest with null fields")
    void rejectsAnEmptyDocument() {
        assertThrows(RuntimeException.class, () -> ResourcePackManifest.parse(GSON, "null"));
        assertThrows(RuntimeException.class, () -> ResourcePackManifest.parse(GSON, "{}"));
    }

    @Test
    @DisplayName("samePack compares identity and hash, which is what decides a re-offer")
    void comparesPacksByIdentityAndHash() {
        ResourcePackManifest manifest = parse();
        String otherSha1 = "1111111111111111111111111111111111111111";

        assertTrue(manifest.samePack(parse()));
        assertFalse(manifest.samePack(parse("\"sha1\":\"" + otherSha1 + "\"")));
        assertFalse(manifest.samePack(
                parse("\"packId\":\"11111111-1111-1111-1111-111111111111\"")));
        assertFalse(manifest.samePack(null));
    }
}
