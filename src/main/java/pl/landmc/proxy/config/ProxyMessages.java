package pl.landmc.proxy.config;

import com.eternalcode.multification.notice.Notice;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import pl.landmc.platform.config.message.PlatformMessagesConfig;

/**
 * {@code messages.yml} - the proxy's own messages, with the platform's technical ones embedded
 * as a section.
 *
 * <p>The split follows the platform's rule: {@code command-no-permission} and friends are
 * framework vocabulary and live in {@code platform}, while everything about maintenance and
 * moving players between servers is this project's domain and lives here.
 *
 * <p>Two kinds of text, for a reason that is not cosmetic:
 *
 * <ul>
 *   <li>{@link Notice} for anything sent to a connected player. A notice can be configured as
 *       chat, an action bar, a title or a sound without touching code, and its placeholders are
 *       Multification's {@code {BRACES}}.</li>
 *   <li>plain MiniMessage strings for kick screens. A disconnect screen is a single component
 *       handed to Velocity, so it cannot be a notice - there is no player left to send an
 *       action bar to.</li>
 * </ul>
 */
public class ProxyMessages extends OkaeriConfig {

    @Comment("Komunikaty techniczne wspolne dla calej sieci - dostarcza je landmc-platform.")
    @Comment("Zmiana klucza 'prefix' przestawia wyglad wszystkich naraz.")
    public PlatformMessagesConfig platform = new PlatformMessagesConfig();

    @Comment("")
    @Comment("Ekran rozlaczenia przy wlaczonym trybie serwisowym.")
    @Comment("To nie jest Notice: gracz nie jest jeszcze w sieci, wiec da sie pokazac tylko ekran.")
    @CustomKey("maintenance-kick")
    public String maintenanceKick =
            "<red><bold>Przerwa techniczna</bold></red>"
                    + "<newline><newline><gray>Sieć LandMC jest chwilowo niedostępna."
                    + "<newline><gray>Spróbuj ponownie za kilka minut.";

    @Comment("")
    @Comment("Ekran rozlaczenia, gdy nie ma dokad przeniesc gracza.")
    @CustomKey("no-fallback-kick")
    public String noFallbackKick =
            "<red><bold>Brak dostępnego serwera</bold></red>"
                    + "<newline><newline><gray>Żaden serwer sieci nie jest teraz osiągalny."
                    + "<newline><gray>Spróbuj ponownie za chwilę.";

    @Comment("")
    @Comment("Komunikaty domenowe proxy. Placeholdery Multification: {NAZWA}.")
    @Comment("Kazdy z nich mozna zamienic na title/actionbar/dzwiek bez zmiany kodu.")
    @CustomKey("server-not-found")
    public Notice serverNotFound = Notice.chat("<red>Błąd> <gray>Nie znaleziono serwera <white>{SERVER}</white>.");

    @Comment("Placeholder: {SERVER}")
    @CustomKey("server-unavailable")
    public Notice serverUnavailable =
            Notice.chat("<red>Błąd> <gray>Serwer <white>{SERVER}</white> jest teraz niedostępny.");

    @Comment("Placeholder: {SERVER}")
    @CustomKey("already-connected")
    public Notice alreadyConnected = Notice.chat("<red>Błąd> <gray>Jesteś już na serwerze <white>{SERVER}</white>.");

    @Comment("Placeholder: {SERVER}")
    @CustomKey("connecting")
    public Notice connecting = Notice.chat("<green>Sieć> <gray>Łączenie z <white>{SERVER}</white>...");

    @Comment("Placeholder: {SERVER}")
    @CustomKey("transfer-failed")
    public Notice transferFailed =
            Notice.chat("<red>Błąd> <gray>Nie udało się połączyć z <white>{SERVER}</white>.");

    @Comment("")
    @Comment("Placeholder: {SERVERS} - lista rozdzielona przecinkami")
    @CustomKey("server-list")
    public Notice serverList = Notice.chat("<green>Sieć> <gray>Dostępne serwery: <white>{SERVERS}</white>");

