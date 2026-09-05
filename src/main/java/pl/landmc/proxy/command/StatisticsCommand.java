package pl.landmc.proxy.command;

import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import java.util.Objects;
import org.slf4j.Logger;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.menu.MenuBridge;
import pl.landmc.proxy.menu.StatisticsMenuService;

/**
 * {@code /statystyki} - the third tab of the profile.
 *
 * <p>A command as well as a tab, for the same reason the other two are: a backend cannot run a
 * proxy command that does not exist, and the tab strip asks for this one by name.
 */
@Command(name = "statystyki", aliases = {"statistics", "stats"})
public final class StatisticsCommand {

    private final StatisticsMenuService statistics;
    private final MenuBridge bridge;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final Logger logger;

    public StatisticsCommand(
            StatisticsMenuService statistics,
            MenuBridge bridge,
            VelocityNoticeService<ProxyMessages> notices,
            Logger logger) {

        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Execute
    void execute(@Context Player player) {
        this.statistics.payload(player)
                .thenAccept(payload -> {
                    if (!this.bridge.send(player, payload)) {
                        this.notices.viewer(player, messages -> messages.menuUnavailable);
                    }
                })
                .exceptionally(throwable -> {
                    this.logger.error(
                            "Could not build the statistics of {}",
                            player.getUsername(), throwable);
                    this.notices.viewer(player, messages -> messages.menuUnavailable);
                    return null;
                });
    }
}
