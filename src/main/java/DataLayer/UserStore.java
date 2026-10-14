package DataLayer;

import InfrastructureLayer.DatabaseManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 用户数据存储（SQLite），密码用 PBKDF2 加盐哈希 */
public class UserStore {

    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int PBKDF2_KEY_LEN = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public UserStore() {
    }

    /** 如果没有任何管理员，创建默认管理员 */
    public void ensureDefaultAdmin(String username, String password) {
        int adminCount = 0;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE is_admin = 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) adminCount = rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[UserStore] 检查管理员失败：" + e.getMessage());
            return;
        }

        if (adminCount == 0) {
            if (register(username, password, "管理员", true)) {
                System.out.println("[UserStore] 已创建默认管理员：" + username + " / " + password);
                System.out.println("[UserStore] 请登录后立即修改管理员密码！");
            } else {
                System.err.println("[UserStore] 创建默认管理员失败");
            }
        }
    }

    public boolean register(String username, String password, String nickname) {
        return register(username, password, nickname, false);
    }

    public boolean register(String username, String password, String nickname, boolean isAdmin) {
        if (username == null || username.isBlank()) return false;
        if (password == null || password.isEmpty()) return false;
        if (nickname == null || nickname.isBlank()) nickname = username;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users(username, password_hash, nickname, avatar_id, is_admin, created_at) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, hash(password));
            ps.setString(3, nickname);
            ps.setString(4, null);
            ps.setInt(5, isAdmin ? 1 : 0);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                return false;
            }
            System.err.println("[UserStore] 注册失败：" + e.getMessage());
            return false;
        }
    }

    /** 校验密码（兼容旧版纯 SHA-256） */
    public boolean verify(String username, String password) {
        if (username == null || password == null) return false;
        String stored = null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) stored = rs.getString("password_hash");
        } catch (SQLException e) {
            System.err.println("[UserStore] 校验失败：" + e.getMessage());
            return false;
        }
        if (stored == null) return false;

        // 新格式（salt:hash）或旧格式（纯 SHA-256）
        if (stored.contains(":")) {
            return verifyPBKDF2(password, stored);
        } else {
            // 旧格式，验证通过后自动升级为 PBKDF2
            if (stored.equals(legacySha256(password))) {
                changePassword(username, password);
                return true;
            }
            return false;
        }
    }

    public User getUser(String username) {
        if (username == null) return null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT username, nickname, avatar_id, is_admin, created_at, theme FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            System.err.println("[UserStore] 查询失败：" + e.getMessage());
        }
        return null;
    }

    public boolean changePassword(String username, String newPassword) {
        if (username == null || newPassword == null || newPassword.isEmpty()) return false;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE username = ?")) {
            ps.setString(1, hash(newPassword));
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserStore] 修改密码失败：" + e.getMessage());
            return false;
        }
    }

    public boolean updateProfile(String username, String nickname, String avatarId) {
        if (username == null) return false;
        // 昵称变了就留一条历史，便于以后回看用过哪些昵称
        String before = getNickname(username);
        boolean changed = before != null && !before.equals(nickname);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET nickname = ?, avatar_id = ? WHERE username = ?")) {
            ps.setString(1, nickname);
            ps.setString(2, avatarId);
            ps.setString(3, username);
            boolean ok = ps.executeUpdate() > 0;
            if (ok && changed) recordNickname(username, nickname);
            return ok;
        } catch (SQLException e) {
            System.err.println("[UserStore] 更新资料失败：" + e.getMessage());
            return false;
        }
    }

    /** 取当前昵称；用户不存在返回 null */
    public String getNickname(String username) {
        if (username == null) return null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nickname FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    /** 记一条昵称历史 */
    public boolean recordNickname(String username, String nickname) {
        if (username == null || nickname == null || nickname.isBlank()) return false;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO nickname_history(username, nickname, changed_at) VALUES(?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, nickname);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[UserStore] 记录昵称历史失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 取某用户的昵称历史，最近的在前。
     * 返回的每项是 [nickname, changedAt]。
     */
    public List<String[]> nicknameHistory(String username, int limit) {
        List<String[]> out = new ArrayList<>();
        if (username == null) return out;
        if (limit <= 0) limit = 50;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nickname, changed_at FROM nickname_history " +
                             "WHERE username = ? ORDER BY changed_at DESC, id DESC LIMIT ?")) {
            ps.setString(1, username);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new String[]{ rs.getString("nickname"), String.valueOf(rs.getLong("changed_at")) });
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserStore] 查询昵称历史失败：" + e.getMessage());
        }
        return out;
    }

    public List<User> listAll() {
        List<User> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT username, nickname, avatar_id, is_admin, created_at, theme FROM users ORDER BY created_at ASC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapUser(rs));
        } catch (SQLException e) {
            System.err.println("[UserStore] 列出用户失败：" + e.getMessage());
        }
        return result;
    }

    public boolean adminUpdate(String username, String nickname, String newPassword,
                               String avatarId, boolean isAdmin) {
        if (username == null) return false;
        try (Connection conn = DatabaseManager.getConnection()) {
            if (newPassword != null && !newPassword.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET nickname = ?, avatar_id = ?, is_admin = ?, password_hash = ? WHERE username = ?")) {
                    ps.setString(1, nickname);
                    ps.setString(2, avatarId);
                    ps.setInt(3, isAdmin ? 1 : 0);
                    ps.setString(4, hash(newPassword));
                    ps.setString(5, username);
                    return ps.executeUpdate() > 0;
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET nickname = ?, avatar_id = ?, is_admin = ? WHERE username = ?")) {
                    ps.setString(1, nickname);
                    ps.setString(2, avatarId);
                    ps.setInt(3, isAdmin ? 1 : 0);
                    ps.setString(4, username);
                    return ps.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserStore] 管理员更新失败：" + e.getMessage());
            return false;
        }
    }

    public boolean exists(String username) {
        return getUser(username) != null;
    }

    public boolean delete(String username) {
        if (username == null) return false;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserStore] 删除用户失败：" + e.getMessage());
            return false;
        }
    }

    public void deleteUserMessages(String username) {
        if (username == null) return;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM messages WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[UserStore] 删除用户消息失败：" + e.getMessage());
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.username = rs.getString("username");
        u.nickname = rs.getString("nickname");
        u.avatarId = rs.getString("avatar_id");
        u.isAdmin = rs.getInt("is_admin") == 1;
        u.createdAt = rs.getLong("created_at");
        // theme 列在老库里可能不存在，读不到就当作 null（即默认配色）
        try { u.theme = rs.getString("theme"); } catch (SQLException ignored) {}
        return u;
    }

    /** 该用户发过的聊天消息数（用于个人主页的统计） */
    public int messageCount(String username) {
        if (username == null) return 0;
        return countOf("SELECT COUNT(*) FROM messages WHERE username = ?", username);
    }

    /** 该用户发过的帖子数 */
    public int postCount(String username) {
        if (username == null) return 0;
        return countOf("SELECT COUNT(*) FROM posts WHERE author = ?", username);
    }

    /** 计数查询的公共实现；出错返回 0，不让统计失败拖垮页面 */
    private int countOf(String sql, String username) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    /** 取用户选择的配色主题名；没设过或用户不存在返回 null */
    public String getTheme(String username) {
        if (username == null) return null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT theme FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    /** 设置配色主题；主题名合法性由调用方（Theme）校验 */
    public boolean setTheme(String username, String theme) {
        if (username == null) return false;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET theme = ? WHERE username = ?")) {
            ps.setString(1, theme);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserStore] 保存配色失败：" + e.getMessage());
            return false;
        }
    }

    // ==================== 密码哈希 ====================

    private String hash(String password) {
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] hashBytes = pbkdf2(password.toCharArray(), salt);
            return toHex(salt) + ":" + toHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean verifyPBKDF2(String password, String stored) {
        try {
            int sep = stored.indexOf(':');
            byte[] salt = fromHex(stored.substring(0, sep));
            byte[] expected = fromHex(stored.substring(sep + 1));
            byte[] actual = pbkdf2(password.toCharArray(), salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] pbkdf2(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LEN);
        return factory.generateSecret(spec).getEncoded();
    }

    private String legacySha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}