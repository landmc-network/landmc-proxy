package pl.landmc.proxy.motd;

import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import pl.landmc.platform.component.ComponentFormatter;
import pl.landmc.proxy.config.ProxyConfig;

/**
 * What the server list shows before anybody connects.
 *
 * <p>Two lines, the player count, the list under the cursor and the icon - and a different set
 * of all of it while the network is closed. That last part is the reason this exists at all: a
 * maintenance mode that only says so after somebody has typed the address, waited for the
 * connection and been disconnected is a maintenance mode that everybody finds out about the
 * hard way. The list is where they are already looking.
 *
 * <p>Everything that does not change is built once. A ping arrives every time a client's server
 * list refreshes, which for a public address is often, and parsing four lines of MiniMessage
 * per ping would be work done thousands of times for a result that differs only in one number.
 * The description is therefore kept until the player count actually moves.
 */
public final class MotdService {

    /** How the configured lines are joined into the two-line description. */
    private static final JoinConfiguration NEWLINE =
            JoinConfiguration.separator(Component.newline());

    /**
     * A protocol number no client speaks.
     *
     * <p>Sent only with a version name during maintenance. A client that cannot match the
     * protocol shows the name in red where it would print the version, which is exactly the
     * "you cannot join right now" the list is meant to convey.
     */
    private static final int INCOMPATIBLE_PROTOCOL = -1;

    private final ProxyConfig config;
    private final ComponentFormatter formatter;

    private final List<ServerPing.SamplePlayer> sample;
    private final List<ServerPing.SamplePlayer> maintenanceSample;
    private final Favicon favicon;

    /**
     * The last description built, together with what it was built from.
     *
     * <p>One field rather than three, because pings are answered on whichever thread they
     * arrive on: replacing a record leaves a reader with a consistent set, while three fields
     * updated in sequence can be read half-written and hand somebody yesterday's player count
     * under today's maintenance text.
     */
    private volatile Described described;

    public MotdService(
            ProxyConfig config,
            ComponentFormatter formatter,
            Path dataDirectory,
            Logger logger) {

        this.config = Objects.requireNonNull(config, "config");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        Objects.requireNonNull(logger, "logger");

        this.sample = sampleOf(config.motd.hover);
        this.maintenanceSample = sampleOf(config.motd.maintenanceHover);
        this.favicon = loadFavicon(dataDirectory, config.motd.iconFile, logger);
    }

    public boolean isEnabled() {
        return this.config.motd.enabled;
    }

    /**
     * Rewrites a ping.
     *
     * @param maintenance whether the network is closed right now
     */
    public ServerPing apply(ServerPing ping, boolean maintenance) {
        ProxyConfig.MotdSection motd = this.config.motd;

        int online = ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0);
        int max = motd.maxPlayers > 0
                ? motd.maxPlayers
                : ping.getPlayers().map(ServerPing.Players::getMax).orElse(0);

        ServerPing.Builder builder = ping.asBuilder()
                .description(this.describe(online, max, maintenance))
                .onlinePlayers(online)
                .maximumPlayers(max);

        List<ServerPing.SamplePlayer> sample =
                maintenance ? this.maintenanceSample : this.sample;
        if (!sample.isEmpty()) {
            builder.clearSamplePlayers()
                    .samplePlayers(sample.toArray(new ServerPing.SamplePlayer[0]));
        }

        if (maintenance && !motd.maintenanceVersion.isBlank()) {
            builder.version(new ServerPing.Version(
                    INCOMPATIBLE_PROTOCOL, motd.maintenanceVersion));
        }

        if (this.favicon != null) {
            builder.favicon(this.favicon);
        }

        return builder.build();
    }

    private Component describe(int online, int max, boolean maintenance) {
        Described cached = this.described;
        if (cached != null && cached.online() == online && cached.maintenance() == maintenance) {
            return cached.component();
        }

        List<String> lines = maintenance
                ? this.config.motd.maintenanceLines
                : this.config.motd.lines;

        List<Component> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(this.formatter.format(line
                    .replace("{ONLINE}", Integer.toString(online))
                    .replace("{MAX}", Integer.toString(max))));
        }

        Component description = Component.join(NEWLINE, rendered);
        // Two pings arriving together can both build this. They build the same thing, and one
        // wasted parse is cheaper than holding a lock across every ping on the network.
        this.described = new Described(description, online, maintenance);
        return description;
    }

    /** A built description and the state it describes. */
    private record Described(Component component, int online, boolean maintenance) {
    }

    private List<ServerPing.SamplePlayer> sampleOf(List<String> lines) {
        List<ServerPing.SamplePlayer> sample = new ArrayList<>(lines.size());
        for (String line : lines) {
            // A fresh id per line, once. Clients key the hover list by id, and repeating one
            // makes the lines collapse into each other.
            // The hover list is plain text to the client, so the colour has to be written
            // the old way; MiniMessage tags would be shown literally.
            sample.add(new ServerPing.SamplePlayer(
                    LegacyComponentSerializer.legacySection()
                            .serialize(this.formatter.format(line)),
                    UUID.randomUUID()));
        }
        return sample;
    }

    /**
     * The icon beside the name, or null when there is not one.
     *
     * <p>Absent is the normal case and says nothing; present but unreadable is worth a line,
     * because somebody put a file there meaning it to be used.
     */
    private static Favicon loadFavicon(Path dataDirectory, String fileName, Logger logger) {
        if (fileName.isBlank()) {
            return null;
        }

        Path icon = dataDirectory.resolve(fileName);
        if (!Files.isRegularFile(icon)) {
            return null;
        }

        try {
            return Favicon.create(icon);
        }
        catch (IOException | IllegalArgumentException exception) {
            logger.warn(
                    "Server icon {} could not be read; the list will show none."
                            + " It has to be a 64x64 PNG. ({})",
                    icon, exception.getMessage());
            return null;
        }
    }
}
