package pl.landmc.proxy.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
    @Comment("Vouchery - kody wymieniane na nagrody. Uzywaja tej samej bazy co znajomi.")
    public VouchersSection vouchers = new VouchersSection();

    @Comment("")
    @Comment("Baza uzywana przez liste znajomych i vouchery. Sekcja pochodzi z landmc-platform.")
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

    @Comment("")
    public ReportSection report = new ReportSection();

    @Comment("")
    @CustomKey("motd")
    public MotdSection motd = new MotdSection();

    @Comment("")
    public MenusSection menus = new MenusSection();

    @Comment("")
    public LiveSection live = new LiveSection();

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

    /** Komenda /live - ogloszenie transmisji na cala siec. */
    public static class LiveSection extends OkaeriConfig {

        @Comment("Komenda /live. Wylaczona nie rejestruje sie wcale.")
        public boolean enabled = false;

        @Comment("")
        @Comment("Ile minut streamer musi odczekac miedzy ogloszeniami.")
        @Comment("Sprawdzenie w API mowi, KTO moze ogloszic - to mowi, JAK CZESTO. Bez tego")
        @Comment("nadajacy gracz moze powtarzac ogloszenie na cala siec bez konca.")
        @CustomKey("cooldown-minutes")
        public int cooldownMinutes = 30;

        @Comment("")
        @Comment("Tresc ogloszenia. Placeholdery: {PLAYER}, {PREFIX}, {URL}, {PLATFORM}")
        @CustomKey("broadcast-lines")
        public List<String> broadcastLines = new ArrayList<>(List.of(
                "",
                "<gradient:#aa0e00:#ff0800><bold>LIVE</bold></gradient> <gray>{PREFIX}<white>{PLAYER}</white> prowadzi transmisję na <white>{PLATFORM}</white>!",
                "<click:open_url:'{URL}'><hover:show_text:'<green>Otwórz transmisję'><green><bold>[DOŁĄCZ]</bold></green></hover></click>",
                ""));

        @Comment("")
        public PlatformCredentials twitch = new PlatformCredentials();

        @Comment("")
        public PlatformCredentials kick = new PlatformCredentials();
    }

    /**
     * Dane aplikacji OAuth platformy. Moga byc wpisane wprost albo jako ${ZMIENNA},
     * tak samo jak dane bazy - wtedy nie ma ich w pliku, ktory ktos wysyla na Discordzie.
     */
    public static class PlatformCredentials extends OkaeriConfig {

        @CustomKey("client-id")
        public String clientId = "";

        @CustomKey("client-secret")
        public String clientSecret = "";
    }

    /** Zgloszenia graczy - komenda /zglos i menu z powodami. */
    public static class ReportSection extends OkaeriConfig {

        @Comment("Komenda /zglos. Wylaczona nie rejestruje sie wcale.")
        public boolean enabled = true;

        @Comment("")
        @Comment("Uprawnienie do odbierania zgloszen. Na starym LandMC byla to ranga POMOCNIK.")
        @CustomKey("receive-permission")
        public String receivePermission = "landmc.report.receive";

        @Comment("")
        @Comment("Ile sekund miedzy zgloszeniami TEJ SAMEJ pary graczy.")
        @Comment("Na pare, nie na gracza: zgloszenie jednego nie moze blokowac zgloszenia")
        @Comment("drugiego, ktory w tej samej chwili robi cos innego.")
        @CustomKey("cooldown-seconds")
        public int cooldownSeconds = 30;

        @Comment("")
        @Comment("Powody, ich miejsce w menu i material kafelka. Lista ze starego LandMC.")
        public List<ReportReason> reasons = new ArrayList<>(List.of(
                reason("cheaty", "<red>CHEATY", "DIAMOND_SWORD", 22),
                reason("wulgarne", "<red>WULGARNE ZACHOWANIE", "PAPER", 37),
                reason("oszustwo", "<red>OSZUSTWO", "SUNFLOWER", 39),
                reason("sojusz", "<red>SOJUSZ", "PLAYER_HEAD", 41),
                reason("spam", "<red>SPAM/FLOOD", "PAPER", 43)));

        private static ReportReason reason(String id, String label, String material, int slot) {
            ReportReason reason = new ReportReason();
            reason.id = id;
            reason.label = label;
            reason.material = material;
            reason.slot = slot;
            return reason;
        }
    }

    /** One thing somebody can be reported for. */
    public static class ReportReason extends OkaeriConfig {

        @Comment("Identyfikator wysylany po klknieciu. Powod spoza tej listy jest odrzucany.")
        public String id = "";

        @Comment("Jak powod czyta sie w menu i w zgloszeniu.")
        public String label = "";

        @Comment("Material kafelka.")
        public String material = "PAPER";

        @Comment("Miejsce w menu.")
        public int slot = 0;
    }

    /** Co widac na liscie serwerow, zanim ktokolwiek sie polaczy. */
    public static class MotdSection extends OkaeriConfig {

        @Comment("Czy proxy przepisuje ping z listy serwerow.")
        @Comment("Wylaczone = zostaje to, co mowi velocity.toml.")
        public boolean enabled = true;

        @Comment("")
        @Comment("Dwie linie opisu. Placeholdery: {ONLINE}, {MAX}")
        public List<String> lines = new ArrayList<>(List.of(
                "<gradient:#00FF37:#95ff00><bold>LandMC</bold></gradient>"
                        + " <dark_gray>» <gray>Sieć serwerów Minecraft",
                "<green>SkyBlock <dark_gray>| <gray>Online: <white>{ONLINE}<gray>/<white>{MAX}"));

        @Comment("")
        @Comment("To samo, gdy wlaczony jest tryb techniczny. To jest wlasciwy powod,")
        @Comment("dla ktorego to istnieje: przerwa, o ktorej gracz dowiaduje sie dopiero")
        @Comment("po rozlaczeniu, to przerwa, o ktorej kazdy dowiaduje sie na twardo.")
        @CustomKey("maintenance-lines")
        public List<String> maintenanceLines = new ArrayList<>(List.of(
                "<red><bold>PRZERWA TECHNICZNA",
                "<gray>Wrócimy niedługo. <dark_gray>» <white>landmc.pl"));

        @Comment("")
        @Comment("Lista pod kursorem. Pusta = zostaje to, co wysyla proxy.")
        public List<String> hover = new ArrayList<>(List.of(
                "<gradient:#00FF37:#95ff00><bold>LandMC</bold></gradient>",
                "<dark_gray>»",
                "<gray>Dołącz: <white>landmc.pl"));

        @CustomKey("maintenance-hover")
        public List<String> maintenanceHover = new ArrayList<>(List.of(
                "<red><bold>PRZERWA TECHNICZNA",
                "<dark_gray>»",
                "<gray>Serwer wróci niedługo."));

        @Comment("")
        @Comment("Maksymalna liczba graczy pokazywana na liscie. 0 = to, co velocity.toml.")
        @CustomKey("max-players")
        public int maxPlayers = 0;

        @Comment("")
        @Comment("Napis w miejscu wersji podczas przerwy. Puste = bez zmiany.")
        @Comment("Klient nie dopasuje protokolu i pokaze go na czerwono - o to chodzi.")
        @CustomKey("maintenance-version")
        public String maintenanceVersion = "Przerwa techniczna";

        @Comment("")
        @Comment("Ikona obok nazwy - plik PNG 64x64 w katalogu TEGO PLUGINU.")
        @Comment("Brak pliku to normalny przypadek i zwykle wlasciwy: wtedy zostaje ikona,")
        @Comment("ktora wysyla samo Velocity, czyli server-icon.png z katalogu proxy.")
        @Comment("Ten wpis sluzy tylko do nadpisania jej czyms innym.")
        @CustomKey("icon-file")
        public String iconFile = "server-icon.png";
    }

    /** Menu rysowane na backendzie, wypelniane danymi stad. */
    public static class MenusSection extends OkaeriConfig {

        @Comment("Komenda /serwery - lista serwerow w GUI.")
        @CustomKey("servers-enabled")
        public boolean serversEnabled = true;

        @Comment("")
        @Comment("Ktore serwery pokazac i jak ma wygladac kazdy kafelek.")
        @Comment("Wpisane recznie, bo lista z velocity.toml zawiera tez limbo i wszystko,")
        @Comment("czego gracz nie ma wybierac. Serwer spoza tej listy nie pojawi sie w menu,")
        @Comment("nawet jesli istnieje.")
        @Comment("Slot, material i lore sa tutaj, a nie w configu menu na backendzie, bo tutaj")
        @Comment("jest lista serwerow - dolozenie trybu to jeden wpis w jednym miejscu.")
        @Comment("W lore dziala {ONLINE}.")
        public List<MenuServer> servers = new ArrayList<>(List.of(skyblockDefaults()));

        @Comment("")
        @Comment("Podserwery - instancje lobby, miedzy ktorymi gracz moze sie przelaczac.")
        @Comment("To co innego niz lista wyzej: tam sa serwery gry, tu kopie huba.")
        @Comment("Dopoki jest jedno lobby, menu pokaze jedna pozycje i tak ma byc.")
        @CustomKey("lobbies-enabled")
        public boolean lobbiesEnabled = true;

        @CustomKey("lobbies")
        public List<MenuServer> lobbies = new ArrayList<>(List.of(hubDefaults()));

        @Comment("")
        @Comment("Ile milisekund czekac na polaczenie z serwerem, zanim menu oznaczy go jako")
        @Comment("niedostepny. Menu otwiera sie po tym czasie, wiec dlugi timeout to dlugie")
        @Comment("czekanie na komende, ktora gracz wlasnie wpisal.")
        @Comment("Sprawdzamy otwarciem gniazda, nie pingiem Minecrafta: backend ma")
        @Comment("enable-status=false, wiec na ping nie odpowiada i wyszedlby jako martwy.")
        @CustomKey("reachability-timeout-millis")
        public long reachabilityTimeoutMillis = 1_500L;

        @Comment("")
        @Comment("Co ile sekund sprawdzac dostepnosc serwerow. Sprawdzane w tle, wiec menu")
        @Comment("otwiera sie natychmiast, a backendy sa odpytywane stala liczbe razy na minute")
        @Comment("niezaleznie od tego, ilu graczy otworzy menu.")
        @CustomKey("health-interval-seconds")
        public int healthIntervalSeconds = 15;

        /** SkyBlock stood in the middle of the old menu as a block of grass. */
        private static MenuServer skyblockDefaults() {
            MenuServer server = new MenuServer();
            server.id = "skyblock";
            server.slot = 22;
            server.material = "GRASS_BLOCK";
            server.name = "<green>SkyBlock";
            server.lore = new ArrayList<>(List.of(
                    "<gray>Stwórz własną wyspę, a następnie postaw wspaniałe budowle.",
                    "<gray>Wykonuj zadania, osiągnięcia. Sprzedawaj zdobyte przedmioty, ...",
                    "<gray>... a następnie zdobądź top 10 wysp!",
                    "",
                    "<white>Online: <green>{ONLINE}",
                    "",
                    "<yellow>Kliknij, aby przejść na ten serwer."));
            return server;
        }

        /** A hub, drawn as a dye the way the old lobby list drew them. */
        private static MenuServer hubDefaults() {
            MenuServer server = new MenuServer();
            server.id = "lobby";
            server.slot = 0;
            server.material = "LIME_DYE";
            server.name = "<green>Lobby #1";
            server.lore = new ArrayList<>(List.of(
                    "",
                    "<white>Online: <green>{ONLINE}",
                    "",
                    "<yellow>Kliknij, aby zmienić podserwer."));
            return server;
        }
    }

    /**
     * One tile in a server menu.
     *
     * <p>Everything about how it looks travels to the backend that draws it, so a new mode is
     * one entry here rather than an entry here and a matching one in every backend's messages.
     */
    public static class MenuServer extends OkaeriConfig {

        @Comment("Nazwa serwera z velocity.toml.")
        public String id = "";

        @Comment("Miejsce w menu, liczone od lewego gornego rogu.")
        public int slot = 0;

        @Comment("Material kafelka.")
        public String material = "PAPER";

        @Comment("Nazwa kafelka, z wlasnym kolorem.")
        public String name = "";

        @Comment("Linie pod nazwa. {ONLINE} to liczba graczy.")
        public List<String> lore = new ArrayList<>();
    }

    public static class VouchersSection extends OkaeriConfig {

        @Comment("Komendy /voucher i /generujvoucher.")
        public boolean enabled = false;

        @Comment("")
        @Comment("Odstep miedzy probami wpisania kodu. Bez niego komenda jest wyrocznia:")
        @Comment("kody mozna zgadywac tak szybko, jak pozwoli lacze.")
        @CustomKey("cooldown-seconds")
        public int cooldownSeconds = 30;

        @Comment("")
        @Comment("Rodzaje voucherow. Nazwa jest tym, co podaje sie w /generujvoucher,")
        @Comment("a 'commands' to komendy wykonywane z konsoli proxy po odebraniu.")
        @Comment("W komendach dziala {PLAYER}. Dodanie nagrody to zmiana tego pliku,")
        @Comment("a nie nowa wersja pluginu.")
        public Map<String, VoucherReward> types = new LinkedHashMap<>(Map.of(
                "vip7", new VoucherReward(
                        "Ranga VIP na 7 dni",
                        List.of("lp user {PLAYER} parent addtemp vip 7d accumulate")),
                "vip30", new VoucherReward(
                        "Ranga VIP na 30 dni",
                        List.of("lp user {PLAYER} parent addtemp vip 30d accumulate"))));
    }

    public static class VoucherReward extends OkaeriConfig {

        @Comment("Nazwa pokazywana graczowi po odebraniu.")
        public String name = "";

        @Comment("Komendy wykonywane z konsoli proxy. Placeholder: {PLAYER}")
        public List<String> commands = new ArrayList<>();

        public VoucherReward() {
        }

        public VoucherReward(String name, List<String> commands) {
            this.name = name;
            this.commands = new ArrayList<>(commands);
        }
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
