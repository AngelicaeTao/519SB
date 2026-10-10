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
        if (sessions.getUser(token) == null) {
            HttpUtil.sendText(ex, 401, "unauthorized");
            return;
        }

        String body = HttpUtil.readBody(ex);

        // 去掉 data:image/xxx;base64, 前缀
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

        if (bytes.length > Config.MAX_IMAGE_SIZE) {
            HttpUtil.sendText(ex, 413, "Image too large");
            return;
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO images(id, data, created_at) VALUES(?,?,?)")) {
            ps.setString(1, id);
            ps.setBytes(2, bytes);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ImageHandler] 上传失败：" + e.getMessage());
            HttpUtil.sendText(ex, 500, "DB error");
            return;
        }

        // 清理 24 小时前的旧图片
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM images WHERE created_at < ?")) {
            ps.setLong(1, now - 24L * 3600 * 1000);
            ps.executeUpdate();
        } catch (SQLException ignored) {}

        HttpUtil.sendText(ex, 200, id);
    }

    private void handleGet(HttpExchange ex, String path) throws IOException {
        String id = path.substring("/image/".length());

        byte[] bytes = null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT data FROM images WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bytes = rs.getBytes("data");
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

        // 检查是否需要强制下载（前端下载按钮会带 ?download=1）
        String query = ex.getRequestURI().getQuery();
        boolean download = query != null && query.contains("download=1");

        ex.getResponseHeaders().set("Content-Type", "image/jpeg");
        if (download) {
            ex.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"image_" + id + ".jpg\"");
        } else {
            ex.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        }
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}