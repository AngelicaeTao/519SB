package HandlerLayer;

import ConfigurationLayer.Config;
import InfrastructureLayer.DatabaseManager;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

        // 区分用途，默认聊天图片。
        // 白名单见 Config.MEDIA_TYPES——注意不能只判断 avatar/post，
        // 之前 comment 不在白名单里，评论图会被当成聊天图而在 24 小时后被清掉。
        String type = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "type");
        if (!isKnownType(type)) type = "message";

        // 大文件走原始二进制（视频/文件），小图仍走 base64（前端本来就是 data URL）。
        // 用 byte[] 读取，避免二进制被当成字符串解码而损坏。
        byte[] raw = HttpUtil.readBodyBytes(ex, BootstrapLayer.ChatServer.MAX_BODY_UPLOAD);

        byte[] bytes;
        if (startsWithDataUrl(raw)) {
            int comma = indexOf(raw, (byte) ',');
            if (comma < 0) {
                HttpUtil.sendText(ex, 400, "Bad data URL");
                return;
            }
            byte[] b64 = new byte[raw.length - comma - 1];
            System.arraycopy(raw, comma + 1, b64, 0, b64.length);
            try {
                bytes = Base64.getDecoder().decode(new String(b64, StandardCharsets.US_ASCII));
            } catch (IllegalArgumentException e) {
                HttpUtil.sendText(ex, 400, "Bad base64");
                return;
            }
        } else {
            bytes = raw;
        }

        if (bytes.length == 0) {
            HttpUtil.sendText(ex, 400, "Empty body");
            return;
        }

        // 单文件上限按用途区分：图片 5MB，视频 50MB，文件 20MB
        long maxBytes = Config.mediaMaxBytes(type);
        if (bytes.length > maxBytes) {
            HttpUtil.sendText(ex, 413, "Too large (limit " + maxBytes + " bytes)");
            return;
        }

        // 魔数校验：只凭扩展名或客户端声明的类型会被伪造，
        // 真实类型必须从文件头判断，同时决定了后续返回的 Content-Type。
        String mime = HttpUtil.sniffMediaMime(type, bytes);
        if (mime == null) {
            HttpUtil.sendText(ex, 415, "Unsupported media format");
            return;
        }

        // 每用户配额：按用途分别计数，防止单个账号无限上传把 SQLite 撑满。
        // 必须带上 type——否则评论图、语音会挤占聊天图的额度。
        if (!withinQuota(username, type, bytes.length)) {
            HttpUtil.sendText(ex, 429, "Quota exceeded");
            return;
        }

        // 原始文件名。只有 file 类型真正需要（作下载名 + 在消息里显示），
        // 但它对任何类型都无害，存下来也便于排查。
        String fileName = HttpUtil.sanitizeFileName(
                HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "filename"));

        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO images(id, data, type, created_at, username, mime, filename) "
                             + "VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setBytes(2, bytes);
            ps.setString(3, type);
            ps.setLong(4, now);
            ps.setString(5, username);
            ps.setString(6, mime);
            if (fileName == null) ps.setNull(7, java.sql.Types.VARCHAR);
            else ps.setString(7, fileName);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ImageHandler] 上传失败：" + e.getMessage());
            HttpUtil.sendText(ex, 500, "DB error");
            return;
        }

        // 按用途清理过期图片。头像永久保留，但只留最近几张——
        // 否则用户反复换头像可以无限堆积（旧头像在聊天记录里可能还被引用，
        // 所以不能一换就删，留最近若干张做折中）。
        cleanup(type, username, now);

        HttpUtil.sendText(ex, 200, id);
    }

    /** 请求体是不是 data URL（形如 data:image/png;base64,....） */
    private boolean startsWithDataUrl(byte[] b) {
        if (b == null || b.length < 5) return false;
        return b[0] == 'd' && b[1] == 'a' && b[2] == 't' && b[3] == 'a' && b[4] == ':';
    }

    /** 在字节数组里找第一个匹配字节的位置 */
    private int indexOf(byte[] b, byte target) {
        for (int i = 0; i < b.length; i++) {
            if (b[i] == target) return i;
        }
        return -1;
    }

    /** type 是否在白名单里 */
    private boolean isKnownType(String type) {
        if (type == null) return false;
        for (String t : Config.MEDIA_TYPES) {
            if (t.equals(type)) return true;
        }
        return false;
    }

    /**
     * 清理该用途下过期的图片。
     *
     * - message/comment 等有保留期的：删掉早于保留期的记录
     * - avatar：保留最近 KEEP_AVATARS 张，更早的删掉
     * - 保留期为 0 的用途不清理
     */
    private void cleanup(String type, String username, long now) {
        long retention = Config.imageRetentionMs(type);

        if (retention > 0) {
            // 只清理全局过期的，不按用户过滤——聊天图是所有用户的公共资源
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM images WHERE type = ? AND created_at < ?")) {
                ps.setString(1, type);
                ps.setLong(2, now - retention);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[ImageHandler] 清理 " + type + " 失败：" + e.getMessage());
            }
            return;
        }

        if ("avatar".equals(type) && username != null) {
            // 保留最近 5 张：换头像后旧图不会立刻失效（聊天记录里可能还引用着），
            // 但也不会无限堆积
            final int keep = 5;
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM images WHERE type = 'avatar' AND username = ? AND id NOT IN (" +
                                 "SELECT id FROM images WHERE type = 'avatar' AND username = ? " +
                                 "ORDER BY created_at DESC LIMIT ?)")) {
                ps.setString(1, username);
                ps.setString(2, username);
                ps.setInt(3, keep);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[ImageHandler] 清理旧头像失败：" + e.getMessage());
            }
        }
    }

    /**
     * 检查该用户在该用途下还有没有余量。
     * 按 type 分别计数，查询失败时按"允许"处理，
     * 避免因为统计出错把正常用户挡在外面。
     */
    private boolean withinQuota(String username, String type, int incomingBytes) {
        long count = 0;
        long totalBytes = 0;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) AS c, COALESCE(SUM(LENGTH(data)), 0) AS b " +
                     "FROM images WHERE username = ? AND type = ?")) {
            ps.setString(1, username);
            ps.setString(2, type);
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
        if (count >= Config.imageCountLimit(type)) return false;
        return totalBytes + incomingBytes <= Config.imageBytesLimit(type);
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
        String fileName = null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT data, mime, type, filename FROM images WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bytes = rs.getBytes("data");
                    storedMime = rs.getString("mime");
                    type = rs.getString("type");
                    fileName = rs.getString("filename");
                }
            }
        } catch (SQLException e) {
            // 老库没有 filename 列时降级查询
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
            } catch (SQLException e2) {
                System.err.println("[ImageHandler] 查询失败：" + e2.getMessage());
            }
        }

        if (bytes == null) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }

        boolean isFile = "file".equals(type);

        // ===== 安全要点 =====
        // file 类型不做内容识别（任意文件没有固定魔数），所以绝不能内联渲染：
        // 一个上传的 .html / .svg 如果被当作 inline 返回，就是一个存储型 XSS。
        // 因此这里强制 Content-Type 为 octet-stream 并作为附件下载，
        // 无论数据库里存的 mime 是什么、也无论 URL 带没带 download=1。
        String mime;
        if (isFile) {
            mime = "application/octet-stream";
        } else {
            mime = storedMime;
            if (mime == null || mime.isEmpty()) {
                mime = HttpUtil.sniffMediaMime(type, bytes);
            }
            if (mime == null) {
                if ("voice".equals(type)) mime = "audio/webm";
                else mime = "image/jpeg";
            }
        }

        String query = ex.getRequestURI().getQuery();
        boolean download = isFile || (query != null && query.contains("download=1"));

        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; sandbox");
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");

        if (download) {
            // 下载名优先用上传时的原始文件名，没有则退回按 id 生成
            String name = (fileName != null && !fileName.isEmpty())
                    ? fileName
                    : ("media_" + id + HttpUtil.extensionForMime(storedMime == null ? mime : storedMime));
            ex.getResponseHeaders().set("Content-Disposition",
                    HttpUtil.contentDisposition(name, false));
        } else {
            ex.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        }

        // ===== Range 支持 =====
        // <audio> 靠它才能拖动进度条、边下边播；下载大文件时也支持续传。
        // 不做的话大文件必须整个下载完才能用，体验很差。
        long start = 0;
        long end = bytes.length - 1L;
        boolean partial = false;

        String range = ex.getRequestHeaders().getFirst("Range");
        if (range != null && range.startsWith("bytes=") && bytes.length > 0) {
            long[] r = parseRange(range, bytes.length);
            if (r != null) {
                start = r[0];
                end = r[1];
                partial = true;
            }
        }

        int len = (int) (end - start + 1);

        if (partial) {
            ex.getResponseHeaders().set("Content-Range",
                    "bytes " + start + "-" + end + "/" + bytes.length);
            ex.sendResponseHeaders(206, len);
        } else {
            ex.sendResponseHeaders(200, bytes.length);
        }

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes, (int) start, len);
        }
        // 流量统计：图片/语音/视频下载通常是大头，必须计入
        InfrastructureLayer.TrafficMonitor.accountOut(ex, len);
    }

    /**
     * 解析 Range 头，返回 [start, end]；不合法或无法满足时返回 null。
     *
     * 只处理最常见的 "bytes=start-end"、"bytes=start-"、"bytes=-suffix" 三种形式。
     */
    long[] parseRange(String range, int total) {
        try {
            String spec = range.substring("bytes=".length()).trim();
            int dash = spec.indexOf('-');
            if (dash < 0) return null;

            String left = spec.substring(0, dash).trim();
            String right = spec.substring(dash + 1).trim();

            long start, end;
            if (left.isEmpty()) {
                // bytes=-N：最后 N 字节
                if (right.isEmpty()) return null;
                long suffix = Long.parseLong(right);
                if (suffix <= 0) return null;
                start = Math.max(0, total - suffix);
                end = total - 1L;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? total - 1L : Long.parseLong(right);
            }

            if (start < 0 || start >= total) return null;
            if (end >= total) end = total - 1L;
            if (end < start) return null;
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}