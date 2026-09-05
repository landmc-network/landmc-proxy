package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.argument.Key;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import pl.landmc.platform.proxy.command.VelocityCommands;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.rank.RankProvider;

/**
 * {@code /setrank <gracz> <ranga> [czas]} - puts a player in a LuckPerms group.
 *
 * <p>Registered only when LuckPerms is installed. The original built the command
 * unconditionally and called {@code LuckPermsProvider.get()} in its constructor, so a proxy
 * without LuckPerms failed to load the plugin at all; here a missing LuckPerms simply means the
 * command is not there.
 *
 * <p>The duration is parsed by LiteCommands, so {@code 30d}, {@code 12h} and {@code 90m} all
 * work and a malformed value is refused before any storage is touched. The original parsed it
 * by hand and answered a bad value with the usage line, which read as though the command itself
 * were wrong.
 *
 * <p>Nothing here blocks: LuckPerms is asked on its own threads and the sender is answered when
 * the write completes.
 */
@Command(name = "setrank", aliases = "rank")
@Permission("landmc.command.setrank")
public class RankCommand {

    private final ProxyServer proxy;
    private final RankProvider ranks;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final Logger logger;

    public RankCommand(
            ProxyServer proxy,
            RankProvider ranks,
            VelocityNoticeService<ProxyMessages> notices,
            Logger logger) {

        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Execute
    void permanently(
            @Context CommandSource sender,
            @Key(VelocityCommands.PLAYER) @Arg("gracz") String target,
            @Arg("ranga") String group) {

        this.assign(sender, target, group, null, "");
    }

    @Execute
    void temporarily(
            @Context CommandSource sender,
            @Key(VelocityCommands.PLAYER) @Arg("gracz") String target,
            @Arg("ranga") String group,
            @Arg("czas") Duration duration) {

        this.assign(sender, target, group, duration, format(duration));
    }

    private void assign(
            CommandSource sender,
            String target,
            String group,
            @Nullable Duration duration,
            String formattedTime) {

        this.ranks.assign(this.proxy, target, group, duration)
                .thenAccept(result -> this.reply(sender, result, target, formattedTime))
                .exceptionally(throwable -> {
                    this.logger.error("Could not set rank {} for {}", group, target, throwable);
                    this.notices.create().viewer(sender).notice(messages -> messages.rankFailed).send();
                    return null;
                });
    }

    private void reply(
            CommandSource sender,
            RankProvider.RankAssignment result,
            String target,
            String formattedTime) {

        switch (result.outcome()) {
            case UNAVAILABLE -> this.notices.create()
                    .viewer(sender)
                    .notice(messages -> messages.rankUnavailable)
                    .send();
            case NO_SUCH_GROUP -> this.notices.create()
                    .viewer(sender)
                    .notice(messages -> messages.rankGroupNotFound)
                    .send();
            case NO_SUCH_PLAYER -> this.notices.create()
                    .viewer(sender)
                    .notice(messages -> messages.rankPlayerNotFound)
                    .formatter(new Formatter().register("{PLAYER}", target))
                    .send();
            case ASSIGNED -> this.notices.create()
                    .viewer(sender)
                    .notice(messages -> formattedTime.isEmpty()
                            ? messages.rankAssigned
                            : messages.rankAssignedTemporarily)
                    .formatter(new Formatter()
                            .register("{RANK}", result.group())
                            .register("{PLAYER}", result.player())
                            .register("{TIME}", formattedTime))
                    .send();
        }
    }

    /** Renders a duration the way it is typed in: {@code 30d}, {@code 12h}, {@code 1h 30m}. */
    static String format(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        StringBuilder text = new StringBuilder();
        append(text, days, "d");
        append(text, hours, "h");
        append(text, minutes, "m");
        if (text.isEmpty()) {
            append(text, seconds, "s");
        }
        return text.toString();
    }

    private static void append(StringBuilder text, long value, String unit) {
        if (value <= 0) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(' ');
        }
        text.append(value).append(unit);
    }
}
