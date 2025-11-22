package com.blake.portalplugin.scoreboard;

import com.blake.portalplugin.GameState;
import com.blake.portalplugin.GameStateManager;
import com.blake.portalplugin.PortalPlugin;
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
            default     -> applyHubBoard(player);
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyScoreboard(player);
        }
    }

    // --------------------------------------------------------------------
    // HUB BOARD (unchanged)
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
    // ARENA BOARD (Gems & Coins REMOVED)
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

        // Website ONLY
        obj.getScore("www.example.com").setScore(line--);

        obj.getScore("--------------------").setScore(line--);

        p.setScoreboard(board);
    }

    // --------------------------------------------------------------------
    // SPLEEF BOARD (Gems & Coins REMOVED)
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

        // Website ONLY
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
