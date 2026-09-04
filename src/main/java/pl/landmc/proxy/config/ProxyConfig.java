package pl.landmc.proxy.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.List;
import pl.landmc.platform.database.DatabaseConfig;
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

    @Comment("")
    public CooldownSection cooldown = new CooldownSection();

    @Comment("")
    @CustomKey("resource-pack")
    public ResourcePackSection resourcePack = new ResourcePackSection();

    @Comment("")
    @Comment("Lista znajomych - jedyna funkcja proxy, ktora przezywa restart,")
    @Comment("wiec jedyna, ktora potrzebuje bazy. Wylaczona nie otwiera polaczenia.")
    public FriendsSection friends = new FriendsSection();

    @Comment("")
    @Comment("Baza uzywana przez liste znajomych. Sekcja pochodzi z landmc-platform.")
    public DatabaseConfig database = new DatabaseConfig();

    @Comment("")
    @CustomKey("help-progress")
    public HelpProgressSection helpProgress = new HelpProgressSection();

    @Comment("")
    public VanishSection vanish = new VanishSection();

    @Comment("")
    @CustomKey("join-debug")
    public JoinDebugSection joinDebug = new JoinDebugSection();

    @Comment("")
    public SkinSection skin = new SkinSection();

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

    public static class CooldownSection extends OkaeriConfig {

        @Comment("Globalny cooldown wspolny dla wszystkich backendow.")
        @Comment("Proxy trzyma stan, bo jako jedyne widzi gracza na wszystkich serwerach.")
        public boolean enabled = true;

        @Comment("")
        @Comment("Blokowanie klikniec w GUI po stronie proxy, na poziomie pakietow.")
        @Comment("Domyslnie WYLACZONE: przepisywanie pakietow ekwipunku na proxy potrafi")
        @Comment("rozjechac klientow w innych wersjach niz serwer. Wlaczaj tylko wtedy,")
        @Comment("gdy backend nie egzekwuje cooldownu sam.")
        @Comment("Wymaga pluginu PacketEvents - bez niego ta czesc po prostu nie startuje.")
        @CustomKey("intercept-gui-packets")
        public boolean interceptGuiPackets = false;

        @Comment("")
        @Comment("Egzekwowanie cooldownu komend przez proxy.")
        @Comment("Domyslnie WYLACZONE: anulowanie komendy na Velocity dziala poprawnie")
        @Comment("dopiero z SignedVelocity (MC 1.19.1+), a backend i tak egzekwuje")
        @Comment("cooldown u siebie - proxy tylko synchronizuje stan miedzy serwerami.")
        @CustomKey("enforce-commands-on-proxy")
        public boolean enforceCommandsOnProxy = false;

        @Comment("Komendy zwolnione z cooldownu. Logowanie MUSI tu byc:")
        @Comment("zablokowanie /login cooldownem odcina gracza od serwera.")
        @CustomKey("ignored-commands")
        public List<String> ignoredCommands = List.of(
                "login", "logowanie", "l", "register", "rejestracja", "reg", "2fa");

        @Comment("")
        @CustomKey("command-cooldown-millis")
        public long commandCooldownMillis = 500L;

        @CustomKey("gui-cooldown-millis")
        public long guiCooldownMillis = 250L;

        @Comment("Uprawnienie omijajace cooldown.")
        @CustomKey("bypass-permission")
        public String bypassPermission = "landmc.cooldown.bypass";

        @Comment("Jak czesto gracz moze dostac komunikat o cooldownie, w millis.")
        @Comment("Trzymajacy wcisniety przycisk zalalby sobie czat bez tego limitu.")
        @CustomKey("gui-message-interval-millis")
        public long guiMessageIntervalMillis = 750L;
    }

    public static class ResourcePackSection extends OkaeriConfig {

        @Comment("Paczka zasobow sieci wysylana przez proxy - gracz pobiera ja raz")
        @Comment("dla calej sieci, a nie przy kazdej zmianie serwera.")
        public boolean enabled = false;

        @Comment("")
        @Comment("Endpoint HTTP z manifestem paczki. Odpytywany RAZ przy starcie proxy;")
        @Comment("o przebudowie proxy dowiaduje sie z wiadomosci przez Redis, nie z pollingu.")
        @CustomKey("manifest-url")
        public String manifestUrl = "http://127.0.0.1:8082/resourcepack/manifest.json";

        @Comment("Naglowek X-Manifest-Token, jesli endpoint go wymaga.")
        @CustomKey("manifest-token")
        public String manifestToken = "";

        @CustomKey("request-timeout-seconds")
        public int requestTimeoutSeconds = 5;

        @Comment("")
        @Comment("Wstrzymanie pierwszego polaczenia do czasu zaladowania paczki.")
        @Comment("UWAGA: przy wlaczonej opcji awaria hostingu paczki blokuje wejscie na siec.")
        @CustomKey("wait-before-initial-server")
        public boolean waitBeforeInitialServer = true;

        @Comment("Maksymalny czas oczekiwania, potem gracz jest rozlaczany.")
        @CustomKey("wait-timeout-seconds")
        public int waitTimeoutSeconds = 90;

        @CustomKey("wait-timeout-message")
        public String waitTimeoutMessage =
                "<red>Nie udało się załadować paczki zasobów na czas. Połącz się ponownie.";

        @Comment("")
        @Comment("Odrzucanie ofert paczki od backendow, zeby gracz nie pobieral jej dwa razy.")
        @CustomKey("block-backend-offers")
        public boolean blockBackendOffers = true;
    }

    public static class FriendsSection extends OkaeriConfig {

        @Comment("Komenda /friend. Wylaczona nie laczy sie z baza.")
        public boolean enabled = false;

        @Comment("")
        @Comment("Gorny limit znajomych. Lista jest odczytywana przy kazdym /friend lista,")
        @Comment("wiec bez limitu jeden gracz potrafi zrobic z niej ciezkie zapytanie.")
        @CustomKey("max-friends")
        public int maxFriends = 100;

        @Comment("Ile dni zyje niezaakceptowane zaproszenie.")
        @Comment("Bez tego tabela zaproszen rosnie w nieskonczonosc.")
        @CustomKey("request-expiry-days")
        public int requestExpiryDays = 14;

        @Comment("")
        @Comment("Kanal wtyczkowy otwierajacy GUI znajomych na backendzie.")
        @Comment("Samo /friend bez argumentow prosi backend o otwarcie menu.")
        @CustomKey("gui-enabled")
        public boolean guiEnabled = true;
    }

    public static class HelpProgressSection extends OkaeriConfig {

        @Comment("Przekazywanie backendowi nazwy wykonanej komendy, na kanale wtyczkowym.")
        @Comment("Sluzy postepowi samouczka - backend nie widzi komend obslugiwanych przez proxy.")
        public boolean enabled = true;
    }

    public static class VanishSection extends OkaeriConfig {

        @Comment("Ukrywanie zvanishowanej administracji przed /msg i lista znajomych.")
        @Comment("Bez tego zvanishowanego moderatora da sie wykryc przez /msg.")
        public boolean enabled = true;

        @Comment("Id pluginu vanish w Velocity - odpytywany refleksyjnie, wiec")
        @Comment("jego brak niczego nie psuje, gracze sa wtedy po prostu widoczni.")
        @CustomKey("plugin-id")
        public String pluginId = "landmc-vanish";
    }

    public static class JoinDebugSection extends OkaeriConfig {

        @Comment("Szczegolowy log logowania gracza - od PreLogin az do rozlaczenia.")
        @Comment("Wlaczaj na czas diagnozy: kazde wejscie to kilkanascie linii w konsoli.")
        public boolean enabled = false;

        @Comment("")
        @Comment("UUID gracza w logu.")
        @CustomKey("include-uuid")
        public boolean includeUuid = true;

        @Comment("Adres IP gracza. To dane osobowe - wlaczaj tylko na czas diagnozy.")
        @CustomKey("include-remote-address")
        public boolean includeRemoteAddress = false;

        @Comment("Powod wyrzucenia z serwera backendowego. Moze zawierac tresc bana.")
        @CustomKey("include-kick-reason")
        public boolean includeKickReason = true;
    }

    public static class SkinSection extends OkaeriConfig {

        @Comment("Komenda /skin <nick> oparta o SkinsRestorer.")
        @Comment("Bez zainstalowanego SkinsRestorera komenda po prostu sie nie rejestruje.")
        public boolean enabled = true;

        @Comment("")
        @Comment("Uprawnienie do zmiany skina. Domyslnie to samo, ktorego uzywa SkinsRestorer.")
        public String permission = "skinsrestorer.command";

        @Comment("")
        @Comment("Odstep miedzy zmianami skina - kazda to zapytanie do Mojanga.")
        @CustomKey("cooldown-seconds")
        public int successCooldownSeconds = 30;

        @Comment("Krotszy odstep po nieudanej probie, zeby literowka nie blokowala na dlugo.")
        @CustomKey("error-cooldown-seconds")
        public int errorCooldownSeconds = 5;
    }

    public static class MessagingSection extends OkaeriConfig {

        @Comment("Komunikacja z instancjami Paper przez Redis.")
        @Comment("Wylaczenie nie wylacza szyny - proxy uzywa wtedy transportu w obrebie procesu,")
        @Comment("wiec plugin wstaje i dziala bez Redisa, tylko nie widzi innych instancji.")
        public boolean enabled = true;

        public RedisConfig redis = new RedisConfig();
    }
}
