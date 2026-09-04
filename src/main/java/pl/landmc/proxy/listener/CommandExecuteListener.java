package pl.landmc.proxy.listener;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.cooldown.CooldownTime;
import pl.landmc.proxy.cooldown.GlobalCooldownService;
import pl.landmc.proxy.help.HelpProgressProtocol;

/**
 * The two things the proxy does when a player runs any command.
 *
 * <p>It enforces the global cooldown, and it tells the backend which command was run so a
 * tutorial can credit commands the backend never sees.
 *
 * <p>Cooldown enforcement here is off by default. Cancelling a command on the proxy only works
 * reliably with SignedVelocity on Minecraft 1.19.1 and later; without it the client can have
 * already committed the command, and the cancellation desynchronises rather than blocks. The
 * backend enforces its own cooldown, and the proxy's job is to keep the timer shared across
 * servers so switching does not reset it - which happens regardless of this switch.
 *
 * <p>Login commands are exempt, and that is not a nicety: a cooldown that blocks {@code /login}
 * locks a player out of the network entirely.
 */
public final class CommandExecuteListener {

    private final GlobalCooldownService cooldowns;
    private final ProxyConfig config;
    private final VelocityNoticeService<ProxyMessages> notices;
    private final Set<String> ignoredCommands;

    public CommandExecuteListener(
            GlobalCooldownService cooldowns,
            ProxyConfig config,
            VelocityNoticeService<ProxyMessages> notices) {

        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.config = Objects.requireNonNull(config, "config");
        this.notices = Objects.requireNonNull(notices, "notices");

        // Normalised once: this runs on every command from every player.
        this.ignoredCommands = new HashSet<>();
        for (String command : config.cooldown.ignoredCommands) {
            this.ignoredCommands.add(command.toLowerCase(Locale.ROOT).strip());
        }
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        String root = new String(
                HelpProgressProtocol.commandRoot(event.getCommand()),
                StandardCharsets.UTF_8);

        if (this.isBlockedByCooldown(player, root)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            return;
        }

        this.reportProgress(player, root);
    }

    private boolean isBlockedByCooldown(Player player, String root) {
        if (!this.config.cooldown.enabled
                || !this.config.cooldown.enforceCommandsOnProxy
                || this.ignoredCommands.contains(root)
                || player.hasPermission(this.config.cooldown.bypassPermission)) {
            return false;
        }

        GlobalCooldownService.AcquireResult result = this.cooldowns.tryAcquireCommand(
                player.getUniqueId(), System.currentTimeMillis(), this.config.cooldown.commandCooldownMillis);

        if (result.acquired()) {
            return false;
        }

        this.notices.create()
                .viewer(player)
                .notice(messages -> messages.cooldownCommandBlocked)
                .formatter(new Formatter().register("{TIME}", CooldownTime.format(result.remainingMillis())))
                .send();
        return true;
    }

    private void reportProgress(Player player, String root) {
        if (!this.config.helpProgress.enabled || root.isEmpty()) {
            return;
        }

        player.getCurrentServer().ifPresent(connection -> connection.sendPluginMessage(
                HelpProgressProtocol.CHANNEL, root.getBytes(StandardCharsets.UTF_8)));
    }
}
