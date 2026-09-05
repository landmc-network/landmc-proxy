package pl.landmc.proxy.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What may become a link in a message sent to everybody online.
 *
 * <p>That is what makes this worth testing properly rather than as parsing trivia. A profile is
 * broadcast network-wide as a clickable button, so anything that gets through here that is not
 * genuinely one of the three platforms turns the feature into an advertising slot - and the
 * interesting inputs are the ones designed to look like they belong.
 */
class StreamProfileTest {

    @Test
    @DisplayName("a bare name is a Twitch login, which is how people write it")
    void readsABareTwitchLogin() {
        StreamProfile profile = StreamProfile.parse("Crispi").orElseThrow();

        assertEquals(StreamPlatform.TWITCH, profile.platform());
        assertEquals("Crispi", profile.identifier());
        assertEquals("https://www.twitch.tv/Crispi", profile.url());

        assertEquals(profile, StreamProfile.parse("@Crispi").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://twitch.tv/crispi",
            "https://www.twitch.tv/crispi",
            "https://m.twitch.tv/crispi",
            "http://twitch.tv/crispi",
            "twitch.tv/crispi",
            "https://www.twitch.tv/crispi/",
    })
    @DisplayName("every shape of a Twitch channel link reads as the same channel")
    void readsTwitchLinks(String input) {
        StreamProfile profile = StreamProfile.parse(input).orElseThrow();

        assertEquals(StreamPlatform.TWITCH, profile.platform());
        assertEquals("crispi", profile.identifier());
    }

    @Test
    @DisplayName("Kick and TikTok links read as their own platforms")
    void readsTheOtherPlatforms() {
        StreamProfile kick = StreamProfile.parse("https://kick.com/crispi").orElseThrow();
        assertEquals(StreamPlatform.KICK, kick.platform());
        assertEquals("crispi", kick.identifier());
        assertEquals("https://kick.com/crispi", kick.url());

        StreamProfile tikTok = StreamProfile.parse("https://www.tiktok.com/@crispi").orElseThrow();
        assertEquals(StreamPlatform.TIKTOK, tikTok.platform());
        assertEquals("crispi", tikTok.identifier());

        // The link people actually copy is the one with /live on the end.
        assertEquals(tikTok, StreamProfile.parse("https://www.tiktok.com/@crispi/live").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Hosts that contain a platform's name without being it. Matching by suffix or by
            // "contains" would accept every one of these.
            "https://twitch.tv.example.com/crispi",
            "https://nottwitch.tv/crispi",
            "https://kick.com.example.com/crispi",
            "https://tiktok.com.evil.test/@crispi",
            "https://example.com/twitch.tv/crispi",
            // Credentials in the authority: the host here is evil.test, not twitch.tv.
            "https://twitch.tv@evil.test/crispi",
            "https://www.twitch.tv:pass@evil.test/crispi",
    })
    @DisplayName("a host that only looks like a platform is refused")
    void refusesLookalikeHosts(String input) {
        assertTrue(
                StreamProfile.parse(input).isEmpty(),
                "accepted a link to something that is not the platform: " + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Not a channel: a clip, a video, a category page. An announcement points at a
            // channel, and these are how somebody links anything else on the domain.
            "https://twitch.tv/crispi/clip/SomeClipName",
            "https://www.twitch.tv/videos/123456",
            "https://www.twitch.tv/directory/game/Minecraft",
            "https://kick.com/crispi/videos/abc",
            "https://www.tiktok.com/@crispi/video/123",
    })
    @DisplayName("a link to something other than a channel is refused")
    void refusesNonChannelLinks(String input) {
        assertTrue(StreamProfile.parse(input).isEmpty(), "accepted: " + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // A shortened link hides where it goes; the only way to find out is to follow it,
            // and following a link somebody supplied is not something a proxy should do.
            "https://vm.tiktok.com/ZMabcdefg/",
            "https://vt.tiktok.com/ZMabcdefg/",
            "https://bit.ly/abcdef",
    })
    @DisplayName("a shortened link is refused rather than followed")
    void refusesShortenedLinks(String input) {
        assertTrue(StreamProfile.parse(input).isEmpty(), "accepted: " + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "ftp://twitch.tv/crispi",
            "https://twitch.tv/",
            "https://twitch.tv",
            "https://www.tiktok.com/crispi",
            "twitch.tv",
            "ab",
            "  ",
            "a b c",
    })
    @DisplayName("nonsense, and anything without a channel, is refused")
    void refusesTheRest(String input) {
        assertTrue(StreamProfile.parse(input).isEmpty(), "accepted: " + input);
    }

    @Test
    @DisplayName("null and empty are empty, not an exception")
    void handlesNothing() {
        assertTrue(StreamProfile.parse(null).isEmpty());
        assertTrue(StreamProfile.parse("").isEmpty());
    }

    @Test
    @DisplayName("a stored profile is rebuilt without parsing a URL again")
    void rebuildsFromStorage() {
        for (StreamPlatform platform : StreamPlatform.values()) {
            StreamProfile rebuilt = StreamProfile.of(platform, "crispi");

            assertEquals(platform, rebuilt.platform());
            assertEquals("crispi", rebuilt.identifier());
            // What was rebuilt has to parse back to itself, or storage and display disagree.
            assertEquals(rebuilt, StreamProfile.parse(rebuilt.url()).orElseThrow());
        }
    }

    @Test
    @DisplayName("only the platforms that can be checked are marked as checkable")
    void knowsWhatItCanVerify() {
        assertTrue(StreamPlatform.TWITCH.isVerifiable());
        assertTrue(StreamPlatform.KICK.isVerifiable());

        // TikTok has no endpoint that answers this without a logged-in session, and the command
        // treats it differently because of it.
        assertTrue(!StreamPlatform.TIKTOK.isVerifiable());
    }
}
