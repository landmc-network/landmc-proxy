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
}
