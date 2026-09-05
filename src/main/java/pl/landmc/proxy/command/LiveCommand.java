package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.live.LiveRepository;
import pl.landmc.proxy.live.LiveService;
import pl.landmc.proxy.live.StreamProfile;
import pl.landmc.proxy.live.StreamStatus;
import pl.landmc.proxy.rank.RankProvider;

/**
 * {@code /live} - a streamer announces their stream to the whole network.
 *
 * <p>The announcement is the whole feature, and everything else here exists to make sure it is
 * not worth abusing. A player cannot supply the link - staff register it - so the command cannot
 * be pointed at an arbitrary URL. The platform is asked whether the stream is really on, so it
 * cannot be used to advertise nothing. And a cooldown limits how often, because being genuinely
 * live is not a reason to repeat the same message to everybody every thirty seconds.
 */
@Command(name = "live")
@Permission("landmc.command.live")
public final class LiveCommand {

    private final LiveService live;
    private final ProxyServer proxy;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final ComponentFormatter formatter;
    private final ProxyConfig config;
    private final RankProvider ranks;
    private final Logger logger;

    public LiveCommand(
            LiveService live,
            ProxyServer proxy,
            VelocityNoticeService<ProxyMessages> notices,
            ComponentFormatter formatter,
            ProxyConfig config,
            RankProvider ranks,
            Logger logger) {

        this.live = Objects.requireNonNull(live, "live");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.config = Objects.requireNonNull(config, "config");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Bare {@code /live}: check, then announce. */
    @Execute
    void announce(@Context Player player) {
        long remaining = this.live.remainingCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            this.notices.create()
                    .viewer(player)
                    .notice(messages -> messages.liveCooldown)
                    .formatter(new Formatter().register("{TIME}", describe(remaining)))
                    .send();
            return;
        }

        this.live.profile(player.getUsername())
                .thenAccept(profile -> {
                    if (profile.isEmpty()) {
                        this.notice(player, messages -> messages.liveNoProfile);
                        return;
                    }
                    this.checkThenAnnounce(player, profile.get());
                })
                .exceptionally(this.report(player, "live profile"));
    }

    private void checkThenAnnounce(Player player, StreamProfile profile) {
        // A platform nothing can ask about - TikTok - is announced on the strength of staff
        // having registered the profile at all. That is the trust decision, and it is made once
        // when the profile is added rather than every time the command is run.
        if (!profile.platform().isVerifiable()) {
            this.broadcast(player, profile);
            return;
        }

        this.live.status(profile).thenAccept(status -> {
            switch (status) {
                case LIVE -> this.broadcast(player, profile);
                case OFFLINE -> this.notices.create()
                        .viewer(player)
                        .notice(messages -> messages.liveNotLive)
                        .formatter(new Formatter()
                                .register("{PLATFORM}", profile.platform().displayName()))
                        .send();
                case UNKNOWN -> this.notices.create()
                        .viewer(player)
                        .notice(messages -> messages.liveCheckFailed)
                        .formatter(new Formatter()
                                .register("{PLATFORM}", profile.platform().displayName()))
                        .send();
            }
        });
    }

    /**
     * Sends the announcement to everybody, and starts the cooldown.
     *
     * <p>The cooldown starts here rather than when the command was typed: a check that came back
     * offline has cost nothing, and making somebody wait half an hour for a command that did not
     * work would be a penalty for a failure that was not theirs.
     */
    private void broadcast(Player player, StreamProfile profile) {
        this.live.startCooldown(player.getUniqueId());

        String prefix = this.ranks.prefixOf(player);
        List<Component> lines = this.config.live.broadcastLines.stream()
                .map(template -> this.formatter.format(template
                        .replace("{PLAYER}", player.getUsername())
                        .replace("{PREFIX}", prefix)
                        .replace("{PLATFORM}", profile.platform().displayName())
                        .replace("{URL}", profile.url())))
                .toList();

        for (Player viewer : this.proxy.getAllPlayers()) {
            lines.forEach(viewer::sendMessage);
        }
        lines.forEach(this.proxy.getConsoleCommandSource()::sendMessage);

        this.logger.info(
                "{} announced a {} stream ({}).",
                player.getUsername(), profile.platform().displayName(), profile.url());
    }

