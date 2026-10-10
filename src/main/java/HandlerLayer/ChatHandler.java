package HandlerLayer;

import DataLayer.MessageStore;
import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import InfrastructureLayer.SseManager;
import ViewLayer.WebPages;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** 处理聊天相关的三个路径：/chat（页面）、/send（发消息）、/events（SSE 订阅） */
public class ChatHandler {

    private final MessageStore store;
    private final SseManager sse;
    private final SessionManager sessions;
    private final UserStore userStore;

    public ChatHandler(MessageStore store, SseManager sse, SessionManager sessions, UserStore userStore) {
        this.store = store;
        this.sse = sse;
        this.sessions = sessions;
        this.userStore = userStore;
    }

    public void handleIndex(HttpExchange ex) throws IOException {
        byte[] bytes = WebPages.INDEX_HTML.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void handleSend(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }

        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录或登录已过期\"}");
            return;
        }

        User user = userStore.getUser(username);
        if (user == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"用户不存在\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        String text = HttpUtil.extractJsonString(body, "text");
        String time = HttpUtil.extractJsonString(body, "time");
        String type = HttpUtil.extractJsonString(body, "type");
        if (text == null) text = "";
        if (time == null) time = "";
        if (type == null) type = "text";

        // 存数据库
        store.add(username, text, time, type);

        // 广播（带最新昵称和头像）
        String msg = "{\"username\":\"" + HttpUtil.escapeJson(user.username)
                + "\",\"nickname\":\"" + HttpUtil.escapeJson(user.nickname)
                + "\",\"avatarId\":" + (user.avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(user.avatarId) + "\"")
                + ",\"text\":\"" + HttpUtil.escapeJson(text)
                + "\",\"time\":\"" + HttpUtil.escapeJson(time)
                + "\",\"type\":\"" + HttpUtil.escapeJson(type) + "\"}";

        sse.broadcast(msg);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    public void handleEvents(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        if (sessions.getUser(token) == null) {
            HttpUtil.sendText(ex, 401, "unauthorized");
            return;
        }

        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);

        OutputStream os = ex.getResponseBody();
        String username = sessions.getUser(token);
        sse.register(os, username);

        try {
            for (String msg : store.snapshot()) {
                sse.sendTo(os, msg);
            }
            os.flush();

            while (true) {
                Thread.sleep(15000);
                os.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (Exception e) {
        } finally {
            sse.unregister(os);
            try { os.close(); } catch (IOException ignored) {}
            ex.close();
        }
    }
}