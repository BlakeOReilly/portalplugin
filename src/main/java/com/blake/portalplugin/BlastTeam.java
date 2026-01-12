package com.blake.portalplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum BlastTeam {

    RED("red", ChatColor.RED, Material.RED_WOOL),
    GREEN("green", ChatColor.GREEN, Material.GREEN_WOOL),
    YELLOW("yellow", ChatColor.YELLOW, Material.YELLOW_WOOL),
    BLUE("blue", ChatColor.BLUE, Material.BLUE_WOOL);

    private final String key;
    private final ChatColor color;
    private final Material woolMaterial;

    BlastTeam(String key, ChatColor color, Material woolMaterial) {
        this.key = key;
        this.color = color;
        this.woolMaterial = woolMaterial;
    }

    public String getKey() {
        return key;
    }

    /**
     * ChatColor used for messages (and for color mapping to scoreboard teams).
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Wool blocks that this team can place / receives on spawn.
     */
    public Material getWoolMaterial() {
        return woolMaterial;
    }

    /**
     * Stable scoreboard team name.
     */
    public String getScoreboardTeamName() {
        return "blast_" + key;
    }

    /**
     * Resolve team from "red/green/yellow/blue" (case-insensitive).
     */
    public static BlastTeam fromKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        for (BlastTeam t : values()) {
            if (t.key.equalsIgnoreCase(k)) return t;
        }
        return null;
    }
}