    @Comment("")
    @CustomKey("maintenance-enabled")
    public Notice maintenanceEnabled = Notice.chat("<green>Sieć> <gray>Tryb serwisowy <white>włączony</white>.");

    @CustomKey("maintenance-disabled")
    public Notice maintenanceDisabled = Notice.chat("<green>Sieć> <gray>Tryb serwisowy <white>wyłączony</white>.");

    @Comment("Placeholder: {STATE} - wlaczony / wylaczony")
    @CustomKey("maintenance-status")
    public Notice maintenanceStatus = Notice.chat("<green>Sieć> <gray>Tryb serwisowy: <white>{STATE}</white>.");

    @Comment("")
    @Comment("Placeholder: {PLAYER} - uzywane przez /send")
    @CustomKey("player-not-found")
    public Notice playerNotFound = Notice.chat("<red>Błąd> <gray>Nie znaleziono gracza <white>{PLAYER}</white>.");

    @Comment("Placeholdery: {PLAYER}, {SERVER}")
    @CustomKey("send-success")
    public Notice sendSuccess =
            Notice.chat("<green>Sieć> <gray>Przeniesiono <white>{PLAYER}</white> na <white>{SERVER}</white>.");

    @Comment("Placeholdery: {PLAYER}, {SERVER}")
    @CustomKey("send-failed")
    public Notice sendFailed =
            Notice.chat("<red>Błąd> <gray>Nie udało się przenieść <white>{PLAYER}</white> na <white>{SERVER}</white>.");

    @Comment("Placeholdery: {SERVER}, {COUNT}")
    @CustomKey("send-success-all")
    public Notice sendSuccessAll =
            Notice.chat("<green>Sieć> <gray>Przeniesiono <white>{COUNT}</white> graczy na <white>{SERVER}</white>.");

    @Comment("")
    @Comment("Zgloszenie /helpop widziane przez ekipe. Placeholdery: {PLAYER}, {SERVER}, {MESSAGE}")
    @CustomKey("helpop-report")
    public Notice helpOpReport = Notice.chat(
            "<gold>HelpOp> <white>{PLAYER}</white> <dark_gray>(<gray>{SERVER}<dark_gray>)<gray>: {MESSAGE}");

    @Comment("Potwierdzenie dla zglaszajacego")
    @CustomKey("helpop-sent")
    public Notice helpOpSent =
            Notice.chat("<green>Sieć> <gray>Zgłoszenie wysłane. Ekipa odpowie, gdy będzie dostępna.");

    @Comment("")
    @Comment("Czat ekipy. Placeholdery: {PLAYER}, {PREFIX}, {MESSAGE}")
    @CustomKey("adminchat-format")
    public Notice adminChatFormat =
            Notice.chat("<dark_red>AC> {PREFIX}<white>{PLAYER}</white><dark_gray>: <gray>{MESSAGE}");

    @Comment("")
    @Comment("Wiadomosci prywatne. Placeholdery: {SENDER}, {RECEIVER}, {MESSAGE}")
    @CustomKey("message-outgoing")
    public Notice messageOutgoing =
            Notice.chat("<dark_gray>[<gray>ja <dark_gray>-> <white>{RECEIVER}<dark_gray>] <gray>{MESSAGE}");

    @CustomKey("message-incoming")
    public Notice messageIncoming =
            Notice.chat("<dark_gray>[<white>{SENDER} <dark_gray>-> <gray>ja<dark_gray>] <gray>{MESSAGE}");

    @Comment("Placeholder: {PLAYER}")
    @CustomKey("message-receiver-disabled")
    public Notice messageReceiverDisabled =
            Notice.chat("<red>Błąd> <gray>Gracz <white>{PLAYER}</white> ma wyłączone wiadomości prywatne.");

    @CustomKey("message-to-self")
    public Notice messageToSelf = Notice.chat("<red>Błąd> <gray>Nie napiszesz wiadomości do samego siebie.");

