package com.blake.portalplugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlastCooldownTracker {

    public enum CooldownType {
        BASIC,
        BIG,
        SCATTER,
        RANGE,
        STRIKE,
        DASH
    }

    private static final class CooldownState {
        private long endsAtMs;
        private long durationMs;
    }

    private final Map<UUID, EnumMap<CooldownType, CooldownState>> cooldowns = new HashMap<>();

    public boolean isReady(UUID playerId, CooldownType type) {
        return getRemainingMs(playerId, type) <= 0;
    }

    public boolean isReady(UUID playerId, CooldownType type, long nowMs) {
        return getRemainingMs(playerId, type, nowMs) <= 0;
    }

    public void startCooldown(UUID playerId, CooldownType type, long durationMs) {
        if (playerId == null || type == null) return;
        long now = System.currentTimeMillis();
        startCooldown(playerId, type, durationMs, now);
    }

    public void startCooldown(UUID playerId, CooldownType type, long durationMs, long nowMs) {
        if (playerId == null || type == null) return;
        long safeDuration = Math.max(1L, durationMs);
        CooldownState state = new CooldownState();
        state.durationMs = safeDuration;
        state.endsAtMs = nowMs + safeDuration;
        cooldowns.computeIfAbsent(playerId, k -> new EnumMap<>(CooldownType.class)).put(type, state);
    }

    public long getRemainingMs(UUID playerId, CooldownType type) {
        return getRemainingMs(playerId, type, System.currentTimeMillis());
    }

    public long getRemainingMs(UUID playerId, CooldownType type, long nowMs) {
        if (playerId == null || type == null) return 0L;
        CooldownState state = getState(playerId, type);
        if (state == null) return 0L;
        long remaining = state.endsAtMs - nowMs;
        return Math.max(0L, remaining);
    }

    public long getDurationMs(UUID playerId, CooldownType type) {
        if (playerId == null || type == null) return 0L;
        CooldownState state = getState(playerId, type);
        if (state == null) return 0L;
        return Math.max(0L, state.durationMs);
    }

    public double getProgress(UUID playerId, CooldownType type) {
        long duration = getDurationMs(playerId, type);
        if (duration <= 0) return 1.0;
        long remaining = getRemainingMs(playerId, type);
        if (remaining <= 0) return 1.0;
        double progress = 1.0 - (double) remaining / (double) duration;
        return Math.max(0.0, Math.min(1.0, progress));
    }

    private CooldownState getState(UUID playerId, CooldownType type) {
        EnumMap<CooldownType, CooldownState> map = cooldowns.get(playerId);
        if (map == null) return null;
        return map.get(type);
    }
}
