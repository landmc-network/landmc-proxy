package pl.landmc.proxy.help;

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Tells the backend which command a player just ran.
 *
 * <p>A command the proxy owns never reaches the backend, so a tutorial that ticks off "use
 * /msg" would never see it happen. The proxy forwards the command's name - only the name - on
 * a plugin channel, and the backend credits it.
 *
 * <p>Just the root: {@code /msg Crispi hello} is sent as {@code msg}. The rest is the message
 * itself, and forwarding a private message to a backend that has no business reading it would
 * be a poor trade for a progress tick.
 */
public final class HelpProgressProtocol {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("landmc:help_progress");

    private HelpProgressProtocol() {
    }

    /**
     * The bare command name, lower-cased and stripped of slashes and namespace.
     *
     * <p>Clients send the same command in several shapes - {@code /Msg}, {@code //msg} after a
     * typo, {@code /landmc:msg} when a name collides - and all of them mean the same command to
     * whoever is counting progress.
     *
     * @return the name as UTF-8 bytes, empty when there is no command in the line
     */
    public static byte[] commandRoot(String commandLine) {
        String command = commandLine == null ? "" : commandLine.strip().toLowerCase(Locale.ROOT);

        while (command.startsWith("/")) {
            command = command.substring(1).stripLeading();
        }

        int separator = command.indexOf(' ');
        if (separator >= 0) {
            command = command.substring(0, separator);
        }

        int namespace = command.indexOf(':');
        if (namespace >= 0 && namespace + 1 < command.length()) {
            command = command.substring(namespace + 1);
        }

        return command.getBytes(StandardCharsets.UTF_8);
    }
}
