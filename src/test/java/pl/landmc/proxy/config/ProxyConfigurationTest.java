package pl.landmc.proxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.platform.config.ConfigService;
import pl.landmc.platform.notice.AudienceNoticeService;
import pl.landmc.platform.notice.PlatformNotice;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;

/**
 * Covers the part of the bootstrap that can be exercised without a running proxy: the
 * configuration, and specifically that a config containing {@code Notice} fields can be loaded
 * at all.
 *
 * <p>That last point is the one worth a test. The notice service supplies the serdes pack the
 * loader needs, while the loader produces the messages the notice service reads - so the
 * bootstrap builds the service against a provider that is filled in afterwards. If that order
 * ever breaks, the proxy fails on startup with a serialisation error, and this catches it here
 * instead.
 */
class ProxyConfigurationTest {

    @Test
    void loadsBothFilesInTheOrderTheBootstrapUses(@TempDir Path directory) {
        Loaded loaded = load(directory);

        assertEquals("proxy-1", loaded.config().proxy.serverId);
        assertEquals("lobby-1", loaded.config().routing.fallbackServer);
        assertFalse(loaded.config().maintenance.enabled);
        assertTrue(loaded.config().messaging.enabled);
        assertEquals("landmc.maintenance.bypass", loaded.config().maintenance.bypassPermission);
    }

    @Test
    void writesNoticeFieldsAsYaml(@TempDir Path directory) throws IOException {
        load(directory);

        String yaml = Files.readString(directory.resolve("messages.yml"));

        // A Notice is serialised by the platform's serdes pack; without it the load would fail.
        assertTrue(yaml.contains("server-not-found:"), yaml);
        assertTrue(yaml.contains("maintenance-enabled:"), yaml);
        assertTrue(yaml.contains("Nie znaleziono serwera"), yaml);
    }

    @Test
    void embedsThePlatformTechnicalMessages(@TempDir Path directory) throws IOException {
        Loaded loaded = load(directory);

        String yaml = Files.readString(directory.resolve("messages.yml"));

        assertTrue(yaml.contains("platform:"), yaml);
        assertTrue(yaml.contains("command-no-permission:"), yaml);
        assertEquals(
                "<red>Błąd> <gray>Nie posiadasz uprawnień do tej komendy.",
                loaded.messages().platform.message(PlatformNotice.COMMAND_NO_PERMISSION));
    }

    @Test
    void writesEveryKeyInKebabCase(@TempDir Path directory) throws IOException {
        // Okaeri names a key after the field unless told otherwise, so a new field arrives as
        // camelCase and sits next to kebab-case neighbours until somebody notices in a running
        // proxy. Checking the whole document means the next @CustomKey cannot be forgotten.
        for (String fileName : new String[] {"config.yml", "messages.yml"}) {
            load(directory);
            for (String line : Files.readAllLines(directory.resolve(fileName))) {
                java.util.regex.Matcher key =
                        java.util.regex.Pattern.compile("^\s*([A-Za-z0-9_-]+):(\s|$)").matcher(line);
                if (key.find()) {
                    assertEquals(
                            key.group(1).toLowerCase(java.util.Locale.ROOT),
                            key.group(1),
                            fileName + " has a camelCase key: " + line.trim());
                }
            }
        }
    }

    @Test
    void embedsThePlatformRedisSection(@TempDir Path directory) throws IOException {
        Loaded loaded = load(directory);

        String yaml = Files.readString(directory.resolve("config.yml"));

        assertTrue(yaml.contains("redis:"), yaml);
        assertEquals("127.0.0.1", loaded.config().messaging.redis.host);
        assertEquals("landmc", loaded.config().messaging.redis.channelPrefix);
    }

    @Test
    void technicalMessagesRenderThroughTheSharedFormatter(@TempDir Path directory) {
        Loaded loaded = load(directory);
        ComponentFormatter formatter = ComponentFormatter.standard();

        var notices = new AudienceNoticeService<>(loaded.messages().platform, formatter);

        assertEquals(
                "Błąd> Nie posiadasz uprawnień do tej komendy.",
                formatter.plain(notices.render(PlatformNotice.COMMAND_NO_PERMISSION)));
    }

    @Test
    void kickScreensStayPlainStringsNotNotices(@TempDir Path directory) {
        ComponentFormatter formatter = ComponentFormatter.standard();
        Loaded loaded = load(directory);

        // A disconnect screen has to become a single Component; a Notice could not.
        String rendered = formatter.plain(formatter.format(loaded.messages().maintenanceKick));

        // The network's kick screens all lead with the domain, as they have since the first
        // version of LandMC; the reason follows underneath.
        assertTrue(rendered.startsWith("LANDMC.PL"), rendered);
        assertTrue(rendered.contains("przerwa techniczna"), rendered);
    }

    @Test
    void overridingAMessageKeepsTheRestAtDefaults(@TempDir Path directory) throws IOException {
        Files.writeString(
                directory.resolve("messages.yml"),
                "maintenance-kick: \"<red>Wracamy za chwilę.\"\n");

        Loaded loaded = load(directory);

        assertEquals("<red>Wracamy za chwilę.", loaded.messages().maintenanceKick);
        assertEquals(
                "<red>Błąd> <gray>Ta komenda nie istnieje.",
                loaded.messages().platform.message(PlatformNotice.COMMAND_NOT_FOUND));
    }

    /**
     * Mirrors {@code ProxyBootstrap}: the notice service is created first against a holder, then
     * the configuration is loaded with the serdes pack it provides.
     */
    private static Loaded load(Path directory) {
        ProxyMessages[] holder = new ProxyMessages[1];

        VelocityNoticeService<ProxyMessages> notices = new VelocityNoticeService<>(
                unusableProxy(), locale -> holder[0], ComponentFormatter.standard());

        ConfigService configs = new ConfigService(notices.okaeriSerdes());
        ProxyConfig config = configs.load(directory, "config.yml", ProxyConfig.class);
        holder[0] = configs.load(directory, "messages.yml", ProxyMessages.class);

        return new Loaded(config, holder[0]);
    }

    /**
     * A {@code ProxyServer} that throws on every call.
     *
     * <p>The notice service needs one to resolve viewers, and nothing in this test sends a
     * message - so a stub that fails loudly is better than a half-real proxy: if a future change
     * makes configuration loading touch the proxy, the test says so instead of quietly passing.
     */
    private static ProxyServer unusableProxy() {
        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[] {ProxyServer.class},
                (instance, method, args) -> {
                    throw new UnsupportedOperationException(
                            "Configuration loading must not call ProxyServer#" + method.getName());
                });
    }

    private record Loaded(ProxyConfig config, ProxyMessages messages) {
    }
}
