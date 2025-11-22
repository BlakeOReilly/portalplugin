package com.blake.portalplugin.ranks;

public enum Rank {
    NONE("None"),
    ALPHA("Alpha"),
    VALOR("Valor"),
    MYTHIC("Mythic"),
    TRAINEE("Trainee"),
    MOD("Mod"),
    SRMOD("SrMod"),
    ADMIN("Admin"),
    OWNER("Owner");

    private final String display;

    Rank(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    public static Rank fromString(String s) {
        if (s == null) return NONE;
        try {
            return Rank.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
