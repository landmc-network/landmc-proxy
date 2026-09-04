package pl.landmc.proxy.resourcepack;

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.OptionalInt;

public final class ResourcePackProtocol {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("landmc:resourcepack");
    public static final byte VERSION = 1;
    public static final byte RESEND_REQUEST = 1;
    public static final byte DELIVERY_PENDING = 2;
    public static final byte DELIVERY_COMPLETE = 3;
    public static final byte REFRESH_MANIFEST = 4;

    private ResourcePackProtocol() {
    }

    public static OptionalInt decodeBackendAction(byte[] payload) {
        if (payload == null || payload.length != 2 || payload[0] != VERSION) {
            return OptionalInt.empty();
        }
        int action = payload[1];
        if (action != RESEND_REQUEST && action != REFRESH_MANIFEST) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(action);
    }

    public static byte[] deliveryState(boolean pending) {
        return new byte[] {
                VERSION,
                pending ? DELIVERY_PENDING : DELIVERY_COMPLETE
        };
    }
}
