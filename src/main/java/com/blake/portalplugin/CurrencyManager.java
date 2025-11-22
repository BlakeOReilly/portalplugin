package com.blake.portalplugin.currency;

import com.blake.portalplugin.stats.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class CurrencyManager {

    private final Plugin plugin;
    private final DatabaseManager database;

    public CurrencyManager(Plugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.database = databaseManager;
    }

    // ------------------------------------------------------------------------
    // TABLE CREATION
    // ------------------------------------------------------------------------
    public void ensureTable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_currency (
                    uuid CHAR(36) NOT NULL PRIMARY KEY,
                    coins INT NOT NULL DEFAULT 0,
                    gems INT NOT NULL DEFAULT 0
                )
                """;

            try (Connection conn = database.getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[PortalPlugin] Failed to create player_currency table", e);
            }
        });
    }

    // ------------------------------------------------------------------------
    // INTERNAL LOADER
    // ------------------------------------------------------------------------
    private int getInt(UUID uuid, String column) {
        String sql = "SELECT " + column + " FROM player_currency WHERE uuid=?";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) return rs.getInt(column);

        } catch (SQLException e) {
            plugin.getLogger().warning("[CurrencyManager] getInt failed: " + e.getMessage());
        }
        return 0;
    }

    private void setInt(UUID uuid, String column, int value) {
        String sql = """
            INSERT INTO player_currency (uuid, coins, gems)
            VALUES (?, 0, 0)
            ON DUPLICATE KEY UPDATE """ + column + "=?";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ps.setInt(2, value);
                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("[CurrencyManager] setInt failed: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------------
    // PUBLIC GETTERS
    // ------------------------------------------------------------------------
    public int getCoins(UUID uuid) { return getInt(uuid, "coins"); }
    public int getGems(UUID uuid) { return getInt(uuid, "gems"); }

    // ------------------------------------------------------------------------
    // PUBLIC SETTERS
    // ------------------------------------------------------------------------
    public void setCoins(UUID uuid, int amount) { setInt(uuid, "coins", amount); }
    public void setGems(UUID uuid, int amount) { setInt(uuid, "gems", amount); }

    // ------------------------------------------------------------------------
    // PUBLIC ADD / REMOVE
    // ------------------------------------------------------------------------
    public void addCoins(UUID uuid, int amount) {
        int current = getCoins(uuid);
        setCoins(uuid, current + amount);
    }

    public void addGems(UUID uuid, int amount) {
        int current = getGems(uuid);
        setGems(uuid, current + amount);
    }

    public void removeCoins(UUID uuid, int amount) {
        int current = getCoins(uuid);
        setCoins(uuid, Math.max(0, current - amount));
    }

    public void removeGems(UUID uuid, int amount) {
        int current = getGems(uuid);
        setGems(uuid, Math.max(0, current - amount));
    }
}
