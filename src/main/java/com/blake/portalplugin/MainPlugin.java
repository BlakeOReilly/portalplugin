package com.blake.portalplugin;

import org.bukkit.plugin.java.JavaPlugin;

public class MainPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        this.getCommand("gamestate").setExecutor(new GameStateCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}