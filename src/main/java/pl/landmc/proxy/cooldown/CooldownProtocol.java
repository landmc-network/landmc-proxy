package pl.landmc.proxy.cooldown;

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class CooldownProtocol {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("landmc:cooldown");
    public static final byte VERSION = 1;
    public static final byte SYNC_REQUEST = 1;
    public static final byte MARK_GUI = 2;
    public static final byte SYNC_STATE = 3;
    public static final byte MARK_COMMAND = 4;
    public static final byte GUI_OPEN = 5;
    public static final byte GUI_CLOSE = 6;
    private static final int MAX_PAYLOAD_SIZE = 128;

    private CooldownProtocol() {
    }

    public static Optional<BackendMessage> decodeBackendMessage(byte[] payload) {
        if (payload == null || payload.length < 2 || payload.length > MAX_PAYLOAD_SIZE) {
            return Optional.empty();
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readByte() != VERSION) {
                return Optional.empty();
            }

            byte action = input.readByte();
            if ((action != SYNC_REQUEST
                    && action != MARK_GUI
                    && action != MARK_COMMAND
                    && action != GUI_OPEN
                    && action != GUI_CLOSE)
                    || input.available() != 0) {
                return Optional.empty();
            }
            return Optional.of(new BackendMessage(action));
        }
        catch (IOException exception) {
            return Optional.empty();
        }
    }

    public static byte[] encodeState(
            UUID playerId,
            long commandRemainingMillis,
            long guiRemainingMillis,
            long commandCooldownMillis,
            long guiCooldownMillis) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(50);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(VERSION);
                output.writeByte(SYNC_STATE);
                output.writeLong(playerId.getMostSignificantBits());
                output.writeLong(playerId.getLeastSignificantBits());
                output.writeLong(commandRemainingMillis);
                output.writeLong(guiRemainingMillis);
                output.writeLong(commandCooldownMillis);
                output.writeLong(guiCooldownMillis);
            }
            return bytes.toByteArray();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not encode cooldown state", exception);
        }
    }

    public record BackendMessage(byte action) {
    }
}
