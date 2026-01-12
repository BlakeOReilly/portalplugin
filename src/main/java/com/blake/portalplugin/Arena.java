package com.blake.portalplugin.arenas;

import com.blake.portalplugin.arenas.tasks.ArenaCountdownTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class Arena {

    private final String name;
    private boolean inUse;
    private boolean started;
    private int maxPlayers = 100;

    private final List<Location> spawnPoints = new ArrayList<>();
    private final List<UUID> playersInArena = new ArrayList<>();

    // Tracks everyone who participated in this game instance
    private final Set<UUID> allPlayersEverJoined = new HashSet<>();

    private ArenaCountdownTask countdownTask = null;

    private final Map<Material, List<Location>> resetBlocks = new HashMap<>();

    // NEW: which game this arena instance is currently running (e.g. "pvp", "spleef")
    private String assignedGame = null;

    public Arena(String name) {
        this.name = name.toLowerCase();
        this.inUse = false;
        this.started = false;
    }

    public String getName() {
        return name;
    }

    public boolean isInUse() { return inUse; }
    public void setInUse(boolean inUse) { this.inUse = inUse; }

    public boolean hasStarted() { return started; }
    public void setStarted(boolean started) { this.started = started; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public void addSpawn(Location loc) { spawnPoints.add(loc); }
    public List<Location> getSpawnPoints() { return spawnPoints; }

    public void addPlayer(Player player) {
        UUID id = player.getUniqueId();
        if (!playersInArena.contains(id)) {
            playersInArena.add(id);
        }
        allPlayersEverJoined.add(id);
    }

    public void removePlayer(Player player) {
        playersInArena.remove(player.getUniqueId());
    }

    public boolean isPlayerInArena(Player player) {
        return playersInArena.contains(player.getUniqueId());
    }

    public List<UUID> getPlayers() {
        return playersInArena;
    }

    public List<Player> getOnlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : playersInArena) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    public Set<UUID> getAllPlayersEverJoined() {
        return new HashSet<>(allPlayersEverJoined);
    }

    public boolean hasCountdown() { return countdownTask != null; }

    public void startCountdown() {
        if (countdownTask != null) return;

        countdownTask = new ArenaCountdownTask(this);

        Plugin plugin = Bukkit.getPluginManager().getPlugin("PortalPlugin");
        if (plugin == null) {
            System.out.println("[Arena] ERROR: Could not find plugin PortalPlugin");
            return;
        }

        countdownTask.runTaskTimer(plugin, 20, 20);
    }

    public void clearCountdown() {
        countdownTask = null;
    }

    public void broadcast(String msg) {
        for (Player p : getOnlinePlayers()) {
            p.sendMessage(msg.replace("&", "§"));
        }
    }

    public Map<Material, List<Location>> getResetBlocks() { return resetBlocks; }

    public void clearResetBlocks(Material material) {
        resetBlocks.remove(material);
    }

    public void addResetBlock(Material m, Location loc) {
        resetBlocks.computeIfAbsent(m, k -> new ArrayList<>()).add(loc);
    }

    // NEW: assigned game getters/setters
    public String getAssignedGame() {
        return assignedGame;
    }

    public void setAssignedGame(String game) {
        if (game == null || game.isBlank()) {
            this.assignedGame = null;
        } else {
            this.assignedGame = game.toLowerCase();
        }
    }

    public boolean matchesAssignedGame(String game) {
        if (game == null) return false;
        if (assignedGame == null) return false;
        return assignedGame.equalsIgnoreCase(game);
    }

    public String getAssignedGameDisplayName() {
        if (assignedGame == null || assignedGame.isBlank()) return "Game";

        String g = assignedGame.trim();
        // Short names like "pvp" look better uppercased
        if (g.length() <= 4) return g.toUpperCase();

        // Otherwise, capitalize first letter
        return Character.toUpperCase(g.charAt(0)) + g.substring(1).toLowerCase();
    }

    public void resetArenaAfterWin() {
        setStarted(false);
        setInUse(false);
        clearCountdown();
        playersInArena.clear();
        setAssignedGame(null); // NEW: allow arena to be used by a different game later
        // We intentionally do NOT clear allPlayersEverJoined here;
        // that happens only after stats are recorded.
    }

    public boolean abortCountdownIfNotEnoughPlayers() {
        if (!hasStarted() && hasCountdown() && playersInArena.size() < 2) {
            broadcast("&cCountdown stopped – not enough players.");
            clearCountdown();
            return true;
        }
        return false;
    }

    public void clearParticipantsAfterStats() {
        allPlayersEverJoined.clear();
    }
}
