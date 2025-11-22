package com.blake.portalplugin.stats;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.holograms.HologramManager;
import com.blake.portalplugin.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public class StatsManager {

    private final PortalPlugin plugin;
    private final DatabaseManager databaseManager;

    public StatsManager(PortalPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    // ---------------------------------------------------------------------
    //  CREATE TABLE IF MISSING
    // ---------------------------------------------------------------------
    public void ensureTable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = """
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid CHAR(36) NOT NULL,
                        gamemode VARCHAR(32) NOT NULL,
                        wins INT NOT NULL DEFAULT 0,
                        losses INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, gamemode)
                    )
                    """;
            try (Connection conn = databaseManager.getConnection();
                 Statement st = conn.createStatement()) {

                st.executeUpdate(sql);

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[PortalPlugin] Failed to create player_stats table", e);
            }
        });
    }

    // ---------------------------------------------------------------------
    //  RECORD A GAME RESULT
    // ---------------------------------------------------------------------
    public void recordGameResult(String gamemode, UUID winnerId, Collection<UUID> participants) {

        if (gamemode == null || winnerId == null || participants == null || participants.isEmpty()) {
            plugin.getLogger().warning("[PortalPlugin] recordGameResult aborted: bad arguments");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            String sql = """
                    INSERT INTO player_stats (uuid, gamemode, wins, losses)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        wins = wins + VALUES(wins),
                        losses = losses + VALUES(losses)
                    """;

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                for (UUID uuid : participants) {
                    boolean isWinner = uuid.equals(winnerId);

                    ps.clearParameters();
                    ps.setString(1, uuid.toString());
                    ps.setString(2, gamemode);
                    ps.setInt(3, isWinner ? 1 : 0);
                    ps.setInt(4, isWinner ? 0 : 1);
                    ps.addBatch();
                }

                ps.executeBatch();

                // Refresh holograms and scoreboards on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    HologramManager hm = plugin.getHologramManager();
                    if (hm != null) hm.updateAll(this);

                    ScoreboardManager sb = plugin.getScoreboardManager();
                    if (sb != null) sb.refreshAll();
                });

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[PortalPlugin] Failed to record game result", e);
            }
        });
    }

    // ---------------------------------------------------------------------
    //  INTERNAL SYNC LOADER
    // ---------------------------------------------------------------------
    private List<PlayerStats> loadStatsSync(UUID uuid) throws SQLException {

        List<PlayerStats> list = new ArrayList<>();

        String sql = "SELECT gamemode, wins, losses FROM player_stats WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {

                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                String name = offline.getName() == null ? "Unknown" : offline.getName();

                while (rs.next()) {

                    String gm = rs.getString("gamemode");
                    int wins = rs.getInt("wins");
                    int losses = rs.getInt("losses");

                    list.add(new PlayerStats(uuid.toString(), name, gm, wins, losses));
                }
            }
        }
        return list;
    }

    // ---------------------------------------------------------------------
    //  ASYNC LOADER FOR GUI
    // ---------------------------------------------------------------------
    public void loadStatsAsync(UUID uuid, Consumer<List<PlayerStats>> callback) {

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            List<PlayerStats> stats = null;

            try {
                stats = loadStatsSync(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[PortalPlugin] Failed to load stats async", e);
            }

            List<PlayerStats> finalStats = stats;

            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalStats));
        });
    }

    // ---------------------------------------------------------------------
    //  DEPRECATED (Used by old /stats)
    //  Now unused but kept for compatibility in case other code calls it.
    // ---------------------------------------------------------------------
    @Deprecated
    public void sendStats(org.bukkit.command.CommandSender viewer, UUID target, String targetName) {

        loadStatsAsync(target, stats -> {

            if (viewer == null) return;

            if (stats == null) {
                viewer.sendMessage("§cError loading stats.");
                return;
            }

            viewer.sendMessage("§e------ Stats for §a" + targetName + "§e ------");

            if (stats.isEmpty()) {
                viewer.sendMessage("§7No stats recorded.");
                return;
            }

            for (PlayerStats s : stats) {
                viewer.sendMessage("§b" + s.getGamemode()
                        + " §7: §a" + s.getWins()
                        + " wins §7/ §c" + s.getLosses() + " losses");
            }
        });
    }

    // ---------------------------------------------------------------------
    //  GET WINS FOR A SINGLE GAMEMODE (ASYNC)
    // ---------------------------------------------------------------------
    public void getWinsForGamemode(UUID uuid, String gamemode, IntConsumer callback) {

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            int wins = 0;

            String sql = "SELECT wins FROM player_stats WHERE uuid = ? AND gamemode = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ps.setString(2, gamemode);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) wins = rs.getInt("wins");
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[PortalPlugin] Failed getWinsForGamemode", e);
            }

            int finalWins = wins;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalWins));
        });
    }

    // ---------------------------------------------------------------------
    //  TOP 10 WINS (SYNC)
    // ---------------------------------------------------------------------
    public List<String> getTopTenWinsSync(String gamemode) {

        List<String> lines = new ArrayList<>();
        lines.add("§e§lTop Wins - " + gamemode);

        String sql = """
                SELECT uuid, wins
                FROM player_stats
                WHERE gamemode = ?
                ORDER BY wins DESC
                LIMIT 10
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, gamemode);

            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;

                while (rs.next()) {

                    String uuidStr = rs.getString("uuid");
                    int wins = rs.getInt("wins");

                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                    String name = op.getName() == null ? "Unknown" : op.getName();

                    lines.add("§f" + rank + ". §a" + name + " §7- §b" + wins);
                    rank++;
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("[PortalPlugin] Failed getTopTenWinsSync: " + e.getMessage());
        }

        return lines;
    }

    // ---------------------------------------------------------------------
    //  TOP X WINS (ASYNC)
    // ---------------------------------------------------------------------
    public void getTopWins(String gamemode, int limit, Consumer<List<PlayerStats>> callback) {

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            List<PlayerStats> list = new ArrayList<>();

            String sql = """
                    SELECT uuid, wins
                    FROM player_stats
                    WHERE gamemode = ?
                    ORDER BY wins DESC
                    LIMIT ?
                    """;

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, gamemode);
                ps.setInt(2, limit);

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {
                        String uuidStr = rs.getString("uuid");
                        int wins = rs.getInt("wins");

                        OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                        String name = op.getName() == null ? "Unknown" : op.getName();

                        list.add(new PlayerStats(uuidStr, name, gamemode, wins, 0));
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("[PortalPlugin] getTopWins failed: " + e.getMessage());
            }

            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(list));
        });
    }
}
