package DataLayer;

import InfrastructureLayer.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** 图片归属校验。上传与读取在 HandlerLayer.ImageHandler，这里只做"这张图是不是你的"这类判断。 */
public final class ImageStore {

    private ImageStore() {}

    /**
     * 校验给定的一批图片 id 是否**全部**存在、属于该用户、且用途匹配。
     *
     * 安全要点：发帖/评论时客户端提交的是图片 id，必须确认这些 id 确实归提交者所有。
     * 否则任何人都能引用别人的图片 id，把别人的图挂到自己的内容下面。
     *
     * @return true 表示全部合法；ids 为空时返回 true（表示"没有配图"）
     */
    public static boolean ownsAll(String username, List<String> ids, String type) {
        if (ids == null || ids.isEmpty()) return true;
        if (username == null) return false;

        // 逐个查而不是拼 IN (...)：参数数量可控、也不用担心拼串，
        // 配图数量本来就有上限，性能上无所谓。
        String sql = "SELECT 1 FROM images WHERE id = ? AND username = ? AND type = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String id : ids) {
                if (!isValidId(id)) return false;
                ps.setString(1, id);
                ps.setString(2, username);
                ps.setString(3, type);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("[ImageStore] 归属校验失败：" + e.getMessage());
            // 校验出错时按"不允许"处理，避免把校验失败的图放进去
            return false;
        }
    }

    /** id 必须是 32 位小写 hex，与上传时生成的格式一致 */
    public static boolean isValidId(String id) {
        return id != null && id.matches("^[a-f0-9]{32}$");
    }
}
