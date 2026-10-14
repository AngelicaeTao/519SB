package InfrastructureLayer;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTP 工具：query 解析、响应输出、JSON 简单处理 */
public class HttpUtil {

    /** 从 query 字符串中取参数，例如 token=abc&x=1 */
    public static String getQueryParam(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                String k = part.substring(0, eq);
                if (k.equals(key)) {
                    try {
                        return URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                    } catch (Exception e) {
                        return part.substring(eq + 1);
                    }
                }
            }
        }
        return null;
    }

    /** 请求体超过允许上限时抛出，由最外层统一转成 413 */
    public static class BodyTooLargeException extends IOException {
        public BodyTooLargeException(String msg) { super(msg); }
    }

    /**
     * 读取请求体并强制限长。
     *
     * 安全说明：绝对不能依赖 Content-Length 头来限长——攻击者可以不发送该头，
     * 改用 Transfer-Encoding: chunked 传输任意大小的请求体。这里在读取过程中
     * 按实际字节数计数，超出限制立即中止。
     */
    public static String readBody(HttpExchange ex, long maxBytes) throws IOException {
        byte[] bytes = readBodyBytes(ex, maxBytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 读取请求体原始字节。
     *
     * 视频和文件走原始二进制上传：base64 会把体积撑大 1/3，还要额外解码，
     * 对大文件既费带宽又费内存。图片/语音仍走 base64（前端就是这个格式）。
     */
    public static byte[] readBodyBytes(HttpExchange ex, long maxBytes) throws IOException {
        byte[] bytes = readLimited(ex.getRequestBody(), maxBytes);
        // 流量统计：按实际读到的字节数计（chunked 也算得准）
        TrafficMonitor.accountIn(ex, bytes.length);
        return bytes;
    }

    /** 从流中读取至多 maxBytes 字节；超出则抛 BodyTooLargeException */
    private static byte[] readLimited(InputStream in, long maxBytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new BodyTooLargeException("请求体超过上限 " + maxBytes + " 字节");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * 校验请求方法，不匹配时返回 405 并返回 true（调用方应立即 return）。
     *
     * 安全说明：所有会产生副作用或读取敏感数据的接口都必须显式限定方法。
     * 否则 GET 也能触发删除/清库这类操作，等于把 CSRF 面放大到「一个链接/一张图」即可触发。
     */
    public static boolean rejectWrongMethod(HttpExchange ex, String expected) throws IOException {
        if (expected.equalsIgnoreCase(ex.getRequestMethod())) return false;
        ex.getResponseHeaders().set("Allow", expected);
        sendJson(ex, 405, "{\"ok\":false,\"msg\":\"method not allowed\"}");
        return true;
    }

    /**
     * 写操作的跨站防护：校验 Origin 与 Host 是否同源，不同源返回 403 并返回 true。
     *
     * 安全说明：方法校验只能把攻击面从「GET 可触发」收窄到「必须发 POST」，
     * 跨站发 POST 本身并不困难（表单提交、no-cors 的 fetch）。
     * 这里再比对 Origin 与 Host，挡住来自其它站点的写请求。
     *
     * 注意：
     * - 浏览器发起的跨站请求一定会带 Origin 且与 Host 不一致，会被拦下。
     * - 同源请求的 Origin 与 Host 一致，正常放行。
     * - 没有 Origin 头的请求（curl、脚本类客户端）放行：这类客户端不受跨站模型约束，
     *   且凭证只在 query/localStorage 里，其它站点读不到，所以不构成 CSRF。
     */
    public static boolean rejectCrossSitePost(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)
                && !"DELETE".equalsIgnoreCase(method) && !"PATCH".equalsIgnoreCase(method)) {
            return false;
        }

        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null || host.isEmpty()) return false;

        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isEmpty()) return false;
        // 沙箱 iframe / 部分重定向场景下浏览器会发送字面量 "null"，视为跨站
        if (origin.length() > 512) {
            sendJson(ex, 403, "{\"ok\":false,\"msg\":\"跨站请求被拒绝\"}");
            return true;
        }
        if ("null".equalsIgnoreCase(origin.trim())) {
            sendJson(ex, 403, "{\"ok\":false,\"msg\":\"跨站请求被拒绝\"}");
            return true;
        }

        String originHost = hostOf(origin);
        if (originHost == null) {
            sendJson(ex, 403, "{\"ok\":false,\"msg\":\"跨站请求被拒绝\"}");
            return true;
        }

        if (!host.equalsIgnoreCase(stripBrackets(originHost))) {
            sendJson(ex, 403, "{\"ok\":false,\"msg\":\"跨站请求被拒绝\"}");
            return true;
        }
        return false;
    }

    /** 从 Origin 这类绝对 URL 里取 host[:port]；解析不出来返回 null */
    private static String hostOf(String origin) {
        try {
            java.net.URI uri = java.net.URI.create(origin.trim());
            String h = uri.getHost();
            if (h == null || h.isEmpty()) return null;
            int port = uri.getPort();
            boolean defaultPort = (port == -1)
                    || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                    || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
            return defaultPort ? h : (h + ":" + port);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    /**
     * 统一附加安全响应头。
     *
     * 说明：
     * - nosniff：阻止浏览器对响应做类型嗅探，图片上传场景尤其重要。
     * - CSP：因为前期无法逐处改造，脚本/样式仍允许内联；重点是 default-src 'self'
     *   挡住外部资源注入，并禁止 object/embed。frame-ancestors 'none' 防点击劫持。
     * - Referrer-Policy：token 走 query 传参，必须保证不随 Referer 外泄。
     * - HSTS 只在 HTTPS 语义成立时才有意义，这里服务本身只有 HTTPS，可以直接发。
     */
    /**
     * 写出响应体并计入流量。
     * 所有 sendXxx 都走这里，避免漏统计某一条响应路径。
     */
    private static void writeOut(HttpExchange ex, OutputStream os, byte[] bytes) throws IOException {
        os.write(bytes);
        TrafficMonitor.accountOut(ex, bytes.length);
    }

    private static void applySecurityHeaders(HttpExchange ex) {        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "same-origin");
        ex.getResponseHeaders().set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        ex.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; "
                        + "img-src 'self' data: blob:; "
                        + "media-src 'self'; "
                        + "script-src 'self' 'unsafe-inline'; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "connect-src 'self'; "
                        + "object-src 'none'; "
                        + "base-uri 'self'; "
                        + "form-action 'self'; "
                        + "frame-ancestors 'none'");
    }

    /** 返回 HTML 响应 */
    public static void sendHtml(HttpExchange ex, int status, String html) throws IOException {
        applySecurityHeaders(ex);
        // 页面会按用户配色/资料变化，禁止缓存。
        // 否则浏览器会按启发式规则缓存旧副本，出现"设置改了但页面没变"。
        ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            writeOut(ex, os, bytes);
        }
    }

    /**
     * 按用户配色返回 HTML。
     *
     * theme 为默认主题时不做任何改动（模板自带的 :root 就是默认色），
     * 只有选了非默认配色才在 </head> 前插入一块覆盖样式。
     */
    public static void sendHtmlThemed(HttpExchange ex, int status, String html, String theme)
            throws IOException {
        sendHtml(ex, status, applyTheme(html, theme));
    }

    /** 把主题覆盖块插到 </head> 前；无需覆盖时原样返回 */
    public static String applyTheme(String html, String theme) {
        if (html == null) return null;
        String block = Theme.overrideStyle(theme);
        if (block == null) return html;
        int i = html.indexOf("</head>");
        if (i < 0) return block + html;
        return html.substring(0, i) + block + html.substring(i);
    }

    /**
     * 返回静态 JS 资源。
     * 可以缓存——它不带用户数据，是靠浏览器端的 localStorage 决定行为的。
     */
    public static void sendJs(HttpExchange ex, int status, String js) throws IOException {
        applySecurityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "application/javascript; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        byte[] bytes = js.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            writeOut(ex, os, bytes);
        }
    }

    /** 返回 JSON 响应（带敏感数据，禁止缓存） */
    public static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        applySecurityHeaders(ex);
        ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            writeOut(ex, os, bytes);
        }
    }

    /** 返回纯文本响应 */
    public static void sendText(HttpExchange ex, int status, String text) throws IOException {
        applySecurityHeaders(ex);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            writeOut(ex, os, bytes);
        }
    }

    /** 发送一个带 Set-Cookie 的 JSON 响应 */
    public static void sendJsonWithCookie(HttpExchange ex, int status, String json, String cookie) throws IOException {
        applySecurityHeaders(ex);
        ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (cookie != null) ex.getResponseHeaders().add("Set-Cookie", cookie);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            writeOut(ex, os, bytes);
        }
    }

    /**
     * 按文件头识别图片真实类型，返回 MIME；不是受支持的图片则返回 null。
     * 只靠扩展名或客户端声明的 Content-Type 判定会被伪造，必须看魔数。
     */
    public static String sniffImageMime(byte[] b) {
        if (b == null || b.length < 4) return null;
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E
                && b[3] == 0x47 && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        // GIF: "GIF87a" / "GIF89a"
        if (b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x38
                && b.length >= 6 && (b[4] == 0x37 || b[4] == 0x39) && b[5] == 0x61) {
            return "image/gif";
        }
        // WebP: "RIFF" .... "WEBP"
        if (b.length >= 12 && b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
                && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50) {
            return "image/webp";
        }
        return null;
    }

    /**
     * 按文件头识别音频真实类型，返回 MIME；不是受支持的音频则返回 null。
     *
     * 和图片一样只认魔数：浏览器录出来的通常是 WebM/Opus（Chrome/Edge）
     * 或 Ogg/Opus（Firefox），Safari 是 MP4/AAC。
     */
    public static String sniffAudioMime(byte[] b) {
        if (b == null || b.length < 4) return null;

        // WebM / Matroska: 1A 45 DF A3
        if ((b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) {
            return "audio/webm";
        }
        // Ogg: "OggS"
        if (b[0] == 0x4F && b[1] == 0x67 && b[2] == 0x67 && b[3] == 0x53) {
            return "audio/ogg";
        }
        // WAV: "RIFF" .... "WAVE"
        if (b.length >= 12 && b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
                && b[8] == 0x57 && b[9] == 0x41 && b[10] == 0x56 && b[11] == 0x45) {
            return "audio/wav";
        }
        // MP4 / M4A: 偏移 4 处是 "ftyp"
        if (b.length >= 12 && b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70) {
            return "audio/mp4";
        }
        // MP3: 带 ID3 标签，或帧同步 FF Ex/Fx
        if (b.length >= 3 && b[0] == 0x49 && b[1] == 0x44 && b[2] == 0x33) {
            return "audio/mpeg";
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0) {
            return "audio/mpeg";
        }
        return null;
    }

    /**
     * 按用途选择合适的魔数识别器。
     * - voice：只接受音频
     * - anim：只接受可能带多帧的图片格式（GIF / WebP）
     * - file：不做内容识别，一律按二进制流处理。
     *         安全性靠"读取时强制作为附件下载"来保证，而不是靠识别内容——
     *         任意文件本来就没有可识别的固定魔数。
     * - 其余：图片
     */
    public static String sniffMediaMime(String type, byte[] bytes) {
        if ("voice".equals(type)) return sniffAudioMime(bytes);
        if ("file".equals(type)) return "application/octet-stream";

        String mime = sniffImageMime(bytes);
        if (mime == null) return null;
        if ("anim".equals(type)) {
            boolean multiFrameCapable = "image/gif".equals(mime) || "image/webp".equals(mime);
            return multiFrameCapable ? mime : null;
        }
        return mime;
    }

    /**
     * 净化上传的文件名。
     *
     * 目的是让它可以安全地进数据库、进 Content-Disposition 头、进页面文本。
     * 具体做法：去掉路径分隔符（防目录穿越式命名）、控制字符与引号，
     * 限制长度，并保证不会只剩一堆点。
     *
     * @return 净化后的名字；无法得到有意义的名字时返回 null
     */
    public static String sanitizeFileName(String name) {
        if (name == null) return null;
        String s = name.trim();
        if (s.isEmpty()) return null;

        // 只取最后一段，去掉任何路径成分（含 Windows 的反斜杠）
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // 控制字符、引号、冒号、通配符等都拿掉
            if (c < 0x20 || c == 0x7F) continue;
            if (c == '"' || c == '\'' || c == '\\' || c == '/' || c == ':' || c == '*' || c == '?'
                    || c == '<' || c == '>' || c == '|' || c == '\r' || c == '\n') continue;
            sb.append(c);
            if (sb.length() >= 120) break;   // 限制长度
        }
        String out = sb.toString().trim();
        // 全是点或者空的，没有意义
        if (out.isEmpty() || out.replace(".", "").isEmpty()) return null;
        return out;
    }

    /**
     * 生成 Content-Disposition 头。
     *
     * 同时给出 ASCII 版的 filename 和 RFC 5987 的 filename*，
     * 让中文文件名在老浏览器和现代浏览器下都能正确落地。
     */
    public static String contentDisposition(String fileName, boolean inline) {
        String type = inline ? "inline" : "attachment";
        String safe = sanitizeFileName(fileName);
        if (safe == null) safe = "download";

        // ASCII 回退版：非 ASCII 字符替换成下划线
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            ascii.append(c < 0x20 || c > 0x7E ? '_' : c);
        }
        return type + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + urlEncode(safe);
    }

    /** 百分号编码（用于 header 里的 filename*，以及 URL 片段） */
    public static String urlEncode(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int c = b & 0xFF;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (unreserved) {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }

    /** MIME -> 下载文件名后缀 */
    public static String extensionForMime(String mime) {
        if (mime == null) return ".bin";
        if ("image/png".equals(mime)) return ".png";
        if ("image/gif".equals(mime)) return ".gif";
        if ("image/webp".equals(mime)) return ".webp";
        if (mime.startsWith("image/")) return ".jpg";
        if ("audio/webm".equals(mime)) return ".webm";
        if ("audio/ogg".equals(mime)) return ".ogg";
        if ("audio/wav".equals(mime)) return ".wav";
        if ("audio/mp4".equals(mime)) return ".m4a";
        if ("audio/mpeg".equals(mime)) return ".mp3";
        return ".bin";
    }

    /** 从 JSON 字符串里提取某个 key 对应的字符串值 */
    public static String extractJsonString(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        String v = m.group(1);
        return v.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    /**
     * 从 JSON 里提取某个 key 对应的字符串数组，例如 "images":["ab..","cd.."]。
     *
     * 只认形如 ["a","b"] 的简单数组；元素不是字符串、或不是数组时返回空列表。
     * 这是给配图 id 这种用途写的——值经过调用方校验，不承担通用 JSON 解析职责。
     */
    public static List<String> extractJsonStringArray(String json, String key) {
        List<String> out = new ArrayList<>();
        if (json == null || key == null) return out;

        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[");
        Matcher m = p.matcher(json);
        if (!m.find()) return out;

        int i = m.end();
        int depth = 1;
        StringBuilder buf = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;

        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; if (inString) buf.append(c); continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') {
                inString = !inString;
                if (!inString) {
                    // 一个字符串元素结束
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                continue;
            }
            if (inString) { buf.append(c); continue; }
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) break;
            }
        }
        return out;
    }

    /** 把一批 id 拼成 JSON 数组字符串，用于入库 */
    public static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(items.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    /** 转义为 JSON 字符串值（严格模式：所有控制字符都转义） */
    public static String escapeJson(String s) {        if (s == null) return "";
        char bs = (char) 92;   // 反斜杠
        char qt = (char) 34;   // 双引号
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == bs) {
                sb.append(bs).append(bs);
            } else if (c == qt) {
                sb.append(bs).append('"');
            } else if (c == 10) {
                sb.append(bs).append('n');
            } else if (c == 13) {
                sb.append(bs).append('r');
            } else if (c == 9) {
                sb.append(bs).append('t');
            } else if (c == 8) {
                sb.append(bs).append('b');
            } else if (c == 12) {
                sb.append(bs).append('f');
            } else if (c < 32 || (c >= 127 && c <= 159) || c == 0x2028 || c == 0x2029) {
                sb.append(bs).append('u');
                String hex = Integer.toHexString(c);
                for (int j = hex.length(); j < 4; j++) sb.append('0');
                sb.append(hex);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 从请求头 Cookie 中取指定名字的值 */
    public static String getCookie(HttpExchange ex, String name) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String s = part.trim();
            int eq = s.indexOf('=');
            if (eq > 0 && s.substring(0, eq).equals(name)) {
                return s.substring(eq + 1);
            }
        }
        return null;
    }

    /** 从 JSON 里提取数字字段（如 "id":123） */
    public static Long extractJsonLong(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Long.parseLong(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 转义为 HTML 文本内容。
     *
     * 用于把用户输入填进 HTML 模板的占位符。这与 {@link #escapeJson} 完全不同：
     * escapeJson 只处理 JSON 语义，昵称里的 &lt;script&gt; 经它处理仍是原样，
     * 直接放进页面就会执行。凡是把不可信文本插进 HTML 的地方都必须走这个。
     */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}