package HandlerLayer;

import ConfigurationLayer.Config;
import InfrastructureLayer.DatabaseManager;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;

/** 图片上传与下载，路径 /image/*，数据存 SQLite BLOB */
public class ImageHandler implements HttpHandler {

    private final SessionManager sessions;

    public ImageHandler(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        if (path.equals("/image/upload") && method.equalsIgnoreCase("POST")) {
            handleUpload(ex);
        } else if (path.startsWith("/image/") && method.equalsIgnoreCase("GET")) {
            handleGet(ex, path);
        } else {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        }
    }

    private void handleUpload(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendText(ex, 401, "unauthorized");
            return;
        }

        // 区分头像和聊天图片，默认聊天图片
        String type = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "type");
        // 只识别 avatar 和 post；其他一律按 message（会被 24 小时清理）
        if (!"avatar".equals(type) && !"post".equals(type)) type = "message";

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_UPLOAD);
        String base64 = body;
        int comma = body.indexOf(',');
        if (body.startsWith("data:") && comma > 0) {
            base64 = body.substring(comma + 1);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            HttpUtil.sendText(ex, 400, "Bad base64");
            return;
        }

        // 空内容检查
        if (bytes.length == 0) {
            HttpUtil.sendText(ex, 400, "Empty image");
            return;
        }

        if (bytes.length > Config.MAX_IMAGE_SIZE) {
            HttpUtil.sendText(ex, 413, "Image too large");
            return;
        }

        // 魔数校验：只凭扩展名或客户端声明的类型会被伪造，
        // 真实类型必须从文件头判断，同时决定了后续返回的 Content-Type。
        String mime = HttpUtil.sniffImageMime(bytes);
        if (mime == null) {
            HttpUtil.sendText(ex, 415, "Unsupported image format");
            return;
        }

        // 每用户配额：防止单个账号无限上传把 SQLite 撑满
        if (!withinQuota(username, bytes.length)) {
            HttpUtil.sendText(ex, 429, "Quota exceeded");
            return;
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO images(id, data, type, created_at, username, mime) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setBytes(2, bytes);
            ps.setString(3, type);
            ps.setLong(4, now);
            ps.setString(5, username);
            ps.setString(6, mime);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ImageHandler] 上传失败：" + e.getMessage());
            HttpUtil.sendText(ex, 500, "DB error");
            return;
        }

        // 只清理聊天图片（type=message），头像永久保留
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM images WHERE type = 'message' AND created_at < ?")) {
            ps.setLong(1, now - 24L * 3600 * 1000);
            ps.executeUpdate();
        } catch (SQLException ignored) {}

        HttpUtil.sendText(ex, 200, id);
    }

    /**
     * 检查该用户已有图片的数量与总字节是否还有余量。
     * 查询失败时按"允许"处理，避免因为统计出错把正常用户挡在外面。
     */
    private boolean withinQuota(String username, int incomingBytes) {
        long count = 0;
        long totalBytes = 0;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) AS c, COALESCE(SUM(LENGTH(data)), 0) AS b " +
                     "FROM images WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getLong("c");
                    totalBytes = rs.getLong("b");
                }
            }
        } catch (SQLException e) {
            System.err.println("[ImageHandler] 配额查询失败：" + e.getMessage());
            return true;
        }
        if (count >= Config.MAX_IMAGE_COUNT_PER_USER) return false;
        return totalBytes + incomingBytes <= Config.MAX_IMAGE_BYTES_PER_USER;
    }

    private void handleGet(HttpExchange ex, String path) throws IOException {
        String id = path.substring("/image/".length());
        // id 必须是 32 位小写 hex，非法格式直接 404，不进数据库查询
        if (!id.matches("^[a-f0-9]{32}$")) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }

        byte[] bytes = null;
        String storedMime = null;
        String type = null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT data, mime, type FROM images WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bytes = rs.getBytes("data");
                    storedMime = rs.getString("mime");
                    type = rs.getString("type");
                }
            }
        } catch (SQLException e) {
            System.err.println("[ImageHandler] 查询失败：" + e.getMessage());
        }

        if (bytes == null) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }

        // 回退：老数据没有 mime 列，就现算；仍识别不出则按 JPEG 处理
        String mime = storedMime;
        if (mime == null || mime.isEmpty()) {
            mime = HttpUtil.sniffImageMime(bytes);
        }
        if (mime == null) mime = "image/jpeg";

        String query = ex.getRequestURI().getQuery();
        boolean download = query != null && query.contains("download=1");

        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; sandbox");
        if (download) {
            ex.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"image_" + id + HttpUtil.extensionForMime(mime) + "\"");
        } else {
            ex.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        }
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}