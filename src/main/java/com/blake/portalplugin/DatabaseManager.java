package com.blake.portalplugin.stats;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class DatabaseManager {

    private final Plugin plugin;

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String pass;

    private final int connectTimeoutMs;
    private final int socketTimeoutMs;

    private final String jdbcUrl;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;

        // Ensure config exists on disk, and reload it so edits are picked up.
        // (If your main plugin already does this, it's still safe here.)
        try {
            plugin.saveDefaultConfig();
        } catch (Exception ignored) {
            // Some setups may throw if called in unusual lifecycle states; ignore.
        }
        try {
            plugin.reloadConfig();
        } catch (Exception ignored) {
            // If reload fails for any reason, we'll still use whatever config is in memory.
        }

        FileConfiguration cfg = plugin.getConfig();

        // ------------------------------------------------------------
        // Print EXACT config location and whether config.yml exists.
        // This is the #1 thing you need to verify across minigame1/2.
        // ------------------------------------------------------------
        File dataFolder = plugin.getDataFolder();
        File configFile = new File(dataFolder, "config.yml");
        plugin.getLogger().info("[PortalPlugin] Data folder: " + dataFolder.getAbsolutePath());
        plugin.getLogger().info("[PortalPlugin] Config file: " + configFile.getAbsolutePath() + " exists=" + configFile.exists());

        // Also print whether keys are present (not just the final values).
        boolean hasDbSection = cfg.isConfigurationSection("database");
        boolean hasUserKey = cfg.isSet("database.user");
        boolean hasPassKey = cfg.isSet("database.pass");
        plugin.getLogger().info("[PortalPlugin] Config keys: databaseSection=" + hasDbSection +
                " database.user.isSet=" + hasUserKey + " database.pass.isSet=" + hasPassKey);

        // Read config (with defaults as fallbacks)
        this.host = cfg.getString("database.host", "127.0.0.1");
        this.port = cfg.getInt("database.port", 3306);
        this.database = cfg.getString("database.name", "mc_stats");
        this.user = cfg.getString("database.user", "root");
        this.pass = cfg.getString("database.pass", "");

        this.connectTimeoutMs = cfg.getInt("database.connect-timeout-ms", 3000);
        this.socketTimeoutMs = cfg.getInt("database.socket-timeout-ms", 5000);

        // Prefer explicit IPv4 and schema selection
        this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database;

        loadDriverBestEffort();

        // Echo effective config values used (mask password length only)
        plugin.getLogger().info("[PortalPlugin] DB config in use: host=" + host + " port=" + port +
                " db=" + database + " user=" + user + " passLen=" + (pass == null ? 0 : pass.length()));

        // Run connection diagnostics immediately
        runStartupDiagnostics();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Connection getConnection() throws SQLException {
        Properties props = buildConnectionProperties();
        return DriverManager.getConnection(jdbcUrl, props);
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    private void runStartupDiagnostics() {
        // Quick DNS/host resolve check
        try {
            InetAddress addr = InetAddress.getByName(host);
            plugin.getLogger().info("[PortalPlugin] DB host resolves to: " + addr.getHostAddress());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PortalPlugin] DB host lookup failed for host=" + host + " (check database.host).", e);
        }

        long start = System.nanoTime();
        try (Connection c = testConnection();
             Statement st = c.createStatement()) {

            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            plugin.getLogger().info("[PortalPlugin] DB connection OK in ~" + ms + "ms. jdbcUrl=" + jdbcUrl);

            // Identify server + driver
            try {
                String product = c.getMetaData().getDatabaseProductName();
                String version = c.getMetaData().getDatabaseProductVersion();
                String driver = c.getMetaData().getDriverName();
                String driverVer = c.getMetaData().getDriverVersion();
                plugin.getLogger().info("[PortalPlugin] DB server=" + product + " " + version + " | driver=" + driver + " " + driverVer);
            } catch (Exception ignored) {
                // no-op
            }

            // Verify active schema
            try (ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                if (rs.next()) {
                    plugin.getLogger().info("[PortalPlugin] DB selected schema: " + rs.getString(1));
                }
            } catch (SQLException ignored) {
                // no-op
            }

            // Show who we authenticated as (very useful for confirming portal vs root)
            try (ResultSet rs = st.executeQuery("SELECT CURRENT_USER(), USER()")) {
                if (rs.next()) {
                    plugin.getLogger().info("[PortalPlugin] DB auth identity: CURRENT_USER()=" + rs.getString(1) + " | USER()=" + rs.getString(2));
                }
            } catch (SQLException ignored) {
                // no-op
            }

            // Server default authentication plugin (may require privileges)
            try (ResultSet rs = st.executeQuery("SHOW VARIABLES LIKE 'default_authentication_plugin'")) {
                if (rs.next()) {
                    plugin.getLogger().info("[PortalPlugin] default_authentication_plugin=" + rs.getString(2));
                }
            } catch (SQLException ignored) {
                // no-op
            }

        } catch (SQLException e) {
            logSqlFailureWithHints(e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[PortalPlugin] Unexpected DB diagnostic failure.", e);
        }
    }

    private Connection testConnection() throws SQLException {
        Properties props = buildConnectionProperties();
        return DriverManager.getConnection(jdbcUrl, props);
    }

    private void logSqlFailureWithHints(SQLException e) {
        plugin.getLogger().log(Level.SEVERE, "[PortalPlugin] DB connection failed. url=" + jdbcUrl + " user=" + user, e);

        String msg = (e.getMessage() == null) ? "" : e.getMessage().toLowerCase(Locale.ROOT);

        if (msg.contains("auth_gssapi_client")) {
            plugin.getLogger().severe("[PortalPlugin] Detected GSSAPI auth error (auth_gssapi_client). " +
                    "If DB config is intended to use 'portal', but log shows user=root, then the running server is NOT reading the config you edited. " +
                    "Fix: edit plugins/PortalPlugin/config.yml in the correct server folder and ensure the plugin does not overwrite it on startup.");
        } else if (msg.contains("access denied")) {
            plugin.getLogger().severe("[PortalPlugin] Access denied. Check database.user/database.pass and MariaDB grants for that user/host.");
        } else if (msg.contains("communications link failure") || msg.contains("connection refused")) {
            plugin.getLogger().severe("[PortalPlugin] Communications/connection refused. DB server not reachable at " +
                    host + ":" + port + " (service not running, wrong host/port, or firewall).");
        } else if (msg.contains("unknown database")) {
            plugin.getLogger().severe("[PortalPlugin] Unknown database/schema '" + database + "'. Create it or fix database.name.");
        }

        Throwable t = e.getCause();
        int depth = 0;
        while (t != null && depth < 8) {
            plugin.getLogger().log(Level.SEVERE, "[PortalPlugin] DB cause[" + depth + "]: " + t.getClass().getName() + ": " + t.getMessage());
            t = t.getCause();
            depth++;
        }
    }

    // -------------------------------------------------------------------------
    // Driver + properties
    // -------------------------------------------------------------------------

    private void loadDriverBestEffort() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            plugin.getLogger().info("[PortalPlugin] Loaded JDBC driver: com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("org.mariadb.jdbc.Driver");
                plugin.getLogger().info("[PortalPlugin] Loaded JDBC driver: org.mariadb.jdbc.Driver");
            } catch (ClassNotFoundException ignored2) {
                plugin.getLogger().warning("[PortalPlugin] Could not load MySQL/MariaDB driver. Ensure a JDBC driver is bundled.");
            }
        }
    }

    private Properties buildConnectionProperties() {
        Properties props = new Properties();

        // Credentials
        props.setProperty("user", user);
        props.setProperty("password", pass == null ? "" : pass);

        // Common safe settings
        props.setProperty("useSSL", "false");
        props.setProperty("serverTimezone", "UTC");

        // Timeouts
        props.setProperty("connectTimeout", String.valueOf(connectTimeoutMs));
        props.setProperty("socketTimeout", String.valueOf(socketTimeoutMs));
        props.setProperty("tcpKeepAlive", "true");

        // Helps keep behavior consistent across localhost vs 127.0.0.1
        props.setProperty("useLocalSessionState", "true");

        // If your server advertises GSSAPI by default, this can help steer Connector/J.
        // (Still, the correct fix is to NOT use a GSSAPI-authenticated account like root.)
        props.setProperty("defaultAuthenticationPlugin", "mysql_native_password");

        // Optional extra stability
        props.setProperty("characterEncoding", "utf8");
        props.setProperty("useUnicode", "true");

        return props;
    }
}
