package InfrastructureLayer;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

    /** 读取请求体 */
    public static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** 返回 HTML 响应 */
    public static void sendHtml(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 返回 JSON 响应 */
    public static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 返回纯文本响应 */
    public static void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 发送一个带 Set-Cookie 的 JSON 响应 */
    public static void sendJsonWithCookie(HttpExchange ex, int status, String json, String cookie) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (cookie != null) ex.getResponseHeaders().add("Set-Cookie", cookie);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 发送一个带 Set-Cookie 的 HTML 响应 */
    public static void sendHtmlWithCookie(HttpExchange ex, int status, String html, String cookie) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        if (cookie != null) ex.getResponseHeaders().add("Set-Cookie", cookie);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
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

    /** 转义为 JSON 字符串值（严格模式：所有控制字符都转义） */
    public static String escapeJson(String s) {
        if (s == null) return "";
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

    /** 拼接 JSON 字符串字段，自动处理闭合引号 */
    public static String jsonStr(String key, String value) {
        return "\"" + key + "\":\"" + escapeJson(value) + "\"";
    }
}