package com.blake.portalplugin;

import com.blake.portalplugin.arenas.ArenaManager;
import com.blake.portalplugin.commands.ArenaCommand;
import com.blake.portalplugin.commands.ArenaMaximumCommand;
import com.blake.portalplugin.commands.CreateGameCommand;
import com.blake.portalplugin.commands.CreateSignCommand;
import com.blake.portalplugin.commands.FillHealthCommand;
import com.blake.portalplugin.commands.FillHungerCommand;
import com.blake.portalplugin.commands.GameStateCommand;
import com.blake.portalplugin.commands.JoinGameCommand;
import com.blake.portalplugin.listeners.PlayerJoinQuitListener;
import com.blake.portalplugin.listeners.QueueSignListener;
import com.blake.portalplugin.queues.GameQueueManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PortalPlugin extends JavaPlugin {

    private GameStateManager gameStateManager;
    private ArenaManager arenaManager;
    private GameQueueManager queueManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Managers
        this.gameStateManager = new GameStateManager(this);
        this.arenaManager = new ArenaManager(this);
        this.queueManager = new GameQueueManager(arenaManager, gameStateManager);

        // /gamestate
        var gameStateCmd = getCommand("gamestate");
        if (gameStateCmd != null) {
            GameStateCommand executor = new GameStateCommand(gameStateManager);
            gameStateCmd.setExecutor(executor);
            gameStateCmd.setTabCompleter(executor);
        }

        // /fillhunger
        var fillHungerCmd = getCommand("fillhunger");
        if (fillHungerCmd != null) {
            fillHungerCmd.setExecutor(new FillHungerCommand());
        }

        // /fillhealth
        var fillHealthCmd = getCommand("fillhealth");
        if (fillHealthCmd != null) {
            fillHealthCmd.setExecutor(new FillHealthCommand());
        }

        // /arena
        var arenaCmd = getCommand("arena");
        if (arenaCmd != null) {
            arenaCmd.setExecutor(new ArenaCommand(arenaManager));
        }

        // /creategame
        var createGameCmd = getCommand("creategame");
        if (createGameCmd != null) {
            createGameCmd.setExecutor(new CreateGameCommand(queueManager));
        }

        // /join
        var joinCmd = getCommand("join");
        if (joinCmd != null) {
            joinCmd.setExecutor(new JoinGameCommand(queueManager));
        }

        // /createsign
        var createSignCmd = getCommand("createsign");
        if (createSignCmd != null) {
            createSignCmd.setExecutor(new CreateSignCommand(queueManager, this));
        }

        // /arenamaximum
        var arenaMaxCmd = getCommand("arenamaximum");
        if (arenaMaxCmd != null) {
            arenaMaxCmd.setExecutor(new ArenaMaximumCommand(arenaManager));
        }

        // Listeners
        Bukkit.getPluginManager().registerEvents(new PlayerJoinQuitListener(gameStateManager), this);
        Bukkit.getPluginManager().registerEvents(new QueueSignListener(queueManager), this);

        getLogger().info("PortalPlugin enabled.");
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) {
            arenaManager.saveArenasToFile();
        }
        if (gameStateManager != null) {
            gameStateManager.clearAllOnline();
        }
        getLogger().info("PortalPlugin disabled.");
    }

    public GameQueueManager getQueueManager() {
        return queueManager;
    }
}
