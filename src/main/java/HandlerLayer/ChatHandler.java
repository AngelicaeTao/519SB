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

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
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
        long now = System.currentTimeMillis();
        String msg = "{\"id\":" + id
                + ",\"createdAt\":" + now
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
            ex.close();
            return;
        }

        // 注意：这里不再做"每用户连接数上限"判断。
        // 切换页面时旧 TCP 连接会残留几秒到几十秒，服务端未及时察觉，
        // 那时用旧上限会把新连接 429 掉，导致历史不加载、在线列表不刷新。
        // 防滥用交给每 IP 和全局上限就足够了。

        String ip = BootstrapLayer.ChatServer.getClientIp(ex);
        if (!sse.tryRegister(ip,
                BootstrapLayer.ChatServer.SSE_MAX_PER_IP,
                BootstrapLayer.ChatServer.SSE_MAX_GLOBAL)) {
            HttpUtil.sendText(ex, 429, "too many connections");
            ex.close();
            return;
        }

        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");

        OutputStream os;
        try {
            ex.sendResponseHeaders(200, 0);
            os = ex.getResponseBody();
            sse.bind(os, username, ip);
        } catch (IOException e) {
            sse.releaseReservation(ip);
            throw e;
        }

        try {
            // 回放历史。即使某条写入失败也不要让整个连接倒掉。
            try {
                for (String msg : store.recent(INITIAL_HISTORY)) {
                    sse.sendTo(os, msg);
                }
                os.write("data: {\"type\":\"history_end\"}\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException ignored) {
            }

            // 无论历史是否成功，都要广播一次在线列表
            broadcastOnlineList();

            long idleTimeout = ConfigurationLayer.Config.SSE_IDLE_TIMEOUT_MS;

            while (true) {
                Thread.sleep(15000);
                if (sse.idleMillis(os) > idleTimeout) {
                    break;
                }
                os.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (Exception e) {
        } finally {
            sse.unregister(os);
            broadcastOnlineList();
            try { os.close(); } catch (IOException ignored) {}
            ex.close();
        }
    }

    /**
     * 广播在线用户列表。
     * 用 sessions.listOnlineUsers()（60 秒活跃窗口）而不是 sse.listOnlineUsers()，
     * 后者只看当前有 SSE 长连接的用户，切到帖子页 SSE 断开就会"掉线"。
     */
    private void broadcastOnlineList() {
        Set<String> users = sessions.listOnlineUsers();
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
        if (HttpUtil.rejectWrongMethod(ex, "POST")) return;
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
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

    /** GET /online?token=xxx */
    public void handleOnline(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "GET")) return;
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        if (sessions.getUser(token) == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        Set<String> users = sessions.listOnlineUsers();
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

    /**
     * GET /users?token=xxx
     *
     * 返回用户目录（账号、昵称、头像），供聊天页的「搜索用户」使用。
     * 之前前端只能从「已加载的消息」里收集用户，刷新后只有 30 条历史，
     * 没在这个范围里发过言的人就永远搜不到。
     *
     * 只返回已登录用户可见的三项字段，不含管理员标记、注册时间等。
     */
    public void handleUsers(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "GET")) return;
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        if (sessions.getUser(token) == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        List<User> all = userStore.listAll();
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"users\":[");
        boolean first = true;
        for (User u : all) {
            if (u == null || u.username == null) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"username\":\"").append(HttpUtil.escapeJson(u.username)).append("\"")
                    .append(",\"nickname\":\"").append(HttpUtil.escapeJson(u.nickname)).append("\"")
                    .append(",\"avatarId\":")
                    .append(u.avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(u.avatarId) + "\"")
                    .append("}");
        }
        sb.append("]}");
        HttpUtil.sendJson(ex, 200, sb.toString());
    }

    /** GET /history?token=xxx&before=<id> */
    public void handleHistory(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "GET")) return;
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