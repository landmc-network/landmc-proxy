package pl.landmc.proxy.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import pl.landmc.platform.messaging.redis.RedisConfig;

/**
 * {@code config.yml} - everything the proxy needs that is not a message.
 *
 * <p>Loaded through the platform's {@code ConfigService}; there is no configuration loader in
 * this project. The Redis section is the platform's own {@code RedisConfig}, embedded rather
 * than restated, so a change to the messaging options lands here without an edit.
 */
public class ProxyConfig extends OkaeriConfig {

    public ProxySection proxy = new ProxySection();

    public RoutingSection routing = new RoutingSection();

    public MaintenanceSection maintenance = new MaintenanceSection();

    @Comment("")
    public FallbackSection fallback = new FallbackSection();

    public MessagingSection messaging = new MessagingSection();

    public static class ProxySection extends OkaeriConfig {

        @Comment("Identyfikator tego proxy w sieci - musi byc unikalny.")
        @Comment("Pod tym ID proxy publikuje i odbiera wiadomosci; zmiana wymaga restartu.")
        @CustomKey("server-id")
        public String serverId = "proxy-1";
    }

    public static class RoutingSection extends OkaeriConfig {

        @Comment("Serwer, na ktory trafia gracz wchodzacy do sieci oraz wyrzucony z backendu.")
        @Comment("Musi istniec w konfiguracji serwerow Velocity.")
        @CustomKey("fallback-server")
        public String fallbackServer = "lobby-1";
    }

    public static class MaintenanceSection extends OkaeriConfig {

        @Comment("Tryb serwisowy: gracze bez uprawnienia ponizej nie wejda do sieci.")
        public boolean enabled = false;

        @Comment("Uprawnienie omijajace tryb serwisowy.")
        @CustomKey("bypass-permission")
        public String bypassPermission = "landmc.maintenance.bypass";
    }

    public static class FallbackSection extends OkaeriConfig {

        @Comment("Czy gracz wyrzucony z backendu ma trafic na fallback zamiast rozlaczenia.")
        @Comment("Dotyczy tylko restartow i zamykania serwera - bany i kicki moderacyjne")
        @Comment("zawsze trafiaja do gracza jako ekran rozlaczenia.")
        public boolean enabled = true;
    }

    public static class MessagingSection extends OkaeriConfig {

        @Comment("Komunikacja z instancjami Paper przez Redis.")
        @Comment("Wylaczenie nie wylacza szyny - proxy uzywa wtedy transportu w obrebie procesu,")
        @Comment("wiec plugin wstaje i dziala bez Redisa, tylko nie widzi innych instancji.")
        public boolean enabled = true;

        public RedisConfig redis = new RedisConfig();
    }
}
