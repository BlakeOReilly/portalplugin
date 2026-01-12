package com.blake.portalplugin.worldedit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClipboardManager {

    public enum FlipAxis { X, Z }

    private final Map<UUID, Clipboard> clipboards = new ConcurrentHashMap<>();

    public Clipboard getClipboard(UUID playerId) {
        return clipboards.get(playerId);
    }

    public void setClipboard(UUID playerId, Clipboard clipboard) {
        if (playerId == null || clipboard == null) return;
        clipboards.put(playerId, clipboard);
    }

    public boolean hasClipboard(UUID playerId) {
        return playerId != null && clipboards.containsKey(playerId);
    }

    public Clipboard rotate(UUID playerId, int degreesClockwise) {
        Clipboard cb = getClipboard(playerId);
        if (cb == null) return null;

        int deg = ((degreesClockwise % 360) + 360) % 360;
        if (deg != 90 && deg != 180 && deg != 270) deg = 90;

        Clipboard out = ClipboardTransforms.rotateY(cb, deg);
        setClipboard(playerId, out);
        return out;
    }

    public Clipboard flip(UUID playerId, FlipAxis axis) {
        Clipboard cb = getClipboard(playerId);
        if (cb == null) return null;

        Clipboard out = ClipboardTransforms.flip(cb, axis);
        setClipboard(playerId, out);
        return out;
    }
}