    @CustomKey("reply-no-target")
    public Notice replyNoTarget = Notice.chat("<red>Błąd> <gray>Nie masz komu odpowiedzieć.");

    @CustomKey("reply-target-offline")
    public Notice replyTargetOffline = Notice.chat("<red>Błąd> <gray>Ten gracz jest już offline.");

    @CustomKey("messages-enabled")
    public Notice messagesEnabled = Notice.chat("<green>Sieć> <gray>Wiadomości prywatne <white>włączone</white>.");

    @CustomKey("messages-disabled")
    public Notice messagesDisabled = Notice.chat("<green>Sieć> <gray>Wiadomości prywatne <white>wyłączone</white>.");

    @Comment("")
    @Comment("SocialSpy. Placeholdery: {SENDER}, {RECEIVER}, {MESSAGE}")
    @CustomKey("social-spy-format")
    public Notice socialSpyFormat =
            Notice.chat("<dark_gray>[SS] <white>{SENDER} <dark_gray>-> <white>{RECEIVER}<dark_gray>: <gray>{MESSAGE}");

    @CustomKey("social-spy-enabled")
    public Notice socialSpyEnabled = Notice.chat("<green>Sieć> <gray>SocialSpy <white>włączony</white>.");

    @CustomKey("social-spy-disabled")
    public Notice socialSpyDisabled = Notice.chat("<green>Sieć> <gray>SocialSpy <white>wyłączony</white>.");

    @Comment("")
    @Comment("Ignorowanie. Placeholdery: {PLAYER}, {PLAYERS}")
    @CustomKey("ignore-added")
    public Notice ignoreAdded = Notice.chat("<green>Sieć> <gray>Ignorujesz gracza <white>{PLAYER}</white>.");

    @CustomKey("ignore-removed")
    public Notice ignoreRemoved =
            Notice.chat("<green>Sieć> <gray>Nie ignorujesz już gracza <white>{PLAYER}</white>.");

    @CustomKey("ignore-self")
    public Notice ignoreSelf = Notice.chat("<red>Błąd> <gray>Nie zignorujesz samego siebie.");

    @CustomKey("ignore-list")
    public Notice ignoreList = Notice.chat("<green>Sieć> <gray>Ignorujesz: <white>{PLAYERS}</white>");

    @CustomKey("ignore-list-empty")
    public Notice ignoreListEmpty = Notice.chat("<green>Sieć> <gray>Nikogo nie ignorujesz.");

    @Comment("")
    @Comment("Cooldown komend - wysylane tylko przy enforce-commands-on-proxy. Placeholder: {TIME}")
    @CustomKey("cooldown-command-blocked")
    public Notice cooldownCommandBlocked =
            Notice.chat("<red>Zwolnij! <gray>Poczekaj jeszcze <white>{TIME}</white>.");

    @Comment("")
    @Comment("Cooldown GUI. Placeholder: {TIME}")
    @CustomKey("cooldown-gui-blocked")
    public Notice cooldownGuiBlocked = Notice.chat("<red>Zwolnij! <gray>Poczekaj jeszcze <white>{TIME}</white>.");

    @Comment("")
    @Comment("Placeholder: {SERVER}, {TIME} - odpowiedz na /testmessage")
    @CustomKey("messaging-pong")
    public Notice messagingPong =
            Notice.chat("<green>Sieć> <gray>Odpowiedź z <white>{SERVER}</white> po <white>{TIME}</white>.");

    @Comment("Placeholder: {SERVER}, {REASON}")
    @CustomKey("messaging-failed")
    public Notice messagingFailed =
            Notice.chat("<red>Błąd> <gray>Brak odpowiedzi z <white>{SERVER}</white>: <white>{REASON}</white>");

    @Comment("Wysylane, gdy messaging jest wylaczony w config.yml")
    @CustomKey("messaging-disabled")
    public Notice messagingDisabled =
            Notice.chat("<red>Błąd> <gray>Komunikacja sieciowa jest wyłączona w konfiguracji.");

