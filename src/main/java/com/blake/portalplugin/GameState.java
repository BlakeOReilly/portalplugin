package com.blake.portalplugin;

public enum GameState {
    HUB,
    ARENA,
    SPLEEF,
    PVP,
    SUMO,
    BLAST,
    SPECTATOR,
    ADMIN;

    public static GameState fromString(String s) {
        if (s == null) return null;
        try {
            return GameState.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
