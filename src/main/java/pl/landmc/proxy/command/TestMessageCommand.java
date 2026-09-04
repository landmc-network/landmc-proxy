package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.time.Instant;
import java.util.Objects;
import pl.landmc.platform.messaging.MessageBus;
import pl.landmc.platform.messaging.message.MessageTarget;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.messaging.PingMessage;
import pl.landmc.proxy.messaging.PongMessage;

/**
 * {@code /testmessage <server>} - proves the Proxy → Redis → Paper → Proxy path works.
 *
 * <p>A diagnostic, not a feature: it is the smallest thing that fails loudly when Redis is
 * unreachable, when the target node is not subscribed, or when the two sides disagree about a
 * message type. Worth having before anything real depends on messaging.
 *
 * <p>The response is composed, never waited on. {@code get()} or {@code join()} here would
 * block the thread Velocity dispatched the command on for as long as the request timeout,
 * which on a busy proxy is a stall the whole network notices.
 */
@Command(name = "testmessage")
@Permission("landmc.command.testmessage")
public class TestMessageCommand {

    private final MessageBus bus;
    private final ProxyConfig config;
    private final VelocityNoticeService<ProxyMessages> notices;

    public TestMessageCommand(
            MessageBus bus, ProxyConfig config, VelocityNoticeService<ProxyMessages> notices) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.config = Objects.requireNonNull(config, "config");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Execute
    void execute(@Context CommandSource sender, @Arg("serwer") String serverId) {
        if (!this.config.messaging.enabled) {
            this.notices.viewer(sender, messages -> messages.messagingDisabled);
            return;
        }

        PingMessage ping = new PingMessage(this.bus.serverId(), Instant.now().toEpochMilli());

        this.bus.request(MessageTarget.server(serverId), ping, PongMessage.class)
                .whenComplete((pong, error) -> {
                    if (error != null) {
                        this.notices.viewer(
                                sender,
                                messages -> messages.messagingFailed,
                                new Formatter()
                                        .register("{SERVER}", serverId)
                                        .register("{REASON}", reasonOf(error)));
                        return;
                    }

                    long roundTrip = Instant.now().toEpochMilli() - pong.sentAt();
                    this.notices.viewer(
                            sender,
                            messages -> messages.messagingPong,
                            new Formatter()
                                    .register("{SERVER}", pong.from())
                                    .register("{TIME}", roundTrip + "ms"));
                });
    }

    /** The cause's message, since the future always arrives wrapped in a CompletionException. */
    private static String reasonOf(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