    /** {@code /live dodaj <gracz> <login lub link>} */
    @Execute(name = "dodaj", aliases = {"add", "ustaw", "set"})
    @Permission("landmc.command.live.admin")
    void add(
            @Context CommandSource sender,
            @Arg("gracz") String playerName,
            @Arg("login lub link") String link) {

        StreamProfile profile = StreamProfile.parse(link).orElse(null);
        if (profile == null) {
            this.notice(sender, messages -> messages.liveInvalidLink);
            return;
        }

        this.live.register(playerName, profile, describe(sender))
                .thenRun(() -> {
                    this.logger.info(
                            "{} registered {} for {} ({}).",
                            describe(sender), profile.platform().displayName(),
                            playerName, profile.url());

                    this.notices.create()
                            .viewer(sender)
                            .notice(messages -> messages.liveProfileSaved)
                            .formatter(new Formatter()
                                    .register("{PLAYER}", playerName)
                                    .register("{PLATFORM}", profile.platform().displayName())
                                    .register("{URL}", profile.url()))
                            .send();
                })
                .exceptionally(this.report(sender, "live add " + playerName));
    }

    /** {@code /live usun <gracz>} */
    @Execute(name = "usun", aliases = {"usuń", "remove", "delete"})
    @Permission("landmc.command.live.admin")
    void remove(@Context CommandSource sender, @Arg("gracz") String playerName) {
        this.live.unregister(playerName)
                .thenAccept(removed -> this.notices.create()
                        .viewer(sender)
                        .notice(messages -> removed
                                ? messages.liveProfileRemoved
                                : messages.liveProfileMissing)
                        .formatter(new Formatter().register("{PLAYER}", playerName))
                        .send())
                .exceptionally(this.report(sender, "live remove " + playerName));
    }

    /** {@code /live lista} */
    @Execute(name = "lista", aliases = "list")
    @Permission("landmc.command.live.admin")
    void list(@Context CommandSource sender) {
        this.live.list()
                .thenAccept(entries -> {
                    if (entries.isEmpty()) {
                        this.notice(sender, messages -> messages.liveListEmpty);
                        return;
                    }

                    this.notices.create()
                            .viewer(sender)
                            .notice(messages -> messages.liveListHeader)
                            .formatter(new Formatter()
                                    .register("{COUNT}", Integer.toString(entries.size())))
                            .send();

                    for (LiveRepository.Entry entry : entries) {
                        this.notices.create()
                                .viewer(sender)
                                .notice(messages -> messages.liveListEntry)
                                .formatter(new Formatter()
                                        .register("{PLAYER}", entry.playerName())
                                        .register("{PLATFORM}", entry.profile().platform().displayName())
                                        .register("{URL}", entry.profile().url()))
                                .send();
                    }
                })
                .exceptionally(this.report(sender, "live list"));
    }

    private void notice(
            CommandSource viewer,
            com.eternalcode.multification.notice.provider.NoticeProvider<ProxyMessages> which) {
        this.notices.create().viewer(viewer).notice(which).send();
    }

    private Function<Throwable, Void> report(CommandSource sender, String what) {
        return throwable -> {
            this.logger.error("Live command failed ({})", what, throwable);
            this.notice(sender, messages -> messages.liveFailed);
            return null;
        };
    }

    private static String describe(CommandSource sender) {
        return sender instanceof Player player ? player.getUsername() : "Konsola";
    }

    /** Seconds as something a person reads: "12 min" rather than "743". */
    private static String describe(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = (seconds + 59) / 60;
        return minutes + " min";
    }
}
