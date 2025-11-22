package com.blake.portalplugin.stats;

public class PlayerStats {

    private final String uuid;
    private final String playerName;
    private final String gamemode;
    private final int wins;
    private final int losses;

    public PlayerStats(String uuid, String playerName, String gamemode, int wins, int losses) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.gamemode = gamemode;
        this.wins = wins;
        this.losses = losses;
    }

    public String getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getGamemode() {
        return gamemode;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }
}
