package pl.landmc.proxy.command;

import com.eternalcode.multification.shared.Formatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.optional.OptionalArg;
import dev.rollczi.litecommands.annotations.permission.Permission;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import org.slf4j.Logger;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;
import pl.landmc.proxy.voucher.VoucherService;

/** The two voucher commands: redeeming one, and issuing them. */
public final class VoucherCommands {

    private VoucherCommands() {
    }

    /**
     * {@code /voucher <kod>} - redeems a code.
     *
     * <p>Rate-limited per player, because without it the command is a way to guess codes as
     * fast as the connection allows. A wrong code and somebody else's code get the same answer,
     * so a guess cannot be confirmed as "close".
     */
    @Command(name = "voucher", aliases = "kod")
    public static class Redeem {

        private final VoucherService vouchers;
        private final ProxyServer proxy;
        private final VelocityNoticeService<ProxyMessages> notices;
        private final Logger logger;

        public Redeem(
                VoucherService vouchers,
                ProxyServer proxy,
                VelocityNoticeService<ProxyMessages> notices,
                Logger logger) {

            this.vouchers = Objects.requireNonNull(vouchers, "vouchers");
            this.proxy = Objects.requireNonNull(proxy, "proxy");
            this.notices = Objects.requireNonNull(notices, "notices");
            this.logger = Objects.requireNonNull(logger, "logger");
        }

        /** Bare {@code /voucher} says how many codes are waiting for this player. */
        @Execute
        void waiting(@Context Player player) {
            this.vouchers.unusedFor(player.getUsername())
                    .thenAccept(count -> this.notices.create()
                            .viewer(player)
                            .notice(messages -> count > 0
                                    ? messages.voucherWaiting
                                    : messages.voucherNoneWaiting)
                            .formatter(new Formatter().register("{COUNT}", Long.toString(count)))
                            .send())
                    .exceptionally(this.report(player, "voucher list"));
        }

        @Execute
        void redeem(@Context Player player, @Arg("kod") String code) {
            long remaining = this.vouchers.remainingCooldownSeconds(player.getUniqueId());
            if (remaining > 0) {
                this.notices.create()
                        .viewer(player)
                        .notice(messages -> messages.voucherCooldown)
                        .formatter(new Formatter().register("{SECONDS}", Long.toString(remaining)))
                        .send();
                return;
            }

            // Started before the lookup, not after: otherwise the limit only applies to codes
            // that were checked quickly, which is the opposite of what it is for.
            this.vouchers.startCooldown(player.getUniqueId());

            this.vouchers.redeem(player.getUniqueId(), player.getUsername(), code)
                    .thenAccept(result -> this.apply(player, code, result))
                    .exceptionally(this.report(player, "voucher " + code));
        }

        private void apply(Player player, String code, VoucherService.RedeemResult result) {
            switch (result.outcome()) {
                case REDEEMED -> {
                    this.notices.create()
                            .viewer(player)
                            .notice(messages -> messages.voucherRedeemed)
                            .formatter(new Formatter().register("{REWARD}", result.reward().name))
                            .send();

                    this.logger.info(
                            "{} redeemed a voucher ({}).", player.getUsername(), result.type());
                    this.runRewardCommands(player, result);
                }
                case ALREADY_USED -> this.notices.create()
                        .viewer(player)
                        .notice(messages -> messages.voucherAlreadyUsed)
                        .send();
                case UNKNOWN -> this.notices.create()
                        .viewer(player)
                        .notice(messages -> messages.voucherUnknown)
                        .send();
                case UNKNOWN_TYPE -> {
                    // The code was valid; the reward it names has been removed from the config.
                    // The player is told it failed, and staff get the detail they need to fix it.
                    this.logger.error(
                            "Voucher {} names reward type '{}', which is not in config.yml.",
                            code, result.type());
                    this.notices.create().viewer(player).notice(messages -> messages.voucherFailed).send();
                }
            }
        }

