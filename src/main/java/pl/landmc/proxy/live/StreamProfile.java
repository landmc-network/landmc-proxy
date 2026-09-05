package pl.landmc.proxy.live;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Where somebody streams: which platform, and who they are on it.
 *
 * <p>Both halves are kept, not just the URL. The identifier is what an API is asked about and
 * the URL is what a player clicks, and deriving one from the other on every use means parsing
 * the same string over and over and getting it wrong once.
 *
 * <p>Parsing is deliberately narrow. What comes out of here is put into a clickable message
 * shown to everybody online, so anything that is not recognisably one of the three platforms is
 * refused rather than passed through - an arbitrary link would make the command an advertising
 * slot, and a lookalike host is how somebody would use it as one.
 *
 * @param platform which service
 * @param identifier the channel's own name there - a Twitch login, a Kick slug, a TikTok handle
 * @param url the address a player opens
 */
public record StreamProfile(StreamPlatform platform, String identifier, String url) {

    private static final Pattern TWITCH_LOGIN = Pattern.compile("[A-Za-z0-9_]{3,25}");
    private static final Pattern KICK_SLUG = Pattern.compile("[A-Za-z0-9_-]{3,25}");
    private static final Pattern TIKTOK_HANDLE = Pattern.compile("[A-Za-z0-9._]{2,24}");

    /**
     * Hosts, exactly.
     *
     * <p>Matched in full rather than by suffix: {@code twitch.tv.example.com} ends with nothing
     * that fails a "contains twitch.tv" test, and a link to it would be broadcast to everybody.
     */
    private static final Set<String> TWITCH_HOSTS = Set.of("twitch.tv", "www.twitch.tv", "m.twitch.tv");
    private static final Set<String> KICK_HOSTS = Set.of("kick.com", "www.kick.com");
    private static final Set<String> TIKTOK_HOSTS = Set.of("tiktok.com", "www.tiktok.com", "m.tiktok.com");
    private static final Set<String> YOUTUBE_HOSTS =
            Set.of("youtube.com", "www.youtube.com", "m.youtube.com");

    /**
     * A YouTube handle, the {@code @name} form every channel has had since 2022.
     *
     * <p>Only that form. A channel is also reachable as {@code /channel/UC...} and as the old
     * {@code /c/name}, and both of those are addresses rather than names - storing one means the
     * announcement shows a string of random characters where a person's name should be.
     */
    private static final Pattern YOUTUBE_HANDLE = Pattern.compile("[A-Za-z0-9._-]{3,30}");

    public StreamProfile {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(url, "url");
    }

    /**
     * Reads what somebody typed.
     *
     * <p>Accepts a bare Twitch login, because that is what people write, and a full link to any
     * of the three platforms. Everything else is empty - including a shortened TikTok link,
     * which hides where it actually leads and would have to be followed to find out.
     */
    public static Optional<StreamProfile> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String candidate = input.trim();

        // A bare name with no dots and no slashes is a Twitch login: the platform people name
        // without a link. Anything containing a dot is treated as an address, so a typo like
        // "twitch.tv" alone is refused rather than read as a login.
        String bare = candidate.startsWith("@") ? candidate.substring(1) : candidate;
        if (!bare.contains(".") && !bare.contains("/") && TWITCH_LOGIN.matcher(bare).matches()) {
            return Optional.of(twitch(bare));
        }

        return parseUrl(candidate.contains("://") ? candidate : "https://" + candidate);
    }

    private static Optional<StreamProfile> parseUrl(String candidate) {
        URI uri;
        try {
            uri = new URI(candidate);
        }
        catch (URISyntaxException exception) {
            return Optional.empty();
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (host == null
                || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                // user@host in a link is how a lookalike address is made to read as the real
                // one; there is no legitimate stream link that carries credentials.
                || uri.getUserInfo() != null) {
            return Optional.empty();
        }

        String normalised = host.toLowerCase(Locale.ROOT);

        if (TWITCH_HOSTS.contains(normalised)) {
            return firstSegment(uri, TWITCH_LOGIN).map(StreamProfile::twitch);
        }
        if (KICK_HOSTS.contains(normalised)) {
            return firstSegment(uri, KICK_SLUG).map(StreamProfile::kick);
        }
        if (TIKTOK_HOSTS.contains(normalised)) {
            return tikTokHandle(uri).map(StreamProfile::tikTok);
        }
        if (YOUTUBE_HOSTS.contains(normalised)) {
            return youTubeHandle(uri).map(StreamProfile::youTube);
        }

        return Optional.empty();
    }

    /** The first path segment, when it is the only meaningful one and it looks like a name. */
    private static Optional<String> firstSegment(URI uri, Pattern shape) {
        String path = uri.getPath();
        if (path == null) {
            return Optional.empty();
        }

        String[] segments = path.split("/");
        String segment = null;
        for (String candidate : segments) {
            if (candidate.isEmpty()) {
                continue;
            }
            if (segment != null) {
                // A second segment means this is a clip, a video or a directory page rather
                // than a channel, and the channel is what an announcement should point at.
                return Optional.empty();
            }
            segment = candidate;
        }

        return Optional.ofNullable(segment).filter(value -> shape.matcher(value).matches());
    }

    /** TikTok handles are {@code /@name}, optionally followed by {@code /live}. */
    private static Optional<String> tikTokHandle(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return Optional.empty();
        }

        String[] segments = path.split("/");
        String handle = null;
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.isEmpty()) {
                continue;
            }

            if (handle == null) {
                if (!segment.startsWith("@")) {
                    return Optional.empty();
                }
                handle = segment.substring(1);
                continue;
            }

            if (!segment.equalsIgnoreCase("live")) {
                return Optional.empty();
            }
        }

        return Optional.ofNullable(handle).filter(value -> TIKTOK_HANDLE.matcher(value).matches());
    }

    /**
     * YouTube handles are {@code /@name}, optionally followed by {@code /live} or
     * {@code /streams}.
     *
     * <p>The two trailing segments are what a creator copies out of the address bar while they
     * are streaming, so refusing them would refuse the most likely thing anybody pastes.
     */
    private static Optional<String> youTubeHandle(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return Optional.empty();
        }

        String handle = null;
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }

            if (handle == null) {
                if (!segment.startsWith("@")) {
                    return Optional.empty();
                }
                handle = segment.substring(1);
                continue;
            }

            if (!segment.equalsIgnoreCase("live") && !segment.equalsIgnoreCase("streams")) {
                return Optional.empty();
            }
        }

        return Optional.ofNullable(handle).filter(value -> YOUTUBE_HANDLE.matcher(value).matches());
    }

    public static StreamProfile twitch(String login) {
        return new StreamProfile(
                StreamPlatform.TWITCH, login, "https://www.twitch.tv/" + login);
    }

    public static StreamProfile kick(String slug) {
        return new StreamProfile(StreamPlatform.KICK, slug, "https://kick.com/" + slug);
    }

    public static StreamProfile tikTok(String handle) {
        return new StreamProfile(
                StreamPlatform.TIKTOK, handle, "https://www.tiktok.com/@" + handle + "/live");
    }

    public static StreamProfile youTube(String handle) {
        return new StreamProfile(
                StreamPlatform.YOUTUBE, handle, "https://www.youtube.com/@" + handle + "/live");
    }

    /** Rebuilds a stored profile without parsing the URL again. */
    public static StreamProfile of(StreamPlatform platform, String identifier) {
        return switch (platform) {
            case TWITCH -> twitch(identifier);
            case KICK -> kick(identifier);
            case TIKTOK -> tikTok(identifier);
            case YOUTUBE -> youTube(identifier);
        };
    }
}
