package DataLayer;

import InfrastructureLayer.DatabaseManager;
import InfrastructureLayer.HttpUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 聊天历史记录存储（SQLite） */
public class MessageStore {

    private final int maxHistory;
    private final AtomicInteger insertCounter = new AtomicInteger(0);

    public MessageStore(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public long add(String username, String text, String time, String type, String replyJson) {
        return add(username, text, time, type, replyJson, null);
    }

    /**
     * 插入消息。
     *
     * @param replyJson 回复引用的摘要 JSON（{"id":1,"author":"..","text":".."}）。
     *                  由调用方负责转义后传入，这里按原文存；无引用时传 null。
     * @param fileName  文件消息的原始文件名（已由调用方净化）；非文件消息传 null。
     */
    public long add(String username, String text, String time, String type,
                    String replyJson, String fileName) {
        if (username == null) username = "";
        if (text == null) text = "";
        if (time == null) time = "";
        if (type == null) type = "text";

        long now = System.currentTimeMillis();
        long newId = -1;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO messages(username, text, time, type, created_at, reply, filename) " +
                             "VALUES(?,?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, text);
            ps.setString(3, time);
            ps.setString(4, type);
            ps.setLong(5, now);
            if (replyJson == null || replyJson.isEmpty()) ps.setNull(6, java.sql.Types.VARCHAR);
            else ps.setString(6, replyJson);
            if (fileName == null || fileName.isEmpty()) ps.setNull(7, java.sql.Types.VARCHAR);
            else ps.setString(7, fileName);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) newId = rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("[MessageStore] 插入消息失败：" + e.getMessage());
            return -1;
        }

        if (insertCounter.incrementAndGet() % 20 == 0) {
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

        return newId;
    }

    public List<String> recent(int limit) {
        String sql = "SELECT m.id, m.username, m.text, m.time, m.type, m.created_at, m.reply, m.filename, " +
                "COALESCE(u.nickname, m.username) AS nickname, u.avatar_id " +
                "FROM messages m LEFT JOIN users u ON m.username = u.username " +
                "ORDER BY m.id DESC LIMIT ?";
        return queryList(sql, stmt -> stmt.setInt(1, limit));
    }

    public List<String> loadBefore(long beforeId, int limit) {
        String sql = "SELECT m.id, m.username, m.text, m.time, m.type, m.created_at, m.reply, m.filename, " +
                "COALESCE(u.nickname, m.username) AS nickname, u.avatar_id " +
                "FROM messages m LEFT JOIN users u ON m.username = u.username " +
                "WHERE m.id < ? ORDER BY m.id DESC LIMIT ?";
        return queryList(sql, stmt -> {
            stmt.setLong(1, beforeId);
            stmt.setInt(2, limit);
        });
    }

    /**
     * 抽取公共查询：逐级降级，保证老库也能读出历史。
     *
     * 四级分别是：正常查询 → 老库没有 filename 列 → 没有 reply 列
     * → 连 created_at 也没有。
     * 每级都保证结果集里一定有 created_at / reply / filename 三列，
     * 这样 rowToJson 读取时不会因为缺列抛异常。
     */
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<String> queryList(String sql, Binder binder) {
        String noFilename = sql.replace("m.created_at, m.reply, m.filename,",
                "m.created_at, m.reply, NULL AS filename,");
        String[] variants = {
                sql,
                noFilename,
                noFilename.replace("m.created_at, m.reply,", "m.created_at, NULL AS reply,"),
                noFilename.replace("m.created_at, m.reply,", "0 AS created_at, NULL AS reply,")
        };

        SQLException last = null;
        for (String variant : variants) {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(variant)) {
                binder.bind(ps);
                List<String> result = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> reversed = new ArrayList<>();
                    while (rs.next()) reversed.add(rowToJson(rs, true));
                    for (int i = reversed.size() - 1; i >= 0; i--) result.add(reversed.get(i));
                }
                return result;
            } catch (SQLException e) {
                last = e;
            }
        }
        System.err.println("[MessageStore] 查询消息失败：" + (last == null ? "unknown" : last.getMessage()));
        return new ArrayList<>();
    }

    public boolean recall(long id, String username, long maxAgeMs) {
        if (username == null) return false;
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM messages WHERE id = ? AND username = ? AND created_at > ?")) {
            ps.setLong(1, id);
            ps.setString(2, username);
            ps.setLong(3, cutoff);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MessageStore] 撤回消息失败：" + e.getMessage());
            return false;
        }
    }

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
            for (int i = 0; i < ids.size(); i++) ps.setLong(i + 1, ids.get(i));
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MessageStore] 批量删除失败：" + e.getMessage());
            return 0;
        }
    }

    private String rowToJson(ResultSet rs, boolean hasCreatedAt) throws SQLException {
        String avatarId = rs.getString("avatar_id");
        long createdAt = 0;
        if (hasCreatedAt) {
            try { createdAt = rs.getLong("created_at"); } catch (SQLException ignored) {}
        }
        // reply 列里存的是生产者已转义好的 JSON 片段，或 NULL；两种都能直接拼
        String reply = null;
        try { reply = rs.getString("reply"); } catch (SQLException ignored) {}
        String fileName = null;
        try { fileName = rs.getString("filename"); } catch (SQLException ignored) {}
        return "{\"id\":" + rs.getLong("id")
                + ",\"createdAt\":" + createdAt
                + ",\"username\":\"" + HttpUtil.escapeJson(rs.getString("username"))
                + "\",\"nickname\":\"" + HttpUtil.escapeJson(rs.getString("nickname"))
                + "\",\"avatarId\":" + (avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(avatarId) + "\"")
                + ",\"text\":\"" + HttpUtil.escapeJson(rs.getString("text"))
                + "\",\"time\":\"" + HttpUtil.escapeJson(rs.getString("time"))
                + "\",\"type\":\"" + HttpUtil.escapeJson(rs.getString("type"))
                + "\",\"reply\":" + (reply == null || reply.isEmpty() ? "null" : reply)
                + ",\"fileName\":" + (fileName == null || fileName.isEmpty()
                        ? "null" : "\"" + HttpUtil.escapeJson(fileName) + "\"")
                + "}";
    }
}