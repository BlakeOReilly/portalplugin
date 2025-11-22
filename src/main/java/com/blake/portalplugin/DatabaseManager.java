package com.blake.portalplugin.stats;

import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    private final Plugin plugin;
    private final String url;
    private final String user;
    private final String pass;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;

        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String name = plugin.getConfig().getString("database.name", "mc_stats");
        this.user = plugin.getConfig().getString("database.user", "root");
        this.pass = plugin.getConfig().getString("database.pass", "");

        this.url = "jdbc:mysql://" + host + ":" + port + "/" + name +
                "?useSSL=false&autoReconnect=true&serverTimezone=UTC";

        // Try to load MySQL/MariaDB driver, ignore if already loaded
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("org.mariadb.jdbc.Driver");
            } catch (ClassNotFoundException ignored2) {
                plugin.getLogger().warning("[PortalPlugin] Could not load MySQL/MariaDB driver. " +
                        "Make sure the driver JAR is on the classpath.");
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
