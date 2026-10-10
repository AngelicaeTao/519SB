package InfrastructureLayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 简单的内存会话管理：token → username */
public class SessionManager {

    private final ConcurrentHashMap<String, String> tokenToUser = new ConcurrentHashMap<>();

    public String createSession(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToUser.put(token, username);
        return token;
    }

    public String getUser(String token) {
        if (token == null) return null;
        return tokenToUser.get(token);
    }

    public void removeSession(String token) {
        if (token != null) tokenToUser.remove(token);
    }

    /** 删除某个用户的所有会话（踢出） */
    public void removeAllSessionsOfUser(String username) {
        if (username == null) return;
        tokenToUser.entrySet().removeIf(e -> username.equals(e.getValue()));
    }
}