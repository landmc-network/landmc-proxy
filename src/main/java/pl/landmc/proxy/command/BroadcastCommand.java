package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.join.Join;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.util.Objects;
import org.slf4j.Logger;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;

/**
 * {@code /broadcast <wiadomość>} - one message to everybody on the network.
 *
 * <p>The proxy is the only process that can do this in one go; a backend can only reach the
 * players standing on it, so an announcement sent from there misses whoever is elsewhere.
 *
 * <p>The wording is the sender's, but the frame around it comes from messages.yml - a broadcast
 * has to be recognisable as one, and staff should not be able to make a message look like a
 * system notice by typing it that way.
 */
@Command(name = "broadcast", aliases = {"bc", "ogloszenie"})
@Permission("landmc.command.broadcast")
public class BroadcastCommand {

    private final VelocityNoticeService<ProxyMessages> notices;
    private final Logger logger;

    public BroadcastCommand(VelocityNoticeService<ProxyMessages> notices, Logger logger) {
        this.notices = Objects.requireNonNull(notices, "notices");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Execute
    void execute(@Context CommandSource sender, @Join("wiadomość") String message) {
        if (message.isBlank()) {
            return;
        }

        this.notices.create()
                .onlinePlayers()
                .console()
                .notice(messages -> messages.broadcast)
                .formatter(new Formatter().register("{MESSAGE}", message))
                .send();

        this.logger.info(
                "Broadcast by {}: {}",
                sender instanceof Player player ? player.getUsername() : "Konsola",
                message);
    }
}
