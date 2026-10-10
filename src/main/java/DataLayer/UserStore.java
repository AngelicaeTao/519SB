package DataLayer;

import InfrastructureLayer.DatabaseManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 用户数据存储（SQLite），密码经 SHA-256 哈希 */
public class UserStore {

    public UserStore() {
    }

    /** 如果没有任何管理员，创建默认管理员 admin / admin123 */
    public void ensureDefaultAdmin() {
        int adminCount = 0;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE is_admin = 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) adminCount = rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[UserStore] 检查管理员失败：" + e.getMessage());
            return;
        }

        // 连接已关闭，再执行注册
        if (adminCount == 0) {
            if (register("admin", "admin123", "管理员", true)) {
                System.out.println("[UserStore] 已创建默认管理员：admin / admin123");
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

    public boolean verify(String username, String password) {
        if (username == null || password == null) return false;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("password_hash").equals(hash(password));
            }
        } catch (SQLException e) {
            System.err.println("[UserStore] 校验失败：" + e.getMessage());
        }
        return false;
    }

    public User getUser(String username) {
        if (username == null) return null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT username, nickname, avatar_id, is_admin, created_at FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
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
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET nickname = ?, avatar_id = ? WHERE username = ?")) {
            ps.setString(1, nickname);
            ps.setString(2, avatarId);
            ps.setString(3, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserStore] 更新资料失败：" + e.getMessage());
            return false;
        }
    }

    public List<User> listAll() {
        List<User> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT username, nickname, avatar_id, is_admin, created_at FROM users ORDER BY created_at ASC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(mapUser(rs));
            }
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

    /** 删除用户 */
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

    /** 删除用户的所有聊天消息 */
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
        return u;
    }

    private String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}