package com.blake.portalplugin;

import org.bukkit.plugin.java.JavaPlugin;

public class MainPlugin extends JavaPlugin {
    private GameStateManager gameStateManager;

    @Override
    public void onEnable() {
        gameStateManager = new GameStateManager();
        this.getCommand("gamestate").setExecutor(new GamestateCommand());
    }
}