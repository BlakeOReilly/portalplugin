package com.blake.portalplugin;

import org.bukkit.plugin.java.JavaPlugin;

public class MainPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("gamestate").setExecutor(new GameStateCommand());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}