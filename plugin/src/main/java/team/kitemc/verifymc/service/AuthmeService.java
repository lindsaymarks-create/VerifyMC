package team.kitemc.verifymc.service;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * AuthMe integration service class
 * Responsible for directly operating AuthMe database for registration, unregistration and password updates.
 */
public class AuthmeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SHA256_SALT_LENGTH = 16;

    private final Plugin plugin;
    private final boolean debug;

    public AuthmeService(Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    public boolean isAuthmeEnabled() {
        return plugin.getConfig().getBoolean("authme.enabled", false);
    }

    public boolean isPasswordRequired() {
        return plugin.getConfig().getBoolean("authme.require_password", false);
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        String regex = plugin.getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{3,16}$");
        return Pattern.matches(regex, password);
    }

    public boolean registerToAuthme(String username, String password) {
        return registerToAuthme(username, password, null);
    }

    public boolean registerToAuthme(String username, String password, String email) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping registration");
            return false;
        }
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }

        String normalizedName = username.trim();
        String loweredName = normalizedName.toLowerCase(Locale.ROOT);
        String passwordHash = hashPassword(password);
        long now = System.currentTimeMillis();

        String table = getTableName();
        ColumnConfig columns = getColumns();

        String querySql = "SELECT 1 FROM " + table + " WHERE LOWER(" + columns.name + ")=LOWER(?) LIMIT 1";

        try (Connection connection = getConnection()) {
            boolean exists;
            try (PreparedStatement ps = connection.prepareStatement(querySql)) {
                ps.setString(1, loweredName);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                return updateAuthmeRecord(connection, table, columns, loweredName, normalizedName, passwordHash, email, now);
            }
            return insertAuthmeRecord(connection, table, columns, loweredName, normalizedName, passwordHash, email, now);
        } catch (SQLException e) {
            debugLog("Failed to register user to AuthMe DB: " + e.getMessage());
            return false;
        }
    }

    public boolean unregisterFromAuthme(String username) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping unregistration");
            return false;
        }
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String table = getTableName();
        ColumnConfig columns = getColumns();
        String loweredName = username.trim().toLowerCase(Locale.ROOT);
        String deleteSql = "DELETE FROM " + table + " WHERE LOWER(" + columns.name + ")=LOWER(?)";

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(deleteSql)) {
            ps.setString(1, loweredName);
            int rows = ps.executeUpdate();
            debugLog("Deleted AuthMe record for " + username + ", rows=" + rows);
            return rows > 0;
        } catch (SQLException e) {
            debugLog("Failed to unregister user from AuthMe DB: " + e.getMessage());
            return false;
        }
    }

    public boolean changePasswordInAuthme(String username, String newPassword) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping password change");
            return false;
        }
        if (username == null || username.trim().isEmpty() || newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }

        String normalizedName = username.trim();
        String loweredName = normalizedName.toLowerCase(Locale.ROOT);
        String passwordHash = hashPassword(newPassword);
        long now = System.currentTimeMillis();

        String table = getTableName();
        ColumnConfig columns = getColumns();

        try (Connection connection = getConnection()) {
            boolean updated = updateAuthmeRecord(connection, table, columns, loweredName, normalizedName, passwordHash, null, now);
            if (updated) {
                return true;
            }

            debugLog("AuthMe user not found when changing password, creating a new record for " + normalizedName);
            return insertAuthmeRecord(connection, table, columns, loweredName, normalizedName, passwordHash, null, now);
        } catch (SQLException e) {
            debugLog("Failed to change password in AuthMe DB: " + e.getMessage());
            return false;
        }
    }



    public String getPasswordHashFromAuthme(String username) {
        if (!isAuthmeEnabled() || username == null || username.trim().isEmpty()) {
            return null;
        }

        String table = getTableName();
        ColumnConfig columns = getColumns();
        String loweredName = username.trim().toLowerCase(Locale.ROOT);
        String sql = "SELECT " + columns.password + " FROM " + table + " WHERE LOWER(" + columns.name + ")=LOWER(?) LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, loweredName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            debugLog("Failed to read AuthMe password hash: " + e.getMessage());
        }
        return null;
    }
    public String getEmailFromAuthme(String username) {
        if (!isAuthmeEnabled() || username == null || username.trim().isEmpty()) {
            return null;
        }

        String table = getTableName();
        ColumnConfig columns = getColumns();
        if (columns.email.isEmpty()) {
            return null;
        }
        String loweredName = username.trim().toLowerCase(Locale.ROOT);
        String sql = "SELECT " + columns.email + " FROM " + table + " WHERE LOWER(" + columns.name + ")=LOWER(?) LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, loweredName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            debugLog("Failed to read AuthMe email: " + e.getMessage());
        }
        return null;
    }

    private boolean updateAuthmeRecord(Connection connection, String table, ColumnConfig columns,
                                       String loweredName, String normalizedName, String passwordHash, String email, long now)
        throws SQLException {
        List<String> setColumns = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        setColumns.add(columns.realName + "=?");
        params.add(normalizedName);
        setColumns.add(columns.password + "=?");
        params.add(passwordHash);

        if (!columns.salt.isEmpty()) {
            setColumns.add(columns.salt + "=?");
            params.add("");
        }
        if (!columns.email.isEmpty() && email != null) {
            setColumns.add(columns.email + "=?");
            params.add(email);
        }
        if (!columns.lastLogin.isEmpty()) {
            setColumns.add(columns.lastLogin + "=?");
            params.add(now);
        }

        String updateSql = "UPDATE " + table + " SET " + String.join(", ", setColumns)
            + " WHERE LOWER(" + columns.name + ")=LOWER(?)";
        params.add(loweredName);

        try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
            bindParams(ps, params);
            int rows = ps.executeUpdate();
            debugLog("Updated AuthMe record for " + normalizedName + ", rows=" + rows);
            return rows > 0;
        }
    }

    private boolean insertAuthmeRecord(Connection connection, String table, ColumnConfig columns,
                                       String loweredName, String normalizedName, String passwordHash, String email, long now)
        throws SQLException {
        List<String> insertColumns = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        insertColumns.add(columns.name);
        params.add(loweredName);
        insertColumns.add(columns.realName);
        params.add(normalizedName);
        insertColumns.add(columns.password);
        params.add(passwordHash);

        addOptionalColumn(insertColumns, params, columns.salt, "");
        addOptionalColumn(insertColumns, params, columns.email, email);
        addOptionalColumn(insertColumns, params, columns.isLogged, 0);
        addOptionalColumn(insertColumns, params, columns.hasSession, 0);
        addOptionalColumn(insertColumns, params, columns.lastIp, "");
        addOptionalColumn(insertColumns, params, columns.lastLogin, now);
        addOptionalColumn(insertColumns, params, columns.registerDate, now);
        addOptionalColumn(insertColumns, params, columns.registerIp, "");
        addOptionalColumn(insertColumns, params, columns.lastLocX, 0.0D);
        addOptionalColumn(insertColumns, params, columns.lastLocY, 0.0D);
        addOptionalColumn(insertColumns, params, columns.lastLocZ, 0.0D);
        addOptionalColumn(insertColumns, params, columns.lastLocWorld, "world");
        addOptionalColumn(insertColumns, params, columns.playerUuid, null);

        String placeholders = String.join(", ", insertColumns.stream().map(c -> "?").toList());
        String insertSql = "INSERT INTO " + table + " (" + String.join(", ", insertColumns)
            + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
            bindParams(ps, params);
            int rows = ps.executeUpdate();
            debugLog("Inserted AuthMe record for " + normalizedName + ", rows=" + rows);
            return rows > 0;
        }
    }

    private void addOptionalColumn(List<String> columns, List<Object> params, String columnName, Object value) {
        if (columnName != null && !columnName.isEmpty()) {
            columns.add(columnName);
            params.add(value);
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private Connection getConnection() throws SQLException {
        String dbType = plugin.getConfig().getString("authme.database.type", "mysql").toLowerCase(Locale.ROOT);
        if ("sqlite".equals(dbType)) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found", e);
            }
            String sqlitePath = plugin.getConfig().getString("authme.database.sqlite.path", "plugins/AuthMe/authme.db");
            File dbFile = new File(sqlitePath);
            if (!dbFile.isAbsolute()) {
                dbFile = new File(plugin.getServer().getWorldContainer(), sqlitePath);
            }
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            debugLog("Connecting to AuthMe SQLite DB: " + dbFile.getAbsolutePath());
            return DriverManager.getConnection(url);
        }

        String host = plugin.getConfig().getString("authme.database.mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("authme.database.mysql.port", 3306);
        String database = plugin.getConfig().getString("authme.database.mysql.database", "authme");
        String user = plugin.getConfig().getString("authme.database.mysql.user", "root");
        String password = plugin.getConfig().getString("authme.database.mysql.password", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";

        debugLog("Connecting to AuthMe MySQL DB: " + host + ":" + port + "/" + database);
        return DriverManager.getConnection(url, user, password);
    }

    private String hashPassword(String plainPassword) {
        // AuthMe default algorithm: SHA256, format: $SHA$<salt>$<sha256(sha256(password)+salt)>
        String salt = randomHex(SHA256_SALT_LENGTH);
        String hash = sha256(sha256(plainPassword) + salt);
        return "$SHA$" + salt + "$" + hash;
    }

    private String randomHex(int len) {
        StringBuilder sb = new StringBuilder(Math.max(len, 1));
        while (sb.length() < len) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.substring(0, len);
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 hash", e);
        }
    }

    private String getTableName() {
        return sanitizeIdentifier(
            plugin.getConfig().getString("authme.database.table", "authme"),
            "authme"
        );
    }

    private ColumnConfig getColumns() {
        ColumnConfig columns = new ColumnConfig();
        columns.id = getColumn("mySQLColumnId", "id", true);
        columns.name = getColumn("mySQLColumnName", "username", false);
        columns.realName = getColumn("mySQLRealName", "realname", false);
        columns.password = getColumn("mySQLColumnPassword", "password", false);
        columns.salt = getColumn("mySQLColumnSalt", "", true);
        columns.email = getColumn("mySQLColumnEmail", "email", true);
        columns.isLogged = getColumn("mySQLColumnLogged", "isLogged", true);
        columns.hasSession = getColumn("mySQLColumnHasSession", "hasSession", true);
        columns.lastIp = getColumn("mySQLColumnIp", "ip", true);
        columns.lastLogin = getColumn("mySQLColumnLastLogin", "lastlogin", true);
        columns.registerDate = getColumn("mySQLColumnRegisterDate", "regdate", true);
        columns.registerIp = getColumn("mySQLColumnRegisterIp", "regip", true);
        columns.lastLocX = getColumn("mySQLlastlocX", "x", true);
        columns.lastLocY = getColumn("mySQLlastlocY", "y", true);
        columns.lastLocZ = getColumn("mySQLlastlocZ", "z", true);
        columns.lastLocWorld = getColumn("mySQLlastlocWorld", "world", true);
        columns.playerUuid = getColumn("mySQLPlayerUUID", "", true);
        return columns;
    }

    private String getColumn(String key, String defaultValue, boolean optional) {
        String configured = plugin.getConfig().getString("authme.database.columns." + key, defaultValue);
        if (configured == null || configured.trim().isEmpty()) {
            return optional ? "" : defaultValue;
        }
        return sanitizeIdentifier(configured.trim(), defaultValue);
    }

    private String sanitizeIdentifier(String raw, String fallback) {
        if (raw != null && raw.matches("^[A-Za-z0-9_]+$")) {
            return raw;
        }
        debugLog("Invalid SQL identifier '" + raw + "', fallback to '" + fallback + "'");
        return fallback;
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] AuthmeService: " + msg);
        }
    }

    private static class ColumnConfig {
        private String id;
        private String name;
        private String realName;
        private String password;
        private String salt;
        private String email;
        private String isLogged;
        private String hasSession;
        private String lastIp;
        private String lastLogin;
        private String registerDate;
        private String registerIp;
        private String lastLocX;
        private String lastLocY;
        private String lastLocZ;
        private String lastLocWorld;
        private String playerUuid;
    }
}
