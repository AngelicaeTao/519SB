package HandlerLayer;

import ConfigurationLayer.Config;
import DataLayer.MessageStore;
import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.RateLimiter;
import InfrastructureLayer.SessionManager;
import InfrastructureLayer.SseManager;
import ViewLayer.WebPages;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** 聊天相关：/chat、/send、/events、/recall、/online、/history */
public class ChatHandler {

    private static final long RECALL_WINDOW_MS = 2 * 60 * 1000;
    private static final int INITIAL_HISTORY = 30;
    private static final int PAGE_SIZE = 30;

    private final MessageStore store;
    private final SseManager sse;
    private final SessionManager sessions;
    private final UserStore userStore;
    private final RateLimiter sendLimiter = new RateLimiter();

    public ChatHandler(MessageStore store, SseManager sse,
                       SessionManager sessions, UserStore userStore) {
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

        String limitKey = "send:" + username;
        if (!sendLimiter.allow(limitKey, Config.SEND_MAX_COUNT, Config.SEND_WINDOW_MS)) {
            HttpUtil.sendJson(ex, 429, "{\"ok\":false,\"msg\":\"发送过于频繁，请稍后再试\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        String text = HttpUtil.extractJsonString(body, "text");
        String time = HttpUtil.extractJsonString(body, "time");
        String type = HttpUtil.extractJsonString(body, "type");
        if (text == null) text = "";
        if (time == null) time = "";
        if (type == null) type = "text";

        if (text.length() > 2000) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"消息过长（最多 2000 字）\"}");
            return;
        }

        long id = store.add(username, text, time, type);

        String msg = "{\"id\":" + id
                + ",\"username\":\"" + HttpUtil.escapeJson(user.username)
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
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendText(ex, 401, "unauthorized");
            return;
        }

        if (sse.countConnectionsFor(username) >= BootstrapLayer.ChatServer.SSE_MAX_PER_USER) {
            HttpUtil.sendText(ex, 429, "too many connections");
            return;
        }

        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);

        OutputStream os = ex.getResponseBody();
        sse.register(os, username);

        try {
            // 历史消息回放
            for (String msg : store.recent(INITIAL_HISTORY)) {
                sse.sendTo(os, msg);
            }
            os.write("data: {\"type\":\"history_end\"}\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();

            // 有新用户上线 → 广播最新在线列表给所有人
            broadcastOnlineList();

            // 心跳
            while (true) {
                Thread.sleep(15000);
                os.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (Exception e) {
        } finally {
            sse.unregister(os);
            // 有用户下线 → 广播最新在线列表给所有人
            broadcastOnlineList();
            try { os.close(); } catch (IOException ignored) {}
            ex.close();
        }
    }

    /** 广播在线用户列表给所有 SSE 客户端 */
    private void broadcastOnlineList() {
        Set<String> users = sse.listOnlineUsers();
        StringBuilder sb = new StringBuilder("{\"type\":\"online_list\",\"users\":[");
        boolean first = true;
        for (String u : users) {
            User user = userStore.getUser(u);
            if (user == null) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"username\":\"").append(HttpUtil.escapeJson(user.username)).append("\"")
                    .append(",\"nickname\":\"").append(HttpUtil.escapeJson(user.nickname)).append("\"")
                    .append(",\"avatarId\":").append(user.avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(user.avatarId) + "\"")
                    .append("}");
        }
        sb.append("]}");
        sse.broadcast(sb.toString());
    }

    /** POST /recall?token=xxx  body: {"id":123} */
    public void handleRecall(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"msg\":\"method not allowed\"}");
            return;
        }
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        Long id = HttpUtil.extractJsonLong(body, "id");
        if (id == null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 id\"}");
            return;
        }

        if (store.recall(id, username, RECALL_WINDOW_MS)) {
            sse.broadcast("{\"type\":\"recall\",\"id\":" + id + "}");
            HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
        } else {
            HttpUtil.sendJson(ex, 400,
                    "{\"ok\":false,\"msg\":\"撤回失败：超过 2 分钟或不是你的消息\"}");
        }
    }

    /** GET /online?token=xxx  前端初次加载时调一次 */
    public void handleOnline(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        if (sessions.getUser(token) == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        Set<String> users = sse.listOnlineUsers();
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"users\":[");
        boolean first = true;
        for (String u : users) {
            User user = userStore.getUser(u);
            if (user == null) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"username\":\"").append(HttpUtil.escapeJson(user.username)).append("\"")
                    .append(",\"nickname\":\"").append(HttpUtil.escapeJson(user.nickname)).append("\"")
                    .append(",\"avatarId\":").append(user.avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(user.avatarId) + "\"")
                    .append("}");
        }
        sb.append("]}");
        HttpUtil.sendJson(ex, 200, sb.toString());
    }

    /** GET /history?token=xxx&before=<id> */
    public void handleHistory(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        if (sessions.getUser(token) == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        String beforeStr = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "before");
        long beforeId;
        try { beforeId = Long.parseLong(beforeStr); }
        catch (Exception e) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少或非法 before\"}");
            return;
        }

        List<String> messages = store.loadBefore(beforeId, PAGE_SIZE);
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(messages.get(i));
        }
        sb.append("]}");
        HttpUtil.sendJson(ex, 200, sb.toString());
    }
}