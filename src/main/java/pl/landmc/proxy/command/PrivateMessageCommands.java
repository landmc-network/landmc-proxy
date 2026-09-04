package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.proxy.Player;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.join.Join;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.util.List;
import java.util.Objects;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.privatemessage.PrivateMessageService;

/**
 * The private-message commands.
 *
 * <p>Grouped in one file because they are five thin entry points into one service - splitting
 * them across five classes, as the original did, meant five copies of the same constructor and
 * the same permission check. The behaviour lives in {@link PrivateMessageService}.
 */
public final class PrivateMessageCommands {

    private PrivateMessageCommands() {
    }

    /** {@code /msg <gracz> <wiadomość>} */
    @Command(name = "msg", aliases = {"tell", "w", "whisper", "pm"})
    @Permission("landmc.command.msg")
    public static class Message {

        private final PrivateMessageService messages;

        public Message(PrivateMessageService messages) {
            this.messages = Objects.requireNonNull(messages, "messages");
        }

        @Execute
        void execute(@Context Player sender, @Arg("gracz") Player receiver, @Join("wiadomość") String message) {
            if (message.isBlank()) {
                return;
            }
            this.messages.send(sender, receiver, message);
        }
    }

    /** {@code /r <wiadomość>} - answers whoever wrote last. */
    @Command(name = "reply", aliases = "r")
    @Permission("landmc.command.msg")
    public static class Reply {

        private final PrivateMessageService messages;

        public Reply(PrivateMessageService messages) {
            this.messages = Objects.requireNonNull(messages, "messages");
        }

        @Execute
        void execute(@Context Player sender, @Join("wiadomość") String message) {
            if (message.isBlank()) {
                return;
            }
            this.messages.reply(sender, message);
        }
    }

    /** {@code /msgtoggle} - stops or resumes incoming private messages. */
    @Command(name = "msgtoggle", aliases = {"togglemsg", "tmsg"})
    @Permission("landmc.command.msg")
    public static class Toggle {

        private final PrivateMessageService messages;
        private final VelocityNoticeService<ProxyMessages> notices;

        public Toggle(PrivateMessageService messages, VelocityNoticeService<ProxyMessages> notices) {
            this.messages = Objects.requireNonNull(messages, "messages");
            this.notices = Objects.requireNonNull(notices, "notices");
        }

        @Execute
        void execute(@Context Player player) {
            boolean accepting = this.messages.toggleMessages(player.getUniqueId());
            this.notices.viewer(
                    player,
                    config -> accepting ? config.messagesEnabled : config.messagesDisabled);
        }
    }

    /** {@code /socialspy} - watch other people's private messages. */
    @Command(name = "socialspy")
    @Permission("landmc.command.socialspy")
    public static class SocialSpy {

        private final PrivateMessageService messages;
        private final VelocityNoticeService<ProxyMessages> notices;

        public SocialSpy(PrivateMessageService messages, VelocityNoticeService<ProxyMessages> notices) {
            this.messages = Objects.requireNonNull(messages, "messages");
            this.notices = Objects.requireNonNull(notices, "notices");
        }

        @Execute
        void execute(@Context Player player) {
            boolean spying = this.messages.toggleSocialSpy(player.getUniqueId());
            this.notices.viewer(
                    player,
                    config -> spying ? config.socialSpyEnabled : config.socialSpyDisabled);
        }
    }

    /** {@code /ignore <gracz>} and {@code /ignore} to list who is muted. */
    @Command(name = "ignore", aliases = {"unignore", "odignoruj"})
    @Permission("landmc.command.ignore")
    public static class Ignore {

        private final PrivateMessageService messages;
        private final VelocityNoticeService<ProxyMessages> notices;

        public Ignore(PrivateMessageService messages, VelocityNoticeService<ProxyMessages> notices) {
            this.messages = Objects.requireNonNull(messages, "messages");
            this.notices = Objects.requireNonNull(notices, "notices");
        }

        @Execute
        void list(@Context Player player) {
            List<String> ignored = this.messages.ignoredNames(player.getUniqueId());
            this.notices.viewer(
                    player,
                    config -> ignored.isEmpty() ? config.ignoreListEmpty : config.ignoreList,
                    new Formatter().register("{PLAYERS}", String.join(", ", ignored)));
        }

        @Execute
        void toggle(@Context Player player, @Arg("gracz") Player target) {
            if (player.getUniqueId().equals(target.getUniqueId())) {
                this.notices.viewer(player, config -> config.ignoreSelf);
                return;
            }

            boolean ignored = this.messages.toggleIgnore(player.getUniqueId(), target.getUniqueId());
            this.notices.viewer(
                    player,
                    config -> ignored ? config.ignoreAdded : config.ignoreRemoved,
                    new Formatter().register("{PLAYER}", target.getUsername()));
        }
    }
}
