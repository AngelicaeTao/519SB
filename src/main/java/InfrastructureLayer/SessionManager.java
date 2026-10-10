package InfrastructureLayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 会话管理：SQLite 持久化 + 内存缓存，30 天过期 */
public class SessionManager {

    /** 过期时间（天），从 config.properties 读取 */
    private static final long TTL_MS = ConfigurationLayer.Config.SESSION_TTL_DAYS * 24L * 3600 * 1000;

    /** 内存缓存，避免每次请求都查库 */
    private final Map<String, Session> cache = new ConcurrentHashMap<>();

    private static class Session {
        String username;
        long lastActive;
    }

    public String createSession(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO sessions(token, username, last_active) VALUES(?,?,?)")) {
            ps.setString(1, token);
            ps.setString(2, username);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[Session] 创建失败：" + e.getMessage());
            return null;
        }

        Session s = new Session();
        s.username = username;
        s.lastActive = now;
        cache.put(token, s);
        return token;
    }

    public String getUser(String token) {
        if (token == null) return null;
        long now = System.currentTimeMillis();

        // 1. 内存缓存命中
        Session s = cache.get(token);
        if (s != null) {
            if (now - s.lastActive > TTL_MS) {
                cache.remove(token);
                removeSession(token);
                return null;
            }
            s.lastActive = now;
            return s.username;
        }

        // 2. 缓存未命中，查 SQLite
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT username, last_active FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long lastActive = rs.getLong("last_active");
                    if (now - lastActive > TTL_MS) {
                        removeSession(token);
                        return null;
                    }
                    String username = rs.getString("username");

                    // 恢复缓存
                    Session ns = new Session();
                    ns.username = username;
                    ns.lastActive = now;
                    cache.put(token, ns);

                    // 更新数据库中的活跃时间
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE sessions SET last_active = ? WHERE token = ?")) {
                        up.setLong(1, now);
                        up.setString(2, token);
                        up.executeUpdate();
                    } catch (SQLException ignored) {}

                    return username;
                }
            }
        } catch (SQLException e) {
            System.err.println("[Session] 查询失败：" + e.getMessage());
        }
        return null;
    }

    public void removeSession(String token) {
        if (token == null) return;
        cache.remove(token);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void removeAllSessionsOfUser(String username) {
        if (username == null) return;
        cache.entrySet().removeIf(e -> username.equals(e.getValue().username));
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM sessions WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /** 定期清理过期会话 */
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        cache.entrySet().removeIf(e -> e.getValue().lastActive < cutoff);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM sessions WHERE last_active < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}