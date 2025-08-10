package com.blake.portalplugin;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Constructor;

public final class HubStatsPlugin extends JavaPlugin {

    private static HubStatsPlugin instance;
    private java.util.logging.Logger logger;

    public static HubStatsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();

        // Attempt to save default config if present
        try {
            saveDefaultConfig();
        } catch (Exception e) {
            logger.fine("No default config to save: " + e.getMessage());
        }

        // Safely register known commands. If plugin.yml does not contain the command
        // getCommand(...) will return null and we must not call setExecutor on null.
        tryRegisterCommand("gamestate",
            "com.blake.portalplugin.GameStateCommand",
            "com.blake.portalplugin.GamestateCommand",
            "com.blake.portalplugin.commands.GameStateCommand",
            "com.blake.portalplugin.commands.GamestateCommand",
            "com.blake.portalplugin.commands.Gamestate",
            "com.blake.portalplugin.commands.GameState"
        );

        tryRegisterCommand("createsign",
            "com.blake.portalplugin.CreateSignCommand",
            "com.blake.portalplugin.commands.CreateSignCommand",
            "com.blake.portalplugin.CreateSign",
            "com.blake.portalplugin.commands.CreateSign"
        );

        tryRegisterCommand("saveblue",
            "com.blake.portalplugin.SaveBlueCommand",
            "com.blake.portalplugin.commands.SaveBlueCommand",
            "com.blake.portalplugin.SaveBlue",
            "com.blake.portalplugin.commands.SaveBlue"
        );

        tryRegisterCommand("savered",
            "com.blake.portalplugin.SaveRedCommand",
            "com.blake.portalplugin.commands.SaveRedCommand",
            "com.blake.portalplugin.SaveRed",
            "com.blake.portalplugin.commands.SaveRed"
        );

        // Other initialization remains unchanged; keep enabling lightweight to avoid NPEs
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    /**
     * Try to register a command by checking plugin.yml and attempting to load
     * a likely executor class via reflection. This prevents a NullPointerException
     * when getCommand(...) returns null and logs helpful diagnostics.
     */
    private void tryRegisterCommand(String commandName, String... candidateClassNames) {
        PluginCommand cmd = getCommand(commandName);
        if (cmd == null) {
            logger.warning("Command '" + commandName + "' not defined in plugin.yml or could not be loaded. Skipping registration.");
            return;
        }

        for (String className : candidateClassNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (!CommandExecutor.class.isAssignableFrom(clazz)) {
                    continue;
                }

                CommandExecutor executor = null;

                // Try constructor(HubStatsPlugin)
                try {
                    Constructor<?> ctor = clazz.getConstructor(HubStatsPlugin.class);
                    executor = (CommandExecutor) ctor.newInstance(this);
                } catch (NoSuchMethodException ignored) {}

                // Try constructor(JavaPlugin)
                if (executor == null) {
                    try {
                        Constructor<?> ctor = clazz.getConstructor(JavaPlugin.class);
                        executor = (CommandExecutor) ctor.newInstance(this);
                    } catch (NoSuchMethodException ignored) {}
                }

                // Try no-arg constructor
                if (executor == null) {
                    try {
                        Constructor<?> ctor = clazz.getConstructor();
                        executor = (CommandExecutor) ctor.newInstance();
                    } catch (NoSuchMethodException ignored) {}
                }

                if (executor != null) {
                    cmd.setExecutor(executor);
                    logger.info("Registered command '" + commandName + "' using executor " + className);
                    return;
                }
            } catch (ClassNotFoundException e) {
                // Try next candidate
            } catch (ReflectiveOperationException e) {
                logger.warning("Failed to instantiate command executor " + className + " for command '" + commandName + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
            } catch (Exception e) {
                logger.warning("Unexpected error while registering command " + commandName + ": " + e.getMessage());
            }
        }

        logger.warning("No suitable CommandExecutor found for command '" + commandName + "'. Command will not be handled unless registered elsewhere.");
    }
}
