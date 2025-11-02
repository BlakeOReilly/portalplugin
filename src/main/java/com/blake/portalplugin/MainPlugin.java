package com.blake.portalplugin;

import org.bukkit.plugin.java.JavaPlugin;

public class MainPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("scoreboard").setExecutor(new Commands(this));
    }

    @Override
    public void onDisable() {
        // Logic for disabling the plugin
    }
}
