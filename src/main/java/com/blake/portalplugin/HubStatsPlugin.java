package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class HubStatsPlugin extends JavaPlugin {
    private static HubStatsPlugin instance;

    private CustomScoreboardManager scoreboardManager;
    private ArenaManager arenaManager;
    private GameQueueManager queueManager;
    private GameStateManager stateManager;

    @Override
    public void onEnable() {
        instance = this;

        // Managers
        this.scoreboardManager = new CustomScoreboardManager(this);
        this.arenaManager = new ArenaManager(this);
        this.stateManager = new GameStateManager(this, scoreboardManager);
        this.queueManager = new GameQueueManager(this, scoreboardManager, stateManager);

        // Listeners
        getServer().getPluginManager().registerEvents(new GameStateListener(stateManager), this);
        getServer().getPluginManager().registerEvents(new SignListener(queueManager, stateManager), this);
        // Ensure SpleefGameListener is active (it calls manager.eliminate(...))
        getServer().getPluginManager().registerEvents(new SpleefGameListener(stateManager), this);

        // Commands (ensure these exist in plugin.yml)
        getCommand("gamestate").setExecutor(new GameStateCommand(stateManager));
        getCommand("createsign").setExecutor(new CreateSignCommand());
        getCommand("sign").setExecutor(new SignCommand(queueManager)); // optional helper

        getLogger().info("HubStatsPlugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("HubStatsPlugin disabled.");
    }

    public static HubStatsPlugin getInstance() { return instance; }

    public CustomScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public GameQueueManager getQueueManager() { return queueManager; }
    public GameStateManager getGameStateManager() { return stateManager; }

    /** Convenience used by older code paths */
    public void setPlayerState(Player p, GameState state) {
        stateManager.setGameState(p, state, null);
    }

    /** Hub spawn; adjust as needed or load from config */
    public Location getHubLocation() {
        World w = Bukkit.getWorlds().get(0);
        return new Location(w, 0.5, w.getSpawnLocation().getY(), 0.5, 0, 0);
    }

    public void giveHubCompass(Player p) {
        ItemStack comp = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) comp.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aServer Selector");
            comp.setItemMeta(meta);
        }
        p.getInventory().setItem(0, comp);
    }

    // Simple placeholders (wire your real stat system here)
    public int getPlayerCoins(Player p) { return 0; }
    public String getPlayerRank(Player p) { return "Default"; }
    public int getPlayerLevel(Player p) { return 1; }
}
