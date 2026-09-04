package pl.landmc.proxy.cooldown;

import java.util.UUID;

/**
 * Throttles inventory clicks at the packet level.
 *
 * <p>The seam that keeps PacketEvents optional. Clicking through a menu faster than the backend
 * can answer is only observable in the packet stream, so this is the one part of the cooldown
 * that cannot work without it - {@link #DISABLED} takes over when PacketEvents is not installed
 * and the rest of the feature, the command cooldown and the backend sync, keeps working.
 */
public interface GuiPacketInterceptor extends AutoCloseable {

    /** Does nothing; installed when PacketEvents is absent. */
    GuiPacketInterceptor DISABLED = new GuiPacketInterceptor() {

        @Override
        public void remove(UUID playerId) {
        }

        @Override
        public void close() {
        }
    };

    /** Forgets a player's per-session packet state. */
    void remove(UUID playerId);

    @Override
    void close();
}
