package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HubStatsPlugin extends JavaPlugin {
    private static HubStatsPlugin instance;

    private CustomScoreboardManager scoreboardManager;
    private GameQueueManager        queueManager;
    private GameStateManager        stateManager;
    private ArenaManager            arenaManager;

    private final Map<UUID, GameState> playerStates = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        scoreboardManager = new CustomScoreboardManager(this);
        arenaManager      = new ArenaManager(this);
        queueManager      = new GameQueueManager(this, scoreboardManager, null);
        stateManager      = new GameStateManager(this, scoreboardManager, queueManager);
        queueManager      = new GameQueueManager(this, scoreboardManager, stateManager);

        getCommand("gamestate").setExecutor(new GameStateCommand(stateManager));
        getCommand("createsign").setExecutor(new CreateSignCommand());
        getCommand("savered").setExecutor((s, c, l, a) -> {
            arenaManager.saveRedArena();
            s.sendMessage("§aRed arena saved."); return true;
        });
        getCommand("saveblue").setExecutor((s, c, l, a) -> {
            arenaManager.saveBlueArena();
            s.sendMessage("§aBlue arena saved."); return true;
        });

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this, stateManager), this);
        pm.registerEvents(new SignListener(queueManager, stateManager), this);
        pm.registerEvents(new PlayerQuitListener(queueManager, stateManager), this);
        pm.registerEvents(new GameStateListener(stateManager), this);
        pm.registerEvents(new SpleefBlockListener(stateManager), this);
        pm.registerEvents(new SpleefGameListener(stateManager), this);

        getLogger().info("HubStatsPlugin enabled");
    }

    public static HubStatsPlugin getInstance() {
        return instance;
    }

    public ArenaManager getArenaManager()         { return arenaManager; }
    public GameQueueManager getQueueManager()     { return queueManager; }
    public GameStateManager getStateManager()     { return stateManager; }
    public CustomScoreboardManager getScoreboardManager() { return scoreboardManager; }

    public void setPlayerState(Player p, GameState s) {
        playerStates.put(p.getUniqueId(), s);
    }
    public GameState getPlayerState(Player p) {
        return playerStates.getOrDefault(p.getUniqueId(), GameState.HUB);
    }
    public void clearPlayerState(Player p) {
        playerStates.remove(p.getUniqueId());
    }

    public Location getHubLocation() {
        return new Location(Bukkit.getWorld("world"), 6, -58, -11, 0f, 0f);
    }
    public void giveHubCompass(Player p) {
        ItemStack comp = new ItemStack(Material.COMPASS);
        CompassMeta m = (CompassMeta) comp.getItemMeta();
        if (m != null) {
            m.setDisplayName("§aServer Selector");
            comp.setItemMeta(m);
        }
        p.getInventory().setItem(0, comp);
    }

    public int getPlayerCoins(Player p)  { return PlayerStatsManager.getCoins(p.getUniqueId()); }
    public String getPlayerRank(Player p) { return "Default"; }
    public int getPlayerLevel(Player p)   { return 1; }
}
