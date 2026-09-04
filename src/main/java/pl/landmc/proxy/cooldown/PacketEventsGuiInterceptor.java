package pl.landmc.proxy.cooldown;

import com.eternalcode.multification.shared.Formatter;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import pl.landmc.platform.proxy.notice.VelocityNoticeService;
import pl.landmc.proxy.config.ProxyConfig;
import pl.landmc.proxy.config.ProxyMessages;

/**
 * Cancels inventory clicks that arrive faster than the configured cooldown allows.
 *
 * <p>Cancelling a click is not enough on its own: the client has already applied the change it
 * predicted, so the window has to be put back. That is what the snapshot is for - the proxy
 * keeps the last full window the backend sent, updated by every {@code SET_SLOT}, and replays it
 * when a click is refused. Without that the player sees a menu that disagrees with the server
 * until the next update.
 *
 * <p>Only installed when PacketEvents is present; {@link GuiPacketInterceptor#DISABLED} takes
 * its place otherwise.
 */
public final class PacketEventsGuiInterceptor implements GuiPacketInterceptor {

    private final GlobalCooldownService cooldowns;
    private final ProxyConfig config;
    private final VelocityNoticeService<ProxyMessages> notices;

    /** When each player may next be told they are clicking too fast. */
    private final ConcurrentMap<UUID, Long> nextNotice = new ConcurrentHashMap<>();

    /** The last window the backend sent, per player, so a refused click can be undone. */
    private final ConcurrentMap<UUID, WindowSnapshot> windowSnapshots = new ConcurrentHashMap<>();

    private final PacketListenerCommon listener;

    public PacketEventsGuiInterceptor(
            GlobalCooldownService cooldowns,
            ProxyConfig config,
            VelocityNoticeService<ProxyMessages> notices) {

        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.config = Objects.requireNonNull(config, "config");
        this.notices = Objects.requireNonNull(notices, "notices");

        this.listener = new PacketListenerAbstract(PacketListenerPriority.LOWEST) {

            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                PacketEventsGuiInterceptor.this.onReceive(event);
            }

            @Override
            public void onPacketSend(PacketSendEvent event) {
                PacketEventsGuiInterceptor.this.onSend(event);
            }
        };

        PacketEvents.getAPI().getEventManager().registerListener(this.listener);
    }

    private void onReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }

        ProxyConfig.CooldownSection settings = this.config.cooldown;
        UUID playerId = player.getUniqueId();

        if (!settings.enabled
                || !this.cooldowns.isGuiOpen(playerId)
                || hasBypass(player, settings.bypassPermission)) {
            return;
        }

        GlobalCooldownService.AcquireResult result = this.cooldowns.tryAcquireGui(
                playerId, System.currentTimeMillis(), settings.guiCooldownMillis);
        if (result.acquired()) {
            return;
        }

        WindowSnapshot snapshot = this.windowSnapshots.get(playerId);
        if (snapshot == null || !isMatchingWindow(event, snapshot.windowId())) {
            // Without a snapshot the click cannot be undone cleanly, so let it through rather
            // than leave the player looking at a window the server disagrees with.
            return;
        }

        event.setCancelled(true);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, snapshot.toPacket());
        this.notifyBlocked(player, result.remainingMillis(), settings);
    }

    private void onSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();

        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW
                || event.getPacketType() == PacketType.Play.Server.CLOSE_WINDOW) {
            this.windowSnapshots.remove(playerId);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
            // Window 0 is the player's own inventory, which this does not throttle.
            if (wrapper.getWindowId() > 0) {
                this.windowSnapshots.put(playerId, WindowSnapshot.from(wrapper));
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            if (!this.windowSnapshots.containsKey(playerId)) {
                return;
            }
            WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
            this.windowSnapshots.computeIfPresent(playerId, (ignored, snapshot) -> snapshot.withSlot(wrapper));
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_CURSOR_ITEM) {
            if (!this.windowSnapshots.containsKey(playerId)) {
                return;
            }
            WrapperPlayServerSetCursorItem wrapper = new WrapperPlayServerSetCursorItem(event);
            this.windowSnapshots.computeIfPresent(
                    playerId, (ignored, snapshot) -> snapshot.withCarriedItem(wrapper.getStack()));
        }
    }

    private static boolean isMatchingWindow(PacketReceiveEvent event, int expectedWindowId) {
        try {
            return new WrapperPlayClientClickWindow(event).getWindowId() == expectedWindowId;
        }
        catch (RuntimeException exception) {
            // A packet this build cannot read is not one to act on.
            return false;
        }
    }

    /**
     * Tells the player once per interval rather than on every refused click - a player holding
     * the mouse down would otherwise flood their own chat.
     */
    private void notifyBlocked(Player player, long remainingMillis, ProxyConfig.CooldownSection settings) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        if (this.nextNotice.getOrDefault(playerId, 0L) > now) {
            return;
        }
        this.nextNotice.put(playerId, safeAdd(now, Math.max(0L, settings.guiMessageIntervalMillis)));

        this.notices.viewer(
                player,
                messages -> messages.cooldownGuiBlocked,
                new Formatter().register("{TIME}", formatTime(remainingMillis)));
    }

    private static boolean hasBypass(Player player, String permission) {
        return permission != null && !permission.isBlank() && player.hasPermission(permission);
    }

    private static String formatTime(long remainingMillis) {
        if (remainingMillis >= 1_000L) {
            return String.format(Locale.ROOT, "%.2fs", remainingMillis / 1_000.0D);
        }
        return Math.max(1L, remainingMillis) + "ms";
    }

    private static long safeAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    @Override
    public void remove(UUID playerId) {
        this.nextNotice.remove(playerId);
        this.windowSnapshots.remove(playerId);
    }

    @Override
    public void close() {
        if (PacketEvents.getAPI() != null && PacketEvents.getAPI().getEventManager() != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(this.listener);
        }
        this.nextNotice.clear();
        this.windowSnapshots.clear();
    }

    /** The last window state the backend sent, enough to put it back after a refused click. */
    private record WindowSnapshot(int windowId, int stateId, List<ItemStack> items, ItemStack carriedItem) {

        private static WindowSnapshot from(WrapperPlayServerWindowItems wrapper) {
            return new WindowSnapshot(
                    wrapper.getWindowId(),
                    wrapper.getStateId(),
                    copyItems(wrapper.getItems()),
                    wrapper.getCarriedItem().map(ItemStack::copy).orElse(null));
        }

        private WindowSnapshot withSlot(WrapperPlayServerSetSlot wrapper) {
            if (wrapper.getWindowId() != this.windowId
                    || wrapper.getSlot() < 0
                    || wrapper.getSlot() >= this.items.size()) {
                return this;
            }

            List<ItemStack> updated = new ArrayList<>(this.items);
            updated.set(wrapper.getSlot(), wrapper.getItem().copy());
            return new WindowSnapshot(this.windowId, wrapper.getStateId(), updated, this.carriedItem);
        }

        private WindowSnapshot withCarriedItem(ItemStack item) {
            return new WindowSnapshot(
                    this.windowId, this.stateId, this.items, item == null ? null : item.copy());
        }

        private WrapperPlayServerWindowItems toPacket() {
            return new WrapperPlayServerWindowItems(
                    this.windowId,
                    this.stateId,
                    copyItems(this.items),
                    this.carriedItem == null ? null : this.carriedItem.copy());
        }

        private static List<ItemStack> copyItems(List<ItemStack> source) {
            List<ItemStack> copy = new ArrayList<>(source.size());
            for (ItemStack item : source) {
                copy.add(item.copy());
            }
            return copy;
        }
    }
}
