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
import com.blake.portalplugin.worldedit.ClipboardManager;
import com.blake.portalplugin.worldedit.SelectionManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
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

    private SelectionManager selectionManager;

    // NEW: per-player clipboard for pcopy/ppaste/protate/pflip
    private ClipboardManager clipboardManager;

    private MinigameQueueManager minigameQueueManager;

    private BlastMinigameManager blastMinigameManager;

    private BlastGeneratorService blastGeneratorService;
    private BlastMiddleGeneratorService blastMiddleGeneratorService;

    // NEW: random map diamond spawns (30s, 5 per wave, max 20)
    private BlastDiamondSpawnerService blastDiamondSpawnerService;

    // NEW: BLAST powerups manager (used by blaster cooldown adjustments, etc.)
    private BlastPowerupManager blastPowerupManager;

    private final List<String> spawnSignEntries = new ArrayList<>();

    @Override
    public void onEnable() {

        saveDefaultConfig();

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        this.gameStateManager = new GameStateManager(this);
        this.arenaManager = new ArenaManager(this);
        this.queueManager = new GameQueueManager(arenaManager, gameStateManager, this);
        this.hubSpawnManager = new HubSpawnManager(this);

        this.winLocationManager = new WinLocationManager(this);

        this.collectiblesManager = new CollectiblesManager(this);
        this.navigationManager = new NavigationManager(this);
        this.cosmeticsManager = new CosmeticsManager(this);

        this.selectionManager = new SelectionManager();

        // NEW: clipboard manager
        this.clipboardManager = new ClipboardManager();

        this.blastMinigameManager = new BlastMinigameManager(this, gameStateManager);

        // NEW: powerups manager (keep before listeners register)
        this.blastPowerupManager = new BlastPowerupManager(this, gameStateManager);

        this.blastGeneratorService = new BlastGeneratorService(this, gameStateManager);
        this.blastGeneratorService.start();

        this.blastMiddleGeneratorService = new BlastMiddleGeneratorService(this, gameStateManager);
        this.blastMiddleGeneratorService.start();

        // NEW: diamond spawner
        this.blastDiamondSpawnerService = new BlastDiamondSpawnerService(this, gameStateManager);
        this.blastDiamondSpawnerService.start();

        this.minigameQueueManager = new MinigameQueueManager(this, gameStateManager);

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

        this.hologramsManagerInit();

        this.arenaEliminationHandler =
                new ArenaEliminationHandler(this, gameStateManager, hubSpawnManager, arenaManager, statsManager);

        this.scoreboardManager =
                new ScoreboardManager(this, gameStateManager, arenaManager, statsManager, rankManager);

        this.serverRegistryManager = new ServerRegistryManager(this, databaseManager);
        serverRegistryManager.ensureTable();

        updateEffectiveActiveGameAndRegistry();

        registerCommands();
        registerListeners();

        if (serverRegistryManager.getServerCode() != null) {
            getLogger().info("[PortalPlugin] This server code: " + serverRegistryManager.getServerCode());
        }
    }

    private void hologramsManagerInit() {
        this.hologramManager = new HologramManager(this);
        hologramManager.loadPersistentHolograms(statsManager);
    }

    @Override
    public void onDisable() {

        if (arenaManager != null) arenaManager.saveArenasToFile();
        if (gameStateManager != null) gameStateManager.clearAllOnline();
        if (hologramManager != null) hologramManager.clearAll();

        if (minigameQueueManager != null) {
            minigameQueueManager.shutdown();
        }

        if (blastGeneratorService != null) {
            blastGeneratorService.stop();
        }

        if (blastMiddleGeneratorService != null) {
            blastMiddleGeneratorService.stop();
        }

        if (blastDiamondSpawnerService != null) {
            blastDiamondSpawnerService.stop();
        }

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

        if (getCommand("pos1") != null)
            getCommand("pos1").setExecutor(new Pos1Command(selectionManager));

        if (getCommand("pos2") != null)
            getCommand("pos2").setExecutor(new Pos2Command(selectionManager));

        if (getCommand("pfill") != null) {
            var cmd = new FillRegionCommand(this, selectionManager);
            getCommand("pfill").setExecutor(cmd);
            getCommand("pfill").setTabCompleter(cmd);
        }

        if (getCommand("pclear") != null) {
            var cmd = new ClearRegionCommand(this, selectionManager);
            getCommand("pclear").setExecutor(cmd);
            getCommand("pclear").setTabCompleter(cmd);
        }

        // NEW: copy/paste/rotate/flip worldedit-style commands
        if (getCommand("pcopy") != null) {
            getCommand("pcopy").setExecutor(new CopyRegionCommand(this, selectionManager, clipboardManager));
        }

        if (getCommand("ppaste") != null) {
            var cmd = new PasteRegionCommand(this, clipboardManager);
            getCommand("ppaste").setExecutor(cmd);
            getCommand("ppaste").setTabCompleter(cmd);
        }

        if (getCommand("protate") != null) {
            var cmd = new RotateClipboardCommand(clipboardManager);
            getCommand("protate").setExecutor(cmd);
            getCommand("protate").setTabCompleter(cmd);
        }

        if (getCommand("pflip") != null) {
            var cmd = new FlipClipboardCommand(clipboardManager);
            getCommand("pflip").setExecutor(cmd);
            getCommand("pflip").setTabCompleter(cmd);
        }

        if (getCommand("hubtype") != null) {
            var cmd = new HubTypeCommand(this);
            getCommand("hubtype").setExecutor(cmd);
            getCommand("hubtype").setTabCompleter(cmd);
        }

        if (getCommand("setminigame") != null) {
            var cmd = new SetMinigameCommand(this);
            getCommand("setminigame").setExecutor(cmd);
            getCommand("setminigame").setTabCompleter(cmd);
        }

        if (getCommand("blastregen") != null) {
            getCommand("blastregen").setExecutor(new BlastRegenCommand(this));
        }

        if (getCommand("blastmap") != null) {
            var cmd = new BlastMapCommand(this);
            getCommand("blastmap").setExecutor(cmd);
            getCommand("blastmap").setTabCompleter(cmd);
        }
    }

    private void registerListeners() {

        Bukkit.getPluginManager().registerEvents(new InstantSoupListener(this), this);

        Bukkit.getPluginManager().registerEvents(
                new LegacyCombatListener(this, gameStateManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new MobSpawnBlockerListener(this, gameStateManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new SumoVoidEliminationListener(arenaManager, arenaEliminationHandler, gameStateManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new PlayerJoinQuitListener(this, gameStateManager, arenaManager, queueManager, arenaEliminationHandler, hubSpawnManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new PlayerArenaEliminationListener(arenaManager, arenaEliminationHandler),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new PlayerDeathSpawnListener(arenaManager, arenaEliminationHandler, gameStateManager, hubSpawnManager, this),
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

        Bukkit.getPluginManager().registerEvents(
                new CosmeticsGUIListener(this, cosmeticsManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new MinigameHubQueueListener(this, minigameQueueManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new BlastBlasterListener(this, gameStateManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new BlastGeneratorListener(gameStateManager),
                this
        );

        // NEW: Hard-cancel BLAST friendly fire for melee/projectiles (guarantee)
        Bukkit.getPluginManager().registerEvents(
                new BlastFriendlyFireListener(blastMinigameManager),
                this
        );

        // NEW: BLAST shop (NPC + GUI purchases)
        Bukkit.getPluginManager().registerEvents(
                new BlastShopListener(this, gameStateManager),
                this
        );

        // NEW: BLAST utility items behaviour (instant wall, fireball, +life, tracker, homing missile)
        Bukkit.getPluginManager().registerEvents(
                new BlastUtilityItemsListener(this, gameStateManager),
                this
        );

        registerOptionalListener(
                "com.blake.portalplugin.listeners.BlastNewBlastersListener",
                new Class<?>[]{PortalPlugin.class, GameStateManager.class},
                new Object[]{this, gameStateManager}
        );

        registerOptionalListener(
                "com.blake.portalplugin.listeners.BlastAdvancedBlasterListener",
                new Class<?>[]{PortalPlugin.class, GameStateManager.class},
                new Object[]{this, gameStateManager}
        );

        // NEW: BLAST diamond HUD + pickup tracking
        Bukkit.getPluginManager().registerEvents(
                new BlastDiamondHudListener(this, gameStateManager, blastDiamondSpawnerService),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler
                    public void onJoin(PlayerJoinEvent e) {
                        Bukkit.getScheduler().runTask(
                                PortalPlugin.this,
                                () -> {
                                    if (scoreboardManager != null) scoreboardManager.refreshAll();
                                }
                        );
                    }
                },
                this
        );
    }

    private void registerOptionalListener(String className, Class<?>[] paramTypes, Object[] args) {
        if (className == null || className.isBlank()) return;

        try {
            Class<?> c = Class.forName(className);

            Object instance;
            if (paramTypes != null && args != null) {
                Constructor<?> ctor = c.getConstructor(paramTypes);
                instance = ctor.newInstance(args);
            } else {
                instance = c.getDeclaredConstructor().newInstance();
            }

            if (instance instanceof Listener listener) {
                Bukkit.getPluginManager().registerEvents(listener, this);
                getLogger().info("[PortalPlugin] Registered optional listener: " + className);
            } else {
                getLogger().warning("[PortalPlugin] Optional class is not a Listener: " + className);
            }
        } catch (ClassNotFoundException ignored) {
            // Not present; that's fine.
        } catch (Throwable t) {
            getLogger().warning("[PortalPlugin] Failed to register optional listener: " + className + " (" + t.getClass().getSimpleName() + ")");
        }
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

    public SelectionManager getSelectionManager() { return selectionManager; }

    public MinigameQueueManager getMinigameQueueManager() { return minigameQueueManager; }

    public BlastMinigameManager getBlastMinigameManager() { return blastMinigameManager; }

    // NEW getter
    public BlastDiamondSpawnerService getBlastDiamondSpawnerService() { return blastDiamondSpawnerService; }

    // NEW getter
    public BlastPowerupManager getBlastPowerupManager() { return blastPowerupManager; }

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

    public String getHubType() {
        return getConfig().getString("hub-type", "arena");
    }

    public void setHubType(String type) {
        if (type == null || type.isBlank()) type = "arena";
        getConfig().set("hub-type", type.toLowerCase());
        saveConfig();
    }

    public boolean isMinigameHub() {
        return "minigame".equalsIgnoreCase(getHubType());
    }

    public String getServerMinigame() {
        return getConfig().getString("server-minigame", "none");
    }

    public void setServerMinigame(String game) {
        if (game == null) {
            getConfig().set("server-minigame", null);
        } else {
            getConfig().set("server-minigame", game.toLowerCase());
        }
        saveConfig();
    }

    public String getEffectiveRegistryGameName() {
        if (isMinigameHub()) {
            String mg = getServerMinigame();
            if (mg == null || mg.isBlank() || mg.equalsIgnoreCase("none")) return "minigame";
            return mg.toLowerCase();
        }
        return "hub";
    }

    public void updateEffectiveActiveGameAndRegistry() {
        String effective = getEffectiveRegistryGameName();
        setActiveGame(effective);

        if (serverRegistryManager != null) {
            String host = getConfig().getString("server-registry.hostname", "127.0.0.1");
            int port = getServer().getPort();
            serverRegistryManager.registerThisServer(effective, host, port);
        }

        if (minigameQueueManager != null) {
            minigameQueueManager.onConfigChanged();
        }
    }

    public void setPlayerQueuedStateSafe(Player p) {
        if (p == null) return;

        try {
            GameState queuing = GameState.valueOf("QUEUING");
            gameStateManager.setGameState(p, queuing);
        } catch (Exception ignored) {
            try {
                gameStateManager.setGameState(p, GameState.HUB);
            } catch (Exception ignored2) {}
        }
    }
}
