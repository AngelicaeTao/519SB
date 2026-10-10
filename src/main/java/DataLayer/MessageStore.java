package DataLayer;

import InfrastructureLayer.DatabaseManager;
import InfrastructureLayer.HttpUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 聊天历史记录存储（SQLite） */
public class MessageStore {

    private final int maxHistory;

    public MessageStore(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public void add(String username, String text, String time, String type) {
        if (username == null) username = "";
        if (text == null) text = "";
        if (time == null) time = "";
        if (type == null) type = "text";

        long now = System.currentTimeMillis();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO messages(username, text, time, type, created_at) VALUES(?,?,?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, text);
            ps.setString(3, time);
            ps.setString(4, type);
            ps.setLong(5, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MessageStore] 插入消息失败：" + e.getMessage());
            return;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM messages WHERE id NOT IN (" +
                             "SELECT id FROM messages ORDER BY id DESC LIMIT ?)")) {
            ps.setInt(1, maxHistory);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MessageStore] 清理旧消息失败：" + e.getMessage());
        }
    }

    public List<String> snapshot() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT m.username, m.text, m.time, m.type, " +
                "COALESCE(u.nickname, m.username) AS nickname, u.avatar_id " +
                "FROM messages m LEFT JOIN users u ON m.username = u.username " +
                "ORDER BY m.id DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maxHistory);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> reversed = new ArrayList<>();
                while (rs.next()) {
                    String avatarId = rs.getString("avatar_id");
                    String json = "{\"username\":\"" + HttpUtil.escapeJson(rs.getString("username"))
                            + "\",\"nickname\":\"" + HttpUtil.escapeJson(rs.getString("nickname"))
                            + "\",\"avatarId\":" + (avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(avatarId) + "\"")
                            + ",\"text\":\"" + HttpUtil.escapeJson(rs.getString("text"))
                            + "\",\"time\":\"" + HttpUtil.escapeJson(rs.getString("time"))
                            + "\",\"type\":\"" + HttpUtil.escapeJson(rs.getString("type")) + "\"}";
                    reversed.add(json);
                }
                for (int i = reversed.size() - 1; i >= 0; i--) {
                    result.add(reversed.get(i));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageStore] 查询消息失败：" + e.getMessage());
        }
        return result;
    }

    /** 查询某个用户的所有消息（管理员用），带 id，按时间升序 */
    public List<String> messagesOfUser(String username) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT id, text, time, type FROM messages WHERE username = ? ORDER BY id ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = "{\"id\":" + rs.getLong("id")
                            + ",\"text\":\"" + HttpUtil.escapeJson(rs.getString("text"))
                            + "\",\"time\":\"" + HttpUtil.escapeJson(rs.getString("time"))
                            + "\",\"type\":\"" + HttpUtil.escapeJson(rs.getString("type")) + "\"}";
                    result.add(json);
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageStore] 查询用户消息失败：" + e.getMessage());
        }
        return result;
    }

    /** 删除单条消息 */
    public boolean deleteMessage(long id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM messages WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MessageStore] 删除消息失败：" + e.getMessage());
            return false;
        }
    }

    /** 批量删除消息 */
    public int deleteMessages(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        StringBuilder sql = new StringBuilder("DELETE FROM messages WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MessageStore] 批量删除失败：" + e.getMessage());
            return 0;
        }
    }
}