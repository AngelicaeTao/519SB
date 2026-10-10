package DataLayer;

import InfrastructureLayer.DatabaseManager;
import java.sql.Types;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 帖子存储（SQLite） */
public class PostStore {

    /** 创建帖子，返回新 id；失败返回 -1 */
    public long create(String title, String content, String author) {
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO posts(title, content, author, created_at, updated_at, views) " +
                "VALUES(?,?,?,?,?,0)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setString(3, author);
            ps.setLong(4, now);
            ps.setLong(5, now);
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
        String sql = "SELECT id, title, content, author, created_at, updated_at, views " +
                "FROM posts WHERE id = ?";
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

    /** 全部帖子，按 id 倒序（最新在前） */
    public List<Post> listAll() {
        List<Post> result = new ArrayList<>();
        String sql = "SELECT id, title, content, author, created_at, updated_at, views " +
                "FROM posts ORDER BY id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            System.err.println("[PostStore] 列出帖子失败：" + e.getMessage());
        }
        return result;
    }

    public boolean update(long id, String title, String content) {
        String sql = "UPDATE posts SET title = ?, content = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setLong(3, System.currentTimeMillis());
            ps.setLong(4, id);
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
        p.createdAt = rs.getLong("created_at");
        p.updatedAt = rs.getLong("updated_at");
        p.views = rs.getInt("views");
        return p;
    }

    // ==================== 点赞 ====================

    /** 是否已点赞 */
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

    /** 点赞总数 */
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

    /** 切换点赞，返回切换后的状态（true=已点赞，false=已取消） */
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

    /** 发表评论，返回新评论 id；失败返回 -1 */
    /** 发表评论/回复，parentId 为 null 表示顶级评论；返回新 id，失败返回 -1 */
    public long addComment(long postId, String username, String content, Long parentId) {
        String sql = "INSERT INTO post_comments(post_id, username, content, parent_id, created_at) " +
                "VALUES(?,?,?,?,?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, postId);
            ps.setString(2, username);
            ps.setString(3, content);
            if (parentId == null) ps.setNull(4, Types.INTEGER);
            else ps.setLong(4, parentId);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 发表评论失败：" + e.getMessage());
        }
        return -1;
    }

    /** 评论总数 */
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

    /** 查帖子所有评论（带 parentId、昵称、头像），按 id 升序 */
    public List<String> listComments(long postId) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT c.id, c.username, c.content, c.parent_id, c.created_at, " +
                "COALESCE(u.nickname, c.username) AS nickname, u.avatar_id " +
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
                    String json = "{\"id\":" + rs.getLong("id")
                            + ",\"username\":\"" + InfrastructureLayer.HttpUtil.escapeJson(rs.getString("username")) + "\""
                            + ",\"nickname\":\"" + InfrastructureLayer.HttpUtil.escapeJson(rs.getString("nickname")) + "\""
                            + ",\"avatarId\":" + (avatarId == null ? "null" : "\"" + InfrastructureLayer.HttpUtil.escapeJson(avatarId) + "\"")
                            + ",\"content\":\"" + InfrastructureLayer.HttpUtil.escapeJson(rs.getString("content")) + "\""
                            + ",\"parentId\":" + (parentIsNull ? "null" : parentId)
                            + ",\"createdAt\":" + rs.getLong("created_at") + "}";
                    result.add(json);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PostStore] 查询评论失败：" + e.getMessage());
        }
        return result;
    }

    /** 取评论作者，用于权限判断；找不到返回 null */
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

    /** 删除评论 */
    public boolean deleteComment(long commentId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            // 先删子回复
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM post_comments WHERE parent_id = ?")) {
                ps.setLong(1, commentId);
                ps.executeUpdate();
            }
            // 再删自己
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

    /** 取评论信息，用于回复时展示被回复者；找不到返回 null */
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
