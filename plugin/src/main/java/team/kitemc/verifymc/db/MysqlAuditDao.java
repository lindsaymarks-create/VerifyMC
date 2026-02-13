package team.kitemc.verifymc.db;

import java.sql.*;
import java.util.*;

public class MysqlAuditDao implements AuditDao {
    private final Connection conn;

    public MysqlAuditDao(Properties mysqlConfig) throws SQLException {
        String url = "jdbc:mysql://" + mysqlConfig.getProperty("host") + ":" +
                mysqlConfig.getProperty("port") + "/" +
                mysqlConfig.getProperty("database") + "?useSSL=false&characterEncoding=utf8";
        conn = DriverManager.getConnection(url, mysqlConfig.getProperty("user"), mysqlConfig.getProperty("password"));
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS audits (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "action VARCHAR(32)," +
                    "operator VARCHAR(32)," +
                    "target VARCHAR(32)," +
                    "detail TEXT," +
                    "timestamp BIGINT)");
        }
    }

    @Override
    public void addAudit(Map<String, Object> audit) {
        if (audit == null) return;
        Object actionObj = audit.get("action");
        Object operatorObj = audit.get("operator");
        Object targetObj = audit.get("target");
        Object timestampObj = audit.get("timestamp");
        if (!(actionObj instanceof String) || ((String) actionObj).isBlank()) return;
        if (!(operatorObj instanceof String) || ((String) operatorObj).isBlank()) return;
        if (!(targetObj instanceof String) || ((String) targetObj).isBlank()) return;
        if (!(timestampObj instanceof Number)) return;

        String action = (String) actionObj;
        String operator = (String) operatorObj;
        String target = (String) targetObj;
        String detail = String.valueOf(audit.getOrDefault("detail", ""));
        long timestamp = ((Number) timestampObj).longValue();

        String sql = "INSERT INTO audits (action, operator, target, detail, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, action);
            ps.setString(2, operator);
            ps.setString(3, target);
            ps.setString(4, detail);
            ps.setLong(5, timestamp);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public List<Map<String, Object>> getAllAudits() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM audits";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> audit = new HashMap<>();
                audit.put("id", rs.getInt("id"));
                audit.put("action", rs.getString("action"));
                audit.put("operator", rs.getString("operator"));
                audit.put("target", rs.getString("target"));
                audit.put("detail", rs.getString("detail"));
                audit.put("timestamp", rs.getLong("timestamp"));
                result.add(audit);
            }
        } catch (SQLException ignored) {}
        return result;
    }

    @Override
    public void save() {
        // MySQL storage: save() called (no-op)
    }
} 
