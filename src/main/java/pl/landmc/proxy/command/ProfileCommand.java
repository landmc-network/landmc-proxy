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
import pl.landmc.proxy.menu.ProfileMenuService;

/**
 * {@code /profil} - a player's own profile, as a menu.
 *
 * <p>On the proxy rather than on each backend, for the same reason as the rest: the friend count
 * and the rank are proxy state, and a command registered here works from every server without
 * being installed on any of them.
 */
@Command(name = "profil", aliases = {"profile", "me"})
public final class ProfileCommand {

    private final ProfileMenuService profiles;
    private final MenuBridge bridge;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final Logger logger;

    public ProfileCommand(
            ProfileMenuService profiles,
            MenuBridge bridge,
            VelocityNoticeService<ProxyMessages> notices,
            Logger logger) {

        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Execute
    void execute(@Context Player player) {
        this.profiles.payload(player)
                .thenAccept(payload -> {
                    if (!this.bridge.send(player, payload)) {
                        this.notices.create()
                                .viewer(player)
                                .notice(messages -> messages.menuUnavailable)
                                .send();
                    }
                })
                .exceptionally(throwable -> {
                    this.logger.error(
                            "Could not build the profile of {}", player.getUsername(), throwable);
                    this.notices.create()
                            .viewer(player)
                            .notice(messages -> messages.menuUnavailable)
                            .send();
                    return null;
                });
    }
}