    @Comment("")
    @Comment("Lista znajomych. Placeholdery: {PLAYER}, {PLAYERS}, {COUNT}, {ONLINE}, {OFFLINE}, {LIMIT}")
    @CustomKey("friend-request-sent")
    public Notice friendRequestSent =
            Notice.chat("<green>Znajomi> <gray>Wysłano zaproszenie do <white>{PLAYER}</white>.");

    @CustomKey("friend-request-received")
    public Notice friendRequestReceived = Notice.chat(
            "<green>Znajomi> <gray>Zaproszenie od <white>{PLAYER}</white>."
                    + "<newline><gray>Przyjmij: <white>/friend akceptuj {PLAYER}</white>");

    @CustomKey("friend-request-already-sent")
    public Notice friendRequestAlreadySent =
            Notice.chat("<red>Błąd> <gray>Zaproszenie do <white>{PLAYER}</white> już czeka.");

    @Comment("Gdy oboje zaprosili sie nawzajem - drugie zaproszenie od razu akceptuje pierwsze.")
    @CustomKey("friend-request-accepted-instead")
    public Notice friendRequestAcceptedInstead = Notice.chat(
            "<green>Znajomi> <gray>Gracz <white>{PLAYER}</white> zaprosił Cię wcześniej"
                    + " - jesteście teraz znajomymi.");

    @CustomKey("friend-request-accepted")
    public Notice friendRequestAccepted =
            Notice.chat("<green>Znajomi> <gray>Jesteś teraz znajomym gracza <white>{PLAYER}</white>.");

    @CustomKey("friend-request-accepted-by-other")
    public Notice friendRequestAcceptedByOther =
            Notice.chat("<green>Znajomi> <gray>Gracz <white>{PLAYER}</white> przyjął Twoje zaproszenie.");

    @CustomKey("friend-request-declined")
    public Notice friendRequestDeclined =
            Notice.chat("<green>Znajomi> <gray>Odrzucono zaproszenie od <white>{PLAYER}</white>.");

    @CustomKey("friend-no-request")
    public Notice friendNoRequest =
            Notice.chat("<red>Błąd> <gray>Nie masz zaproszenia od gracza <white>{PLAYER}</white>.");

    @CustomKey("friend-already-friends")
    public Notice friendAlreadyFriends =
            Notice.chat("<red>Błąd> <gray>Gracz <white>{PLAYER}</white> jest już Twoim znajomym.");

    @CustomKey("friend-self")
    public Notice friendSelf = Notice.chat("<red>Błąd> <gray>Nie zaprosisz samego siebie.");

    @CustomKey("friend-unknown-player")
    public Notice friendUnknownPlayer =
            Notice.chat("<red>Błąd> <gray>Gracz <white>{PLAYER}</white> nigdy nie był na sieci.");

    @CustomKey("friend-list-full")
    public Notice friendListFull =
            Notice.chat("<red>Błąd> <gray>Masz już maksymalną liczbę znajomych (<white>{LIMIT}</white>).");

    @CustomKey("friend-other-list-full")
    public Notice friendOtherListFull =
            Notice.chat("<red>Błąd> <gray>Gracz <white>{PLAYER}</white> ma już pełną listę znajomych.");

    @CustomKey("friend-removed")
    public Notice friendRemoved =
            Notice.chat("<green>Znajomi> <gray>Usunięto <white>{PLAYER}</white> ze znajomych.");

    @CustomKey("friend-removed-you")
    public Notice friendRemovedYou =
            Notice.chat("<green>Znajomi> <gray>Gracz <white>{PLAYER}</white> usunął Cię ze znajomych.");

    @CustomKey("friend-not-on-list")
    public Notice friendNotOnList =
            Notice.chat("<red>Błąd> <gray>Gracz <white>{PLAYER}</white> nie jest Twoim znajomym.");