        /**
         * Runs what the reward is defined as.
         *
         * <p>Console commands rather than a hard-coded list of reward kinds, so a new reward is
         * a config change. They run on the proxy: LuckPerms lives here, which covers ranks -
         * anything a backend has to grant needs a command the proxy can forward.
         */
        private void runRewardCommands(Player player, VoucherService.RedeemResult result) {
            for (String template : result.reward().commands) {
                String command = template.replace("{PLAYER}", player.getUsername());

                this.proxy.getCommandManager()
                        .executeAsync(this.proxy.getConsoleCommandSource(), command)
                        .exceptionally(throwable -> {
                            this.logger.error(
                                    "Reward command '{}' failed for {}", command, player.getUsername(), throwable);
                            return null;
                        });
            }
        }

        private Function<Throwable, Void> report(Player player, String what) {
            return throwable -> {
                this.logger.error("Voucher command failed ({})", what, throwable);
                this.notices.create().viewer(player).notice(messages -> messages.voucherFailed).send();
                return null;
            };
        }
    }

    /** {@code /generujvoucher <typ> <ilość> [gracz]} */
    @Command(name = "generujvoucher", aliases = {"generatevoucher", "voucherget"})
    @Permission("landmc.voucher.generate")
    public static class Generate {

        /** Enough for a giveaway, few enough that a typo cannot fill the table. */
        private static final int MAXIMUM_COUNT = 100;

        private final VoucherService vouchers;
        private final VelocityNoticeService<ProxyMessages> notices;
        private final ProxyConfig config;
        private final Logger logger;

        public Generate(
                VoucherService vouchers,
                VelocityNoticeService<ProxyMessages> notices,
                ProxyConfig config,
                Logger logger) {

            this.vouchers = Objects.requireNonNull(vouchers, "vouchers");
            this.notices = Objects.requireNonNull(notices, "notices");
            this.config = Objects.requireNonNull(config, "config");
            this.logger = Objects.requireNonNull(logger, "logger");
        }

        @Execute
        void execute(
                @Context CommandSource sender,
                @Arg("typ") String type,
                @Arg("ilość") int count,
                @OptionalArg("gracz") String assignedTo) {

            if (!this.config.vouchers.types.containsKey(type)) {
                StringJoiner known = new StringJoiner(", ");
                this.config.vouchers.types.keySet().forEach(known::add);

                this.notices.create()
                        .viewer(sender)
                        .notice(messages -> messages.voucherUnknownType)
                        .formatter(new Formatter()
                                .register("{TYPE}", type)
                                .register("{TYPES}", known.toString()))
                        .send();
                return;
            }

            if (count < 1 || count > MAXIMUM_COUNT) {
                this.notices.create()
                        .viewer(sender)
                        .notice(messages -> messages.voucherInvalidCount)
                        .formatter(new Formatter().register("{MAXIMUM}", Integer.toString(MAXIMUM_COUNT)))
                        .send();
                return;
            }

            String issuedBy = sender instanceof Player player ? player.getUsername() : "Konsola";

            this.vouchers.issue(type, assignedTo, count, issuedBy)
                    .thenAccept(codes -> this.report(sender, type, assignedTo, codes, issuedBy))
                    .exceptionally(throwable -> {
                        this.logger.error("Could not issue {} voucher(s) of type {}", count, type, throwable);
                        this.notices.create().viewer(sender).notice(messages -> messages.voucherFailed).send();
                        return null;
                    });
        }

        private void report(
                CommandSource sender, String type, String assignedTo, List<String> codes, String issuedBy) {

            this.notices.create()
                    .viewer(sender)
                    .notice(messages -> messages.voucherIssued)
                    .formatter(new Formatter()
                            .register("{COUNT}", Integer.toString(codes.size()))
                            .register("{TYPE}", type)
                            .register("{PLAYER}", assignedTo == null ? "wszystkich" : assignedTo))
                    .send();

            codes.forEach(code -> this.notices.create()
                    .viewer(sender)
                    .notice(messages -> messages.voucherCode)
                    .formatter(new Formatter().register("{CODE}", code))
                    .send());

            // Logged because a voucher is worth something: who issued what, and for whom.
            this.logger.info(
                    "{} issued {} voucher(s) of type {} for {}.",
                    issuedBy, codes.size(), type, assignedTo == null ? "anyone" : assignedTo);
        }
    }
}
