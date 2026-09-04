package pl.landmc.proxy.friend;

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

/**
 * Asks the backend to open the friends menu.
 *
 * <p>The proxy owns the friends list but has no inventories, so the menu belongs to whichever
 * server the player is standing on. This channel carries one instruction and no data - the
 * backend asks the proxy for the list itself.
 *
 * <p>The payload is versioned so a backend on an older build can tell an unfamiliar message
 * from a corrupt one, and ignore it rather than misread it.
 */
public final class FriendGuiProtocol {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("landmc:friends_gui");

    private static final byte VERSION = 1;
    private static final byte OPEN_GUI = 1;

    private FriendGuiProtocol() {
    }

    public static byte[] openGuiPayload() {
        return new byte[] {VERSION, OPEN_GUI};
    }
}
