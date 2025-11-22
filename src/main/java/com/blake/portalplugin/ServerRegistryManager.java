package com.blake.portalplugin.registry;

import com.blake.portalplugin.PortalPlugin;
import com.blake.portalplugin.stats.DatabaseManager;

import java.sql.*;

/**
 * Manages server registration in the shared MySQL database.
 *
 * Table: server_registry
 *  - id          INT AUTO_INCREMENT PRIMARY KEY
 *  - game        VARCHAR(64) NOT NULL
 *  - server_code VARCHAR(64) UNIQUE NOT NULL   (e.g. "Spleef-1")
 *  - hostname    VARCHAR(128) NOT NULL
 *  - port        INT NOT NULL
 *  - last_seen   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *                ON UPDATE CURRENT_TIMESTAMP
 */
public class ServerRegistryManager {

    private final PortalPlugin plugin;
    private final DatabaseManager databaseManager;

    private String serverCode; // e.g. "Spleef-1"

    public ServerRegistryManager(PortalPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public String getServerCode() {
        return serverCode;
    }

    /**
     * Create the server_registry table if it does not exist.
     */
    public void ensureTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS server_registry (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    game VARCHAR(64) NOT NULL,
                    server_code VARCHAR(64) UNIQUE NOT NULL,
                    hostname VARCHAR(128) NOT NULL,
                    port INT NOT NULL,
                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                );
                """;

        try (Connection conn = databaseManager.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException ex) {
            plugin.getLogger().severe("[PortalPlugin] Failed to ensure server_registry table: " + ex.getMessage());
        }
    }

    /**
     * Register THIS server in the registry for the given game.
     */
    public void registerThisServer(String game, String hostname, int port) {
        if (game == null || game.isBlank() || "none".equalsIgnoreCase(game)) {
            plugin.getLogger().warning("[PortalPlugin] Active game is 'none' or blank, skipping server registration.");
            return;
        }

        String normalizedGame = game.trim().toLowerCase();

        try (Connection conn = databaseManager.getConnection()) {

            // Check if already registered (same game + host + port)
            String selectExisting = """
                    SELECT server_code
                    FROM server_registry
                    WHERE game = ? AND hostname = ? AND port = ?
                    """;

            try (PreparedStatement ps = conn.prepareStatement(selectExisting)) {
                ps.setString(1, normalizedGame);
                ps.setString(2, hostname);
                ps.setInt(3, port);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        this.serverCode = rs.getString("server_code");
                        plugin.getLogger().info("[PortalPlugin] Found existing server_registry entry: " + serverCode);
                        touchLastSeen(conn, serverCode);
                        return;
                    }
                }
            }

            // Get number of existing servers for this game
            int countForGame = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS cnt FROM server_registry WHERE game = ?")) {

                ps.setString(1, normalizedGame);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) countForGame = rs.getInt("cnt");
                }
            }

            int nextIndex = countForGame + 1;
            String code = buildServerCode(normalizedGame, nextIndex);

            // Insert new server
            String insertSql = """
                    INSERT INTO server_registry (game, server_code, hostname, port)
                    VALUES (?, ?, ?, ?)
                    """;

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, normalizedGame);
                ps.setString(2, code);
                ps.setString(3, hostname);
                ps.setInt(4, port);
                ps.executeUpdate();
            }

            this.serverCode = code;
            plugin.getLogger().info("[PortalPlugin] Registered this server as " + serverCode
                    + " for game '" + normalizedGame + "' (" + hostname + ":" + port + ")");

        } catch (SQLException ex) {
            plugin.getLogger().severe("[PortalPlugin] Failed to register server in server_registry: " + ex.getMessage());
        }
    }

    /**
     * Update last_seen for this server_code.
     */
    private void touchLastSeen(Connection conn, String code) {
        String sql = "UPDATE server_registry SET last_seen = CURRENT_TIMESTAMP WHERE server_code = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("[PortalPlugin] Failed to update last_seen for " + code + ": " + ex.getMessage());
        }
    }

    /**
     * Build formatted server code like "Spleef-3".
     */
    private String buildServerCode(String game, int index) {
        if (game == null || game.isEmpty()) return "Server-" + index;

        String lower = game.toLowerCase();
        String pretty = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);

        return pretty + "-" + index;
    }
}
