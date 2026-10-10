package InfrastructureLayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 会话管理：token → 用户，24 小时过期 */
public class SessionManager {

    private static final long TTL_MS = 24L * 3600 * 1000;

    private static class Session {
        String username;
        long lastActive;
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String createSession(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Session s = new Session();
        s.username = username;
        s.lastActive = System.currentTimeMillis();
        sessions.put(token, s);
        return token;
    }

    public String getUser(String token) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        long now = System.currentTimeMillis();
        if (now - s.lastActive > TTL_MS) {
            sessions.remove(token);
            return null;
        }
        s.lastActive = now;
        return s.username;
    }

    public void removeSession(String token) {
        if (token != null) sessions.remove(token);
    }

    public void removeAllSessionsOfUser(String username) {
        if (username == null) return;
        sessions.entrySet().removeIf(e -> username.equals(e.getValue().username));
    }

    /** 定期清理过期会话 */
    public void cleanup() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> now - e.getValue().lastActive > TTL_MS);
    }
}