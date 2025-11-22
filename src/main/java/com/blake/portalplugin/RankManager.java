package com.blake.portalplugin.ranks;

import com.blake.portalplugin.stats.DatabaseManager;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Rank> cache = new ConcurrentHashMap<>();

    public RankManager(Plugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    // ---------------------------------------------------------
    // Table creation
    // ---------------------------------------------------------
    public void ensureTable() {
        String sql =
                "CREATE TABLE IF NOT EXISTS player_ranks (" +
                "  uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                "  rank VARCHAR(32) NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = databaseManager.getConnection();
             Statement st = conn.createStatement()) {

            st.executeUpdate(sql);
        } catch (SQLException ex) {
            plugin.getLogger().severe("[PortalPlugin] Failed to create player_ranks table: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------
    // Public API
    // ---------------------------------------------------------
    public Rank getRank(UUID uuid) {
        if (uuid == null) return Rank.NONE;

        Rank cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }

        Rank loaded = loadRankFromDatabase(uuid);
        cache.put(uuid, loaded);
        return loaded;
    }

    public Rank getRankOrDefault(UUID uuid) {
        Rank r = getRank(uuid);
        return (r == null ? Rank.NONE : r);
    }

    public void setRank(UUID uuid, Rank rank) {
        if (uuid == null) return;
        if (rank == null) rank = Rank.NONE;

        if (rank == Rank.NONE) {
            deleteRank(uuid);
            cache.put(uuid, Rank.NONE);
        } else {
            upsertRank(uuid, rank);
            cache.put(uuid, rank);
        }
    }

    // ---------------------------------------------------------
    // DB helpers
    // ---------------------------------------------------------
    private Rank loadRankFromDatabase(UUID uuid) {
        String sql = "SELECT rank FROM player_ranks WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String rankName = rs.getString("rank");
                    Rank r = Rank.fromString(rankName);
                    return (r != null ? r : Rank.NONE);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("[PortalPlugin] Failed to load rank for " + uuid + ": " + ex.getMessage());
        }

        return Rank.NONE;
    }

    private void upsertRank(UUID uuid, Rank rank) {
        String sql =
                "INSERT INTO player_ranks (uuid, rank) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE rank = VALUES(rank);";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, rank.name());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("[PortalPlugin] Failed to save rank for " + uuid + ": " + ex.getMessage());
        }
    }

    private void deleteRank(UUID uuid) {
        String sql = "DELETE FROM player_ranks WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("[PortalPlugin] Failed to delete rank for " + uuid + ": " + ex.getMessage());
        }
    }
}
