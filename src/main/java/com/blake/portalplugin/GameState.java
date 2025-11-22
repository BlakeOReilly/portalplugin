// src/main/java/com/blake/portalplugin/GameState.java
package com.blake.portalplugin;

public enum GameState {
    HUB,
    ARENA,
    SPLEEF,
    ADMIN;   // NEW

    public static GameState fromString(String s) {
        if (s == null) return null;
        try {
            return GameState.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
