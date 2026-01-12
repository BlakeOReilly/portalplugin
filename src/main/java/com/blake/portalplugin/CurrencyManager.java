package com.blake.portalplugin.currency;

import com.blake.portalplugin.stats.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public class CurrencyManager {

    private final Plugin plugin;
    private final DatabaseManager database;

    // Cache so scoreboards never block server thread
    private final ConcurrentMap<UUID, CurrencyData> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> loading = new ConcurrentHashMap<>();

    private static final class CurrencyData {
        volatile int coins;
        volatile int gems;

        CurrencyData(int coins, int gems) {
            this.coins = coins;
            this.gems = gems;
        }
    }

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
    // CACHE LOAD (ASYNC)
    // ------------------------------------------------------------------------
    private void ensureLoaded(UUID uuid) {
        if (uuid == null) return;

        // already cached
        if (cache.containsKey(uuid)) return;

        // prevent spamming async loads
        if (loading.putIfAbsent(uuid, Boolean.TRUE) != null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CurrencyData data = loadFromDb(uuid);
                cache.put(uuid, data);
            } catch (Exception e) {
                plugin.getLogger().warning("[CurrencyManager] load failed: " + e.getMessage());
                // keep default 0/0 in cache to avoid repeated loads every tick
                cache.putIfAbsent(uuid, new CurrencyData(0, 0));
            } finally {
                loading.remove(uuid);
            }
        });
    }

    private CurrencyData loadFromDb(UUID uuid) throws SQLException {
        String select = "SELECT coins, gems FROM player_currency WHERE uuid=?";
        String insert = "INSERT INTO player_currency (uuid, coins, gems) VALUES (?, 0, 0)";

        try (Connection conn = database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(select)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new CurrencyData(rs.getInt("coins"), rs.getInt("gems"));
                    }
                }
            }

            // row doesn't exist yet: create it
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }

            return new CurrencyData(0, 0);
        }
    }

    // ------------------------------------------------------------------------
    // PUBLIC GETTERS (NON-BLOCKING)
    // ------------------------------------------------------------------------
    public int getCoins(UUID uuid) {
        ensureLoaded(uuid);
        CurrencyData d = cache.get(uuid);
        return d != null ? d.coins : 0;
    }

    public int getGems(UUID uuid) {
        ensureLoaded(uuid);
        CurrencyData d = cache.get(uuid);
        return d != null ? d.gems : 0;
    }

    // ------------------------------------------------------------------------
    // PUBLIC SETTERS (ASYNC WRITE + CACHE)
    // ------------------------------------------------------------------------
    public void setCoins(UUID uuid, int amount) {
        if (uuid == null) return;
        int value = Math.max(0, amount);

        cache.compute(uuid, (k, d) -> {
            if (d == null) d = new CurrencyData(0, 0);
            d.coins = value;
            return d;
        });

        upsertSet(uuid, "coins", value);
    }

    public void setGems(UUID uuid, int amount) {
        if (uuid == null) return;
        int value = Math.max(0, amount);

        cache.compute(uuid, (k, d) -> {
            if (d == null) d = new CurrencyData(0, 0);
            d.gems = value;
            return d;
        });

        upsertSet(uuid, "gems", value);
    }

    private void upsertSet(UUID uuid, String column, int value) {
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
                plugin.getLogger().warning("[CurrencyManager] upsertSet failed: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------------
    // ADD / REMOVE (ASYNC DB UPDATE + CACHE)
    // ------------------------------------------------------------------------
    public void addCoins(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) return;

        cache.compute(uuid, (k, d) -> {
            if (d == null) d = new CurrencyData(0, 0);
            d.coins = d.coins + amount;
            return d;
        });

        applyDelta(uuid, "coins", amount);
    }

    public void addGems(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) return;

        cache.compute(uuid, (k, d) -> {
            if (d == null) d = new CurrencyData(0, 0);
            d.gems = d.gems + amount;
            return d;
        });

        applyDelta(uuid, "gems", amount);
    }

    public void removeCoins(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) return;

        cache.compute(uuid, (k, d) -> {
            if (d == null) d = new CurrencyData(0, 0);
            d.coins = Math.max(0, d.coins - amount);
            return d;
        });

        applyDelta(uuid, "coins", -amount);
    }

    public void removeGems(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) return;

        cache.compute(uuid, (k, d) -> {
            if (d == null) d = new CurrencyData(0, 0);
            d.gems = Math.max(0, d.gems - amount);
            return d;
        });

        applyDelta(uuid, "gems", -amount);
    }

    private void applyDelta(UUID uuid, String column, int delta) {
        // 1) ensure row exists
        String ensure = """
            INSERT INTO player_currency (uuid, coins, gems)
            VALUES (?, 0, 0)
            ON DUPLICATE KEY UPDATE uuid=uuid
            """;

        // 2) atomic update with clamp
        String update = "UPDATE player_currency SET " + column + " = GREATEST(0, " + column + " + ?) WHERE uuid=?";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getConnection()) {

                try (PreparedStatement ps = conn.prepareStatement(ensure)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setInt(1, delta);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("[CurrencyManager] applyDelta failed: " + e.getMessage());
            }
        });
    }
}
