package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import java.util.Objects;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.skin.SkinService;

/**
 * {@code /skin <nick>} - wears the skin of a premium account.
 *
 * <p>Registered only when SkinsRestorer is installed; {@link SkinService} owns the integration
 * and the cooldown, and this only turns its answers into messages.
 *
 * <p>The permission is checked in the body rather than through {@code @Permission} because it
 * is configurable - the network already grants {@code skinsrestorer.command} to the ranks that
 * may change skins, and the original honoured that.
 */
@Command(name = "skin")
public class SkinCommand {

    private final SkinService skins;
    private final VelocityNoticeService<ProxyMessages> notices;

    public SkinCommand(SkinService skins, VelocityNoticeService<ProxyMessages> notices) {
        this.skins = Objects.requireNonNull(skins, "skins");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    void execute(@Context Player player, @Arg("nick") String skinName) {
        if (!player.hasPermission(this.skins.permission())) {
            this.notices.create().viewer(player).notice(messages -> messages.skinNoPermission).send();
            return;
        }

        if (!SkinService.isValidSkinName(skinName)) {
            this.notices.create().viewer(player).notice(messages -> messages.skinInvalidName).send();
            return;
        }

        if (!this.skins.mayWear(player, skinName)) {
            this.notices.create().viewer(player).notice(messages -> messages.skinProtected).send();
            return;
        }

        long remaining = this.skins.remainingCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            this.notices.create()
                    .viewer(player)
                    .notice(messages -> messages.skinCooldown)
                    .formatter(new Formatter().register("{SECONDS}", Long.toString(remaining)))
                    .send();
            return;
        }

        this.notices.create()
                .viewer(player)
                .notice(messages -> messages.skinLoading)
                .formatter(new Formatter().register("{SKIN}", skinName))
                .send();

        this.skins.apply(player, skinName).thenAccept(result -> this.reply(player, skinName, result));
    }

    /**
     * {@code /skin przywroc} - zdejmuje wybranego skina.
     *
     * <p>Osobna sciezka, a nie {@code /skin <wlasny nick>}: to drugie wyglada na to samo, ale
     * zostawia w magazynie wpis, ze gracz nosi skina o nazwie takiej samej jak jego nick - i
     * przestaje dzialac w dniu, w ktorym zmieni nick.
     */
    @Execute(name = "przywroc", aliases = {"restart", "reset", "zdejmij"})
    void restore(@Context Player player) {
        if (!player.hasPermission(this.skins.permission())) {
            this.notices.create().viewer(player).notice(messages -> messages.skinNoPermission).send();
            return;
        }

        long remaining = this.skins.remainingCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            this.notices.create()
                    .viewer(player)
                    .notice(messages -> messages.skinCooldown)
                    .formatter(new Formatter().register("{SECONDS}", Long.toString(remaining)))
                    .send();
            return;
        }

        this.skins.restore(player).thenAccept(result -> {
            if (!player.isActive()) {
                return;
            }

            this.notices.create()
                    .viewer(player)
                    .notice(result == SkinService.SkinResult.APPLIED
                            ? messages -> messages.skinRestored
                            : messages -> messages.skinFailed)
                    .formatter(new Formatter().register("{SKIN}", player.getUsername()))
                    .send();
        });
    }

    private void reply(Player player, String skinName, SkinService.SkinResult result) {
        // The lookup outlives short sessions; sending to a player who already left would be a
        // message nobody reads and, on some transports, a warning in the log.
        if (!player.isActive()) {
            return;
        }

        this.notices.create()
                .viewer(player)
                .notice(messages -> switch (result) {
                    case APPLIED -> messages.skinApplied;
                    case NOT_FOUND -> messages.skinNotFound;
                    case FAILED -> messages.skinFailed;
                })
                .formatter(new Formatter().register("{SKIN}", skinName))
                .send();
    }
}
