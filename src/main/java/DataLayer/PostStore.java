package DataLayer;

import InfrastructureLayer.DatabaseManager;
import java.sql.Types;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 帖子存储（SQLite） */
public class PostStore {

    /** 创建帖子，返回新 id；失败返回 -1 */
    public long create(String title, String content, String author, String images) {
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO posts(title, content, author, created_at, updated_at, views, images) " +
                "VALUES(?,?,?,?,?,0,?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setString(3, author);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.setString(6, images);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 创建帖子失败：" + e.getMessage());
        }
        return -1;
    }

    public Post getById(long id) {
        String sql = "SELECT p.id, p.title, p.content, p.author, p.created_at, p.updated_at, p.views, p.images, " +
                "COALESCE(u.nickname, p.author) AS author_nickname, " +
                "(SELECT COUNT(*) FROM post_likes WHERE post_id = p.id) AS like_count " +
                "FROM posts p LEFT JOIN users u ON p.author = u.username " +
                "WHERE p.id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 查询帖子失败：" + e.getMessage());
        }
        return null;
    }

    /** 全部帖子，按 id 倒序（最新在前），带点赞数 */
    public List<Post> listAll() {
        List<Post> result = new ArrayList<>();
        String sql = "SELECT p.id, p.title, p.content, p.author, p.created_at, p.updated_at, p.views, p.images, " +
                "COALESCE(u.nickname, p.author) AS author_nickname, " +
                "(SELECT COUNT(*) FROM post_likes WHERE post_id = p.id) AS like_count " +
                "FROM posts p LEFT JOIN users u ON p.author = u.username " +
                "ORDER BY p.id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            System.err.println("[PostStore] 列出帖子失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 搜索帖子。
     *
     * @param q     关键词
     * @param field all / title / content / author；其他值按 all 处理
     * @param limit 返回条数上限
     *
     * 作者检索同时匹配账号和昵称——使用者通常只记得昵称。
     * 关键词里的 % 和 _ 会被转义，否则用户输入一个 % 就会匹配到全部帖子。
     */
    public List<Post> search(String q, String field, int limit) {
        List<Post> result = new ArrayList<>();
        if (q == null || q.isBlank()) return result;
        if (limit <= 0 || limit > 200) limit = 50;

        String like = escapeLike(q.trim());
        String cols = "SELECT p.id, p.title, p.content, p.author, p.created_at, p.updated_at, p.views, p.images, "
                + "COALESCE(u.nickname, p.author) AS author_nickname, "
                + "(SELECT COUNT(*) FROM post_likes WHERE post_id = p.id) AS like_count "
                + "FROM posts p LEFT JOIN users u ON p.author = u.username ";

        String where;
        int params;
        switch (field == null ? "all" : field) {
            case "title" -> {
                where = "WHERE p.title LIKE ? ESCAPE '\\' ";
                params = 1;
            }
            case "content" -> {
                where = "WHERE p.content LIKE ? ESCAPE '\\' ";
                params = 1;
            }
            case "author" -> {
                where = "WHERE (p.author LIKE ? ESCAPE '\\' OR COALESCE(u.nickname, p.author) LIKE ? ESCAPE '\\') ";
                params = 2;
            }
            default -> {
                where = "WHERE (p.title LIKE ? ESCAPE '\\' OR p.content LIKE ? ESCAPE '\\' "
                        + "OR p.author LIKE ? ESCAPE '\\' OR COALESCE(u.nickname, p.author) LIKE ? ESCAPE '\\') ";
                params = 4;
            }
        }

        String sql = cols + where + "ORDER BY p.id DESC LIMIT ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + like + "%";
            int idx = 1;
            for (int i = 0; i < params; i++) ps.setString(idx++, pattern);
            ps.setInt(idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 搜索帖子失败：" + e.getMessage());
        }
        return result;
    }

    /** 转义 LIKE 的通配符，让它们按字面量匹配 */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public boolean update(long id, String title, String content, String images) {        String sql = "UPDATE posts SET title = ?, content = ?, updated_at = ?, images = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, images);
            ps.setLong(5, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[PostStore] 更新帖子失败：" + e.getMessage());
            return false;
        }
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM posts WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[PostStore] 删除帖子失败：" + e.getMessage());
            return false;
        }
    }

    public void incrementViews(long id) {
        String sql = "UPDATE posts SET views = views + 1 WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private Post map(ResultSet rs) throws SQLException {
        Post p = new Post();
        p.id = rs.getLong("id");
        p.title = rs.getString("title");
        p.content = rs.getString("content");
        p.author = rs.getString("author");
        p.authorNickname = rs.getString("author_nickname");
        p.images = rs.getString("images");
        p.createdAt = rs.getLong("created_at");
        p.updatedAt = rs.getLong("updated_at");
        p.views = rs.getInt("views");
        try { p.likeCount = rs.getInt("like_count"); } catch (SQLException ignored) { p.likeCount = 0; }
        return p;
    }

    // ==================== 点赞 ====================

    public boolean isLiked(long postId, String username) {
        String sql = "SELECT 1 FROM post_likes WHERE post_id = ? AND username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 查询点赞失败：" + e.getMessage());
            return false;
        }
    }

    public int getLikeCount(long postId) {
        String sql = "SELECT COUNT(*) FROM post_likes WHERE post_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 查询点赞数失败：" + e.getMessage());
        }
        return 0;
    }

    public boolean toggleLike(long postId, String username) {
        try (Connection conn = DatabaseManager.getConnection()) {
            boolean exists;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM post_likes WHERE post_id = ? AND username = ?")) {
                ps.setLong(1, postId);
                ps.setString(2, username);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (exists) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM post_likes WHERE post_id = ? AND username = ?")) {
                    ps.setLong(1, postId);
                    ps.setString(2, username);
                    ps.executeUpdate();
                }
                return false;
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO post_likes(post_id, username, created_at) VALUES(?,?,?)")) {
                    ps.setLong(1, postId);
                    ps.setString(2, username);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 切换点赞失败：" + e.getMessage());
            return false;
        }
    }

    // ==================== 评论 ====================

    public long addComment(long postId, String username, String content, Long parentId) {
        return addComment(postId, username, content, parentId, null);
    }

    /**
     * 发表评论。
     *
     * @param imagesJson 配图 id 的 JSON 数组（如 ["ab12...","cd34..."]）；
     *                   由调用方校验 id 归属后传入，这里按原文存。
     */
    public long addComment(long postId, String username, String content, Long parentId, String imagesJson) {
        String sql = "INSERT INTO post_comments(post_id, username, content, parent_id, created_at, images) " +
                "VALUES(?,?,?,?,?,?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, postId);
            ps.setString(2, username);
            ps.setString(3, content);
            if (parentId == null) ps.setNull(4, Types.INTEGER);
            else ps.setLong(4, parentId);
            ps.setLong(5, System.currentTimeMillis());
            if (imagesJson == null || imagesJson.isEmpty()) ps.setNull(6, Types.VARCHAR);
            else ps.setString(6, imagesJson);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 发表评论失败：" + e.getMessage());
        }
        return -1;
    }

    public int getCommentCount(long postId) {
        String sql = "SELECT COUNT(*) FROM post_comments WHERE post_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 查询评论数失败：" + e.getMessage());
        }
        return 0;
    }

    /** 查帖子所有评论（带 parentId、昵称、头像、被回复者昵称），按 id 升序 */
    public List<String> listComments(long postId) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT c.id, c.username, c.content, c.parent_id, c.created_at, c.images, " +
                "COALESCE(u.nickname, c.username) AS nickname, u.avatar_id, " +
                "(SELECT COALESCE(u2.nickname, c2.username) FROM post_comments c2 " +
                " LEFT JOIN users u2 ON c2.username = u2.username " +
                " WHERE c2.id = c.parent_id) AS reply_to_nick " +
                "FROM post_comments c LEFT JOIN users u ON c.username = u.username " +
                "WHERE c.post_id = ? ORDER BY c.id ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String avatarId = rs.getString("avatar_id");
                    long parentId = rs.getLong("parent_id");
                    boolean parentIsNull = rs.wasNull();
                    String replyToNick = rs.getString("reply_to_nick");
                    // images 列存的是已校验过的 id 数组 JSON，直接内嵌
                    String images = null;
                    try { images = rs.getString("images"); } catch (SQLException ignored) {}
                    String json = "{\"id\":" + rs.getLong("id")
                            + ",\"username\":\"" + InfrastructureLayer.HttpUtil.escapeJson(rs.getString("username")) + "\""
                            + ",\"nickname\":\"" + InfrastructureLayer.HttpUtil.escapeJson(rs.getString("nickname")) + "\""
                            + ",\"avatarId\":" + (avatarId == null ? "null" : "\"" + InfrastructureLayer.HttpUtil.escapeJson(avatarId) + "\"")
                            + ",\"content\":\"" + InfrastructureLayer.HttpUtil.escapeJson(rs.getString("content")) + "\""
                            + ",\"parentId\":" + (parentIsNull ? "null" : parentId)
                            + ",\"replyToNick\":" + (replyToNick == null ? "null" : "\"" + InfrastructureLayer.HttpUtil.escapeJson(replyToNick) + "\"")
                            + ",\"images\":" + (images == null || images.isBlank() ? "[]" : images)
                            + ",\"createdAt\":" + rs.getLong("created_at") + "}";
                    result.add(json);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 查询评论失败：" + e.getMessage());
        }
        return result;
    }

    public String getCommentAuthor(long commentId) {
        String sql = "SELECT username FROM post_comments WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, commentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("username");
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 查询评论作者失败：" + e.getMessage());
        }
        return null;
    }

    public boolean deleteComment(long commentId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM post_comments WHERE parent_id = ?")) {
                ps.setLong(1, commentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM post_comments WHERE id = ?")) {
                ps.setLong(1, commentId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 删除评论失败：" + e.getMessage());
            return false;
        }
    }

    public String[] getCommentInfo(long commentId) {
        String sql = "SELECT c.username, COALESCE(u.nickname, c.username) AS nickname " +
                "FROM post_comments c LEFT JOIN users u ON c.username = u.username " +
                "WHERE c.id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, commentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{ rs.getString("username"), rs.getString("nickname") };
                }
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 查询评论信息失败：" + e.getMessage());
        }
        return null;
    }
}