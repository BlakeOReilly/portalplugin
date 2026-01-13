package com.blake.portalplugin.scoreboard;

import com.blake.portalplugin.*;
import com.blake.portalplugin.arenas.Arena;
import com.blake.portalplugin.arenas.ArenaManager;
import com.blake.portalplugin.currency.CurrencyManager;
import com.blake.portalplugin.ranks.Rank;
import com.blake.portalplugin.ranks.RankManager;
import com.blake.portalplugin.registry.ServerRegistryManager;
import com.blake.portalplugin.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class ScoreboardManager {

    private final PortalPlugin plugin;
    private final GameStateManager gameStateManager;
    private final ArenaManager arenaManager;
    private final StatsManager statsManager;
    private final RankManager rankManager;

    private static final String BLANK_1 = " ";
    private static final String BLANK_2 = "  ";
    private static final String BLANK_3 = "   ";
    private static final String BLANK_4 = "    ";

    public ScoreboardManager(PortalPlugin plugin,
                             GameStateManager gsm,
                             ArenaManager arenaManager,
                             StatsManager statsManager,
                             RankManager rankManager) {
        this.plugin = plugin;
        this.gameStateManager = gsm;
        this.arenaManager = arenaManager;
        this.statsManager = statsManager;
        this.rankManager = rankManager;
    }

    public void applyScoreboard(Player player) {
        if (player == null || !player.isOnline()) return;

        GameState state = gameStateManager.getGameState(player);

        switch (state) {
            case SPLEEF -> applySpleefBoard(player);
            case ARENA  -> applyArenaBoard(player);
            case PVP    -> applyPvpBoard(player);
            case SUMO   -> applySumoBoard(player);
            case BLAST  -> applyBlastBoard(player);
            case SPECTATOR -> applySpectatorBoard(player);
            default     -> applyHubBoard(player);
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyScoreboard(player);
        }
    }

    // --------------------------------------------------------------------
    // BLAST BOARD
    // --------------------------------------------------------------------
    private void applyBlastBoard(Player p) {
        BlastMinigameManager bm = plugin.getBlastMinigameManager();

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("blast", "dummy", "§6§lBLAST");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 15;

        // Make borders unique so scoreboard lines don't collide
        obj.getScore("§7--------------------").setScore(line--);

        String time = "00:00";
        if (bm != null && bm.isInProgress()) {
            time = formatSeconds(bm.getSecondsRemaining());
        }
        obj.getScore("§fTime: §e" + time).setScore(line--);

        obj.getScore(BLANK_1).setScore(line--);

        BlastTeam team = bm != null ? bm.getTeam(p) : null;
        obj.getScore("§fTeam: " + (team != null ? (team.getColor() + team.getKey().toUpperCase()) : "§7NONE")).setScore(line--);

        obj.getScore(BLANK_2).setScore(line--);

        int tokens = (bm != null) ? bm.getElimTokens(p) : 0;
        obj.getScore("§fElim Tokens: §b" + tokens).setScore(line--);

        // REQUIRED: space between Elim Tokens and Team Lives
        obj.getScore(BLANK_3).setScore(line--);

        obj.getScore("§fTeam Lives").setScore(line--);

        for (BlastTeam t : BlastTeam.values()) {
            int lives = bm != null ? bm.getTeamLives(t) : 0;
            String label = t.getColor() + t.getKey().toUpperCase() + "§7: §f" + lives;
            obj.getScore(label).setScore(line--);
        }

        obj.getScore(BLANK_4).setScore(line--);
        obj.getScore("www.example.com").setScore(line--);
        obj.getScore("§8--------------------").setScore(line--);

        p.setScoreboard(board);

        if (bm != null && bm.isInProgress()) {
            bm.applyBlastTeamsToScoreboard(p);
        }
    }

    private String formatSeconds(int seconds) {
        if (seconds < 0) seconds = 0;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    // --------------------------------------------------------------------
    // SPECTATOR BOARD
    // --------------------------------------------------------------------
    private void applySpectatorBoard(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("spectator", "dummy", "§7§lSPECTATOR");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        BlastMinigameManager bm = plugin.getBlastMinigameManager();
        boolean blastActive = bm != null && bm.isInProgress();
        String gameName = blastActive ? "§6BLAST" : "§7NONE";
        String time = blastActive ? formatSeconds(bm.getSecondsRemaining()) : "--:--";

        int line = 15;

        obj.getScore("§7--------------------").setScore(line--);

        obj.getScore("§fGame: " + gameName).setScore(line--);
        obj.getScore("§fTime: §e" + time).setScore(line--);

        obj.getScore(BLANK_1).setScore(line--);

        obj.getScore("§fTeam Lives").setScore(line--);
        for (BlastTeam t : BlastTeam.values()) {
            int lives = blastActive ? bm.getTeamLives(t) : 0;
            String label = t.getColor() + t.getKey().toUpperCase() + "§7: §f" + lives;
            obj.getScore(label).setScore(line--);
        }

        obj.getScore(BLANK_2).setScore(line--);

        String serverCode = getServerCode();
        obj.getScore("§fServer").setScore(line--);
        obj.getScore("§e" + serverCode).setScore(line--);

        obj.getScore(BLANK_3).setScore(line--);
        obj.getScore("www.example.com").setScore(line--);
        obj.getScore("§8--------------------").setScore(line--);

        p.setScoreboard(board);
    }

    // --------------------------------------------------------------------
    // HUB BOARD
    // --------------------------------------------------------------------
    private void applyHubBoard(Player p) {

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("hub", "dummy", "§b§lHUB");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String serverCode = getServerCode();
        CurrencyManager cur = plugin.getCurrencyManager();
        int coins = cur.getCoins(p.getUniqueId());
        int gems = cur.getGems(p.getUniqueId());
        Rank rank = rankManager.getRankOrDefault(p.getUniqueId());

        int line = 15;

        obj.getScore("--------------------").setScore(line--);
        obj.getScore("§fServer").setScore(line--);
        obj.getScore("§e" + serverCode).setScore(line--);
        obj.getScore(BLANK_1).setScore(line--);

        obj.getScore("§fRank").setScore(line--);
        obj.getScore("§a" + rank.name()).setScore(line--);
        obj.getScore(BLANK_2).setScore(line--);

        obj.getScore("§fGems").setScore(line--);
        obj.getScore("§d" + gems).setScore(line--);

        obj.getScore("§fCoins").setScore(line--);
        obj.getScore("§6" + coins).setScore(line--);

        obj.getScore(BLANK_3).setScore(line--);

        obj.getScore("www.example.com").setScore(line--);
        obj.getScore("--------------------").setScore(line--);

        p.setScoreboard(board);
    }

    // --------------------------------------------------------------------
    // ARENA BOARD
    // --------------------------------------------------------------------
    private void applyArenaBoard(Player p) {

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("arena", "dummy", "§e§lARENA");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String serverCode = getServerCode();
        Rank rank = rankManager.getRankOrDefault(p.getUniqueId());

        Arena arena = arenaManager.getArenaPlayerIsIn(p);
        int players = arena != null ? arena.getPlayers().size() : 0;

        int line = 15;

        obj.getScore("--------------------").setScore(line--);

        obj.getScore("§fServer").setScore(line--);
        obj.getScore("§e" + serverCode).setScore(line--);
        obj.getScore(BLANK_1).setScore(line--);

        obj.getScore("§fPlayers Joined").setScore(line--);
        obj.getScore("§a" + players).setScore(line--);
        obj.getScore(BLANK_2).setScore(line--);

        obj.getScore("§fRank").setScore(line--);
        obj.getScore("§a" + rank.name()).setScore(line--);

        obj.getScore(BLANK_3).setScore(line--);

        obj.getScore("www.example.com").setScore(line--);
        obj.getScore("--------------------").setScore(line--);

        p.setScoreboard(board);
    }

    // --------------------------------------------------------------------
    // SPLEEF BOARD
    // --------------------------------------------------------------------
    private void applySpleefBoard(Player p) {

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("spleef", "dummy", "§c§lSPLEEF");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String serverCode = getServerCode();
        Rank rank = rankManager.getRankOrDefault(p.getUniqueId());

        Arena arena = arenaManager.getArenaPlayerIsIn(p);
        int alive = arena != null ? arena.getPlayers().size() : 0;

        int line = 15;

        obj.getScore("--------------------").setScore(line--);

        obj.getScore("§fServer").setScore(line--);
        obj.getScore("§e" + serverCode).setScore(line--);
        obj.getScore(BLANK_1).setScore(line--);

        obj.getScore("§fPlayers Left").setScore(line--);
        obj.getScore("§c" + alive).setScore(line--);
        obj.getScore(BLANK_2).setScore(line--);

        obj.getScore("§fRank").setScore(line--);
        obj.getScore("§a" + rank.name()).setScore(line--);

        obj.getScore(BLANK_3).setScore(line--);

        obj.getScore("www.example.com").setScore(line--);
        obj.getScore("--------------------").setScore(line--);

        p.setScoreboard(board);
    }

    // --------------------------------------------------------------------
    // PVP BOARD
    // --------------------------------------------------------------------
    private void applyPvpBoard(Player p) {

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("pvp", "dummy", "§4§lPVP");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String serverCode = getServerCode();
        Rank rank = rankManager.getRankOrDefault(p.getUniqueId());

        int pvpPlayers = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (gameStateManager.getGameState(online) == GameState.PVP) {
                pvpPlayers++;
            }
        }

        int line = 15;

        obj.getScore("--------------------").setScore(line--);

        obj.getScore("§fServer").setScore(line--);
        obj.getScore("§e" + serverCode).setScore(line--);
        obj.getScore(BLANK_1).setScore(line--);

        obj.getScore("§fPlayers in PVP").setScore(line--);
        obj.getScore("§c" + pvpPlayers).setScore(line--);
        obj.getScore(BLANK_2).setScore(line--);

        obj.getScore("§fRank").setScore(line--);
        obj.getScore("§a" + rank.name()).setScore(line--);

        obj.getScore(BLANK_3).setScore(line--);

        obj.getScore("www.example.com").setScore(line--);
        obj.getScore("--------------------").setScore(line--);

        p.setScoreboard(board);
    }

    // --------------------------------------------------------------------
    // SUMO BOARD
    // --------------------------------------------------------------------
    private void applySumoBoard(Player p) {

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("sumo", "dummy", "§6§lSUMO");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String serverCode = getServerCode();
        Rank rank = rankManager.getRankOrDefault(p.getUniqueId());

        Arena arena = arenaManager.getArenaPlayerIsIn(p);
        int alive = arena != null ? arena.getPlayers().size() : 0;

        int line = 15;

        obj.getScore("--------------------").setScore(line--);

        obj.getScore("§fServer").setScore(line--);
        obj.getScore("§e" + serverCode).setScore(line--);
        obj.getScore(BLANK_1).setScore(line--);

        obj.getScore("§fPlayers Left").setScore(line--);
        obj.getScore("§c" + alive).setScore(line--);
        obj.getScore(BLANK_2).setScore(line--);

        obj.getScore("§fRank").setScore(line--);
        obj.getScore("§a" + rank.name()).setScore(line--);

        obj.getScore(BLANK_3).setScore(line--);

        obj.getScore("www.example.com").setScore(line--);
        obj.getScore("--------------------").setScore(line--);

        p.setScoreboard(board);
    }

    private String getServerCode() {
        ServerRegistryManager reg = plugin.getServerRegistryManager();
        if (reg == null || reg.getServerCode() == null) return "Unknown";
        return reg.getServerCode();
    }
}
