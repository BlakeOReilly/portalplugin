package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CustomScoreboardManager {
    private final HubStatsPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public CustomScoreboardManager(HubStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyHubBoard(Player p) {
        Scoreboard sb = getOrCreate(p);
        sb.clearSlot(DisplaySlot.SIDEBAR);
        Objective obj = ensureObjective(sb, "hub", "§a§lPORTAL");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        setLine(obj, 4, "§7IP: §fexample.net");
        setLine(obj, 3, "§7Rank: §f" + plugin.getPlayerRank(p));
        setLine(obj, 2, "§7Coins: §f" + plugin.getPlayerCoins(p));
        setLine(obj, 1, "§7State: §fHUB");

        p.setScoreboard(sb);
    }

    public void applyQueueBoard(Player p, int position) {
        Scoreboard sb = getOrCreate(p);
        sb.clearSlot(DisplaySlot.SIDEBAR);
        Objective obj = ensureObjective(sb, "queue", "§e§lQUEUING");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        setLine(obj, 3, "§7Game: §fSpleef");
        setLine(obj, 2, "§7Position: §f" + position);
        setLine(obj, 1, "§7Waiting for players...");

        p.setScoreboard(sb);
    }

    public void applyGamePrepBoard(Player p, String arena) {
        Scoreboard sb = getOrCreate(p);
        sb.clearSlot(DisplaySlot.SIDEBAR);
        Objective obj = ensureObjective(sb, "prep", "§b§lGAME PREP");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        setLine(obj, 2, "§7Arena: §f" + arena);
        setLine(obj, 1, "§7Starting soon...");

        p.setScoreboard(sb);
    }

    public void clear(Player p) {
        boards.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    private Scoreboard getOrCreate(Player p) {
        return boards.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
    }

    private Objective ensureObjective(Scoreboard sb, String name, String title) {
        Objective o = sb.getObjective(name);
        if (o == null) {
            o = sb.registerNewObjective(name, "dummy", title);
        } else {
            o.setDisplayName(title);
        }
        return o;
    }

    private void setLine(Objective o, int score, String text) {
        Score s = o.getScore(text);
        s.setScore(score);
    }
}
