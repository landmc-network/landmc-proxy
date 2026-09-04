package pl.landmc.proxy.cooldown;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;

public final class GlobalCooldownService {

    private final ConcurrentMap<UUID, CooldownState> states = new ConcurrentHashMap<>();
    private final Set<UUID> activeGuiPlayers = ConcurrentHashMap.newKeySet();

    public AcquireResult tryAcquireCommand(UUID playerId, long now, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return AcquireResult.acquired(now);
        }

        AcquireHolder holder = new AcquireHolder();
        this.states.compute(playerId, (ignored, current) -> {
            CooldownState state = current == null ? CooldownState.EMPTY : current;
            if (state.commandUntil() > now) {
                holder.result = AcquireResult.blocked(state.commandUntil() - now, state.commandUntil());
                return state;
            }

            long until = safeAdd(now, cooldownMillis);
            holder.result = AcquireResult.acquired(until);
            return new CooldownState(until, state.guiUntil());
        });
        return holder.result;
    }

    public CooldownState markGui(UUID playerId, long now, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return this.state(playerId, now);
        }

        long until = safeAdd(now, cooldownMillis);
        return this.states.compute(playerId, (ignored, current) -> {
            CooldownState state = current == null ? CooldownState.EMPTY : current;
            return new CooldownState(state.commandUntil(), Math.max(state.guiUntil(), until));
        });
    }

    public AcquireResult tryAcquireGui(UUID playerId, long now, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return AcquireResult.acquired(now);
        }

        AcquireHolder holder = new AcquireHolder();
        this.states.compute(playerId, (ignored, current) -> {
            CooldownState state = current == null ? CooldownState.EMPTY : current;
            if (state.guiUntil() > now) {
                holder.result = AcquireResult.blocked(state.guiUntil() - now, state.guiUntil());
                return state;
            }

            long until = safeAdd(now, cooldownMillis);
            holder.result = AcquireResult.acquired(until);
            return new CooldownState(state.commandUntil(), until);
        });
        return holder.result;
    }

    public void setGuiOpen(UUID playerId, boolean open) {
        if (open) {
            this.activeGuiPlayers.add(playerId);
            return;
        }
        this.activeGuiPlayers.remove(playerId);
    }

    public boolean isGuiOpen(UUID playerId) {
        return this.activeGuiPlayers.contains(playerId);
    }

    public CooldownState markCommand(UUID playerId, long now, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return this.state(playerId, now);
        }

        long until = safeAdd(now, cooldownMillis);
        return this.states.compute(playerId, (ignored, current) -> {
            CooldownState state = current == null ? CooldownState.EMPTY : current;
            return new CooldownState(Math.max(state.commandUntil(), until), state.guiUntil());
        });
    }

    public CooldownState state(UUID playerId, long now) {
        CooldownState state = this.states.get(playerId);
        if (state == null) {
            return CooldownState.EMPTY;
        }
        if (state.commandUntil() <= now && state.guiUntil() <= now) {
            this.states.remove(playerId, state);
            return CooldownState.EMPTY;
        }
        return state;
    }

    public void remove(UUID playerId) {
        this.states.remove(playerId);
        this.activeGuiPlayers.remove(playerId);
    }

    private static long safeAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    public record CooldownState(long commandUntil, long guiUntil) {
        private static final CooldownState EMPTY = new CooldownState(0L, 0L);
    }

    public record AcquireResult(boolean acquired, long remainingMillis, long until) {

        private static AcquireResult acquired(long until) {
            return new AcquireResult(true, 0L, until);
        }

        private static AcquireResult blocked(long remainingMillis, long until) {
            return new AcquireResult(false, Math.max(1L, remainingMillis), until);
        }
    }

    private static final class AcquireHolder {
        private AcquireResult result;
    }
}
