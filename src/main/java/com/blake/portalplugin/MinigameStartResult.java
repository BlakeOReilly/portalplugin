package com.blake.portalplugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MinigameStartResult {

    private final boolean started;
    private final List<UUID> consumed;
    private final String message;

    public MinigameStartResult(boolean started, List<UUID> consumed, String message) {
        this.started = started;
        this.consumed = consumed == null ? new ArrayList<>() : new ArrayList<>(consumed);
        this.message = message;
    }

    public static MinigameStartResult ok(List<UUID> consumed) {
        return new MinigameStartResult(true, consumed, null);
    }

    public static MinigameStartResult fail(String message) {
        return new MinigameStartResult(false, new ArrayList<>(), message);
    }

    public boolean isStarted() {
        return started;
    }

    public List<UUID> getConsumed() {
        return new ArrayList<>(consumed);
    }

    public String getMessage() {
        return message;
    }
}