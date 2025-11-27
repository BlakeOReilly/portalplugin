// src/main/java/com/blake/portalplugin/PortalPlugin.java
package com.blake.portalplugin;

import com.blake.portalplugin.arenas.ArenaManager;
import com.blake.portalplugin.commands.*;
import com.blake.portalplugin.currency.CurrencyManager;
import com.blake.portalplugin.holograms.HologramManager;
import com.blake.portalplugin.listeners.*;
import com.blake.portalplugin.queues.GameQueueManager;
import com.blake.portalplugin.registry.ServerRegistryManager;
import com.blake.portalplugin.ranks.RankManager;
import com.blake.portalplugin.scoreboard.ScoreboardManager;
import com.blake.portalplugin.stats.DatabaseManager;
import com.blake.portalplugin.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class PortalPlugin extends JavaPlugin {

    private GameStateManager gameStateManager;
    private ArenaManager arenaManager;
    private GameQueueManager queueManager;
    private HubSpawnManager hubSpawnManager;
    private ArenaEliminationHandler arenaEliminationHandler;
    private ScoreboardManager scoreboardManager;

    private DatabaseManager databaseManager;
    private StatsManager statsManager;

    private CurrencyManager currencyManager;
    private HologramManager hologramManager;

    private ServerRegistryManager serverRegistryManager;

    private RankManager rankManager;
    private WinLocationManager winLocationManager;

    private CollectiblesManager collectiblesManager;
    private NavigationManager navigationManager;
    private CosmeticsManager cosmeticsManager;

    private final List<String> spawnSignEntries = new ArrayList<>();

    @Override
    public void onEnable() {

        saveDefaultConfig();

        // Plugin messaging channel for Velocity/BungeeCord
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        this.gameStateManager = new GameStateManager(this);
        this.arenaManager = new ArenaManager(this);
        this.queueManager = new GameQueueManager(arenaManager, gameStateManager, this);
        this.hubSpawnManager = new HubSpawnManager(this);

        this.winLocationManager = new WinLocationManager(this);

        this.collectiblesManager = new CollectiblesManager(this);
        this.navigationManager = new NavigationManager(this);
        this.cosmeticsManager = new CosmeticsManager(this);

        List<String> savedQueues = getConfig().getStringList("queues");
        queueManager.loadQueuesFromConfig(savedQueues);

        List<String> savedSigns = getConfig().getStringList("queue-signs");
        queueManager.loadSignsFromConfig(savedSigns);

        spawnSignEntries.clear();
        spawnSignEntries.addAll(getConfig().getStringList("spawn-signs"));

        Bukkit.getScheduler().runTask(this, () -> queueManager.restoreSignMetadata(this));
        Bukkit.getScheduler().runTask(this, this::restoreSpawnSignMetadata);

        this.databaseManager = new DatabaseManager(this);
        this.statsManager = new StatsManager(this, databaseManager);
        statsManager.ensureTable();

        this.currencyManager = new CurrencyManager(this, databaseManager);
        currencyManager.ensureTable();

        this.rankManager = new RankManager(this, databaseManager);
        rankManager.ensureTable();

        // Holograms (persistent leaderboards)
        this.hologramManager = new HologramManager(this);
        hologramManager.loadPersistentHolograms(statsManager);

        this.arenaEliminationHandler =
                new ArenaEliminationHandler(this, gameStateManager, hubSpawnManager, arenaManager, statsManager);

        this.scoreboardManager =
                new ScoreboardManager(this, gameStateManager, arenaManager, statsManager, rankManager);

        this.serverRegistryManager = new ServerRegistryManager(this, databaseManager);
        serverRegistryManager.ensureTable();

        String activeGame = getActiveGame();
        String host = getConfig().getString("server-registry.hostname", "127.0.0.1");
        int port = getServer().getPort();
        serverRegistryManager.registerThisServer(activeGame, host, port);

        registerCommands();
        registerListeners();

        if (serverRegistryManager.getServerCode() != null) {
            getLogger().info("[PortalPlugin] This server code: " + serverRegistryManager.getServerCode());
        }
    }

    @Override
    public void onDisable() {

        if (arenaManager != null) arenaManager.saveArenasToFile();
        if (gameStateManager != null) gameStateManager.clearAllOnline();
        if (hologramManager != null) hologramManager.clearAll();

        if (queueManager != null) {
            getConfig().set("queues", queueManager.getQueueNames());
            getConfig().set("queue-signs", queueManager.getSignEntries());
        }

        getConfig().set("spawn-signs", new ArrayList<>(spawnSignEntries));

        saveConfig();
    }

    private void registerCommands() {

        if (getCommand("gamestate") != null) {
            var cmd = new GameStateCommand(gameStateManager);
            getCommand("gamestate").setExecutor(cmd);
            getCommand("gamestate").setTabCompleter(cmd);
        }

        if (getCommand("fillhunger") != null)
            getCommand("fillhunger").setExecutor(new FillHungerCommand());

        if (getCommand("fillhealth") != null)
            getCommand("fillhealth").setExecutor(new FillHealthCommand());

        if (getCommand("arena") != null)
            getCommand("arena").setExecutor(new ArenaCommand(arenaManager));

        if (getCommand("arenamaximum") != null)
            getCommand("arenamaximum").setExecutor(new ArenaMaximumCommand(arenaManager));

        if (getCommand("creategame") != null)
            getCommand("creategame").setExecutor(new CreateGameCommand(queueManager, this));

        if (getCommand("join") != null)
            getCommand("join").setExecutor(new JoinGameCommand(queueManager));

        if (getCommand("createsign") != null)
            getCommand("createsign").setExecutor(new CreateSignCommand(queueManager, this));

        if (getCommand("createspawnsign") != null)
            getCommand("createspawnsign").setExecutor(new CreateSpawnSignCommand(this));

        if (getCommand("setspawn") != null)
            getCommand("setspawn").setExecutor(new SetSpawnCommand(hubSpawnManager));

        if (getCommand("stats") != null)
            getCommand("stats").setExecutor(new StatsCommand(statsManager));

        if (getCommand("createscoreboard") != null)
            getCommand("createscoreboard").setExecutor(
                    new CreateScoreboardCommand(statsManager, hologramManager)
            );

        if (getCommand("gameset") != null)
            getCommand("gameset").setExecutor(new GameSetCommand(this));

        if (getCommand("currency") != null)
            getCommand("currency").setExecutor(
                    new CurrencyCommand(currencyManager, gameStateManager)
            );

        if (getCommand("clearsigns") != null)
            getCommand("clearsigns").setExecutor(
                    new ClearSignsCommand(queueManager, gameStateManager, this)
            );

        if (getCommand("gamewinloc") != null)
            getCommand("gamewinloc").setExecutor(new SetWinLocationCommand(winLocationManager));
    }

    private void registerListeners() {

        Bukkit.getPluginManager().registerEvents(
                new PlayerJoinQuitListener(gameStateManager, arenaManager, arenaEliminationHandler, hubSpawnManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new PlayerArenaEliminationListener(arenaManager, arenaEliminationHandler),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new PlayerDeathSpawnListener(arenaManager, arenaEliminationHandler, gameStateManager, hubSpawnManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new QueueSignListener(queueManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new SpawnSignListener(hubSpawnManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new BlockBreakListener(gameStateManager, this),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new CollectiblesGUIListener(this, collectiblesManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new NavigationGUIListener(this, navigationManager),
                this
        );

        // FIXED: Correct constructor (plugin, cosmeticsManager)
        Bukkit.getPluginManager().registerEvents(
                new CosmeticsGUIListener(this, cosmeticsManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler
                    public void onJoin(PlayerJoinEvent e) {
                        Bukkit.getScheduler().runTask(
                                PortalPlugin.this,
                                () -> scoreboardManager.refreshAll()
                        );
                    }
                },
                this
        );
    }

    private void restoreSpawnSignMetadata() {
        for (String entry : spawnSignEntries) {
            try {
                String[] parts = entry.split(",");
                if (parts.length < 4) continue;

                String worldName = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);

                if (Bukkit.getWorld(worldName) == null) continue;

                Block b = Bukkit.getWorld(worldName).getBlockAt(x, y, z);
                b.setMetadata("spawn_sign", new FixedMetadataValue(this, true));

            } catch (Exception ignored) {}
        }
    }

    public void registerSpawnSign(Block signBlock) {
        if (signBlock == null || signBlock.getWorld() == null) return;

        String entry = signBlock.getWorld().getName() + ","
                + signBlock.getX() + ","
                + signBlock.getY() + ","
                + signBlock.getZ();

        spawnSignEntries.add(entry);
        signBlock.setMetadata("spawn_sign", new FixedMetadataValue(this, true));
    }

    public RankManager getRankManager() { return rankManager; }
    public GameStateManager getGameStateManager() { return gameStateManager; }
    public GameQueueManager getQueueManager() { return queueManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public HubSpawnManager getHubSpawnManager() { return hubSpawnManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public CurrencyManager getCurrencyManager() { return currencyManager; }
    public ServerRegistryManager getServerRegistryManager() { return serverRegistryManager; }
    public WinLocationManager getWinLocationManager() { return winLocationManager; }
    public CollectiblesManager getCollectiblesManager() { return collectiblesManager; }
    public NavigationManager getNavigationManager() { return navigationManager; }
    public CosmeticsManager getCosmeticsManager() { return cosmeticsManager; }

    public String getActiveGame() {
        return getConfig().getString("active-game", "none");
    }

    public void setActiveGame(String game) {
        if (game == null) {
            getConfig().set("active-game", null);
        } else {
            getConfig().set("active-game", game.toLowerCase());
        }
        saveConfig();
    }
}