    @CustomKey("friend-list")
    public Notice friendList = Notice.chat(
            "<green>Znajomi</green> <gray>(<white>{COUNT}</white>)"
                    + "<newline><gray>Online: <green>{ONLINE}"
                    + "<newline><gray>Offline: <dark_gray>{OFFLINE}");

    @CustomKey("friend-list-empty")
    public Notice friendListEmpty = Notice.chat("<gray>Nie masz jeszcze znajomych.");

    @CustomKey("friend-pending-requests")
    public Notice friendPendingRequests =
            Notice.chat("<green>Znajomi> <gray>Zaproszenia od: <white>{PLAYERS}</white>");

    @CustomKey("friend-no-pending-requests")
    public Notice friendNoPendingRequests = Notice.chat("<gray>Nie masz oczekujących zaproszeń.");

    @Comment("Awaria bazy - szczegoly ladują w konsoli, nie u gracza.")
    @CustomKey("friend-failed")
    public Notice friendFailed =
            Notice.chat("<red>Błąd> <gray>Lista znajomych jest chwilowo niedostępna.");

    @Comment("")
    @Comment("Nadawanie rang. Placeholdery: {RANK}, {PLAYER}, {TIME}")
    @CustomKey("rank-assigned")
    public Notice rankAssigned =
            Notice.chat("<green>Sieć> <gray>Gracz <white>{PLAYER}</white> ma teraz rangę <white>{RANK}</white>.");

    @CustomKey("rank-assigned-temporarily")
    public Notice rankAssignedTemporarily = Notice.chat(
            "<green>Sieć> <gray>Gracz <white>{PLAYER}</white> ma rangę <white>{RANK}</white>"
                    + " przez <white>{TIME}</white>.");

    @CustomKey("rank-group-not-found")
    public Notice rankGroupNotFound = Notice.chat("<red>Błąd> <gray>Taka ranga nie istnieje.");

    @Comment("Placeholder: {PLAYER}")
    @CustomKey("rank-player-not-found")
    public Notice rankPlayerNotFound =
            Notice.chat("<red>Błąd> <gray>Nie znaleziono gracza <white>{PLAYER}</white>.");

    @Comment("Gdy LuckPerms zniknal juz po starcie proxy.")
    @CustomKey("rank-unavailable")
    public Notice rankUnavailable = Notice.chat("<red>Błąd> <gray>Zarządzanie rangami jest niedostępne.");

    @CustomKey("rank-failed")
    public Notice rankFailed = Notice.chat("<red>Błąd> <gray>Nie udało się zapisać rangi. Sprawdź konsolę.");

    @Comment("")
    @Comment("Komenda /skin. Placeholdery: {SKIN}, {SECONDS}")
    @CustomKey("skin-loading")
    public Notice skinLoading = Notice.chat("<green>Sieć> <gray>Pobieram skin gracza <white>{SKIN}</white>...");

    @CustomKey("skin-applied")
    public Notice skinApplied = Notice.chat("<green>Sieć> <gray>Ustawiono skin gracza <white>{SKIN}</white>.");

    @CustomKey("skin-not-found")
    public Notice skinNotFound =
            Notice.chat("<red>Błąd> <gray>Nie ma konta premium o nicku <white>{SKIN}</white>.");

    @CustomKey("skin-failed")
    public Notice skinFailed = Notice.chat("<red>Błąd> <gray>Nie udało się pobrać skina. Spróbuj później.");

    @CustomKey("skin-invalid-name")
    public Notice skinInvalidName =
            Notice.chat("<red>Błąd> <gray>Nick skina to od 3 do 16 znaków: litery, cyfry lub podkreślnik.");

    @CustomKey("skin-cooldown")
    public Notice skinCooldown =
            Notice.chat("<red>Błąd> <gray>Poczekaj jeszcze <white>{SECONDS}</white> s przed kolejną zmianą.");

    @CustomKey("skin-no-permission")
    public Notice skinNoPermission = Notice.chat("<red>Błąd> <gray>Nie możesz zmieniać skina.");
}
