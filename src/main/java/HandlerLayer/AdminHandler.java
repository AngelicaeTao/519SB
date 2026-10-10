package HandlerLayer;

import DataLayer.MessageStore;
import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import InfrastructureLayer.SseManager;
import ViewLayer.AdminPage;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;

/** 管理员接口：查看所有用户、修改用户、删除用户、查看用户消息 */
public class AdminHandler {

    private final UserStore userStore;
    private final MessageStore messageStore;
    private final SessionManager sessions;
    private final SseManager sse;

    public AdminHandler(UserStore userStore, MessageStore messageStore,
                        SessionManager sessions, SseManager sse) {
        this.userStore = userStore;
        this.messageStore = messageStore;
        this.sessions = sessions;
        this.sse = sse;
    }

    public void handlePage(HttpExchange ex) throws IOException {
        HttpUtil.sendHtml(ex, 200, AdminPage.HTML);
    }

    public void handleListUsers(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null || !isAdmin(username)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权访问\"}");
            return;
        }

        List<User> users = userStore.listAll();
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"username\":\"").append(HttpUtil.escapeJson(u.username)).append("\"")
                    .append(",\"nickname\":\"").append(HttpUtil.escapeJson(u.nickname)).append("\"")
                    .append(",\"avatarId\":").append(u.avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(u.avatarId) + "\"")
                    .append(",\"isAdmin\":").append(u.isAdmin)
                    .append(",\"createdAt\":").append(u.createdAt)
                    .append("}");
        }
        sb.append("]}");
        HttpUtil.sendJson(ex, 200, sb.toString());
    }

    public void handleUpdateUser(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String operator = sessions.getUser(token);
        if (operator == null || !isAdmin(operator)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权访问\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        String target = HttpUtil.extractJsonString(body, "username");
        String nickname = HttpUtil.extractJsonString(body, "nickname");
        String newPassword = HttpUtil.extractJsonString(body, "newPassword");
        String avatarId = HttpUtil.extractJsonString(body, "avatarId");
        if (body.contains("\"avatarId\":null")) avatarId = null;
        // 校验 avatarId 格式，防止 XSS
        if (avatarId != null && !avatarId.matches("^[a-f0-9]{32}$")) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"头像 ID 格式错误\"}");
            return;
        }

        boolean isAdmin = body.contains("\"isAdmin\":true");

        if (target == null || nickname == null || nickname.isBlank()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"参数错误\"}");
            return;
        }
        if (!userStore.exists(target)) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"用户不存在\"}");
            return;
        }

        if (userStore.adminUpdate(target, nickname, newPassword, avatarId, isAdmin)) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"修改成功\"}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"修改失败\"}");
        }
    }

    /** POST /api/admin/deleteUser?token=xxx  body: {"username":".."} */
    public void handleDeleteUser(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String operator = sessions.getUser(token);
        if (operator == null || !isAdmin(operator)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权访问\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        String target = HttpUtil.extractJsonString(body, "username");

        if (target == null || target.isBlank()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 username\"}");
            return;
        }
        if (target.equals(operator)) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"不能删除自己\"}");
            return;
        }
        if (!userStore.exists(target)) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"用户不存在\"}");
            return;
        }

        // 1. 先踢出在线用户（删会话 + 断 SSE）
        sessions.removeAllSessionsOfUser(target);
        sse.kickUser(target);

        // 2. 删除用户及其消息
        userStore.deleteUserMessages(target);
        if (userStore.delete(target)) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"用户已删除\"}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"删除失败\"}");
        }
    }

    public void handleUserMessages(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String operator = sessions.getUser(token);
        if (operator == null || !isAdmin(operator)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权访问\"}");
            return;
        }

        String target = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "username");
        if (target == null || target.isBlank()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 username\"}");
            return;
        }

        List<String> messages = messageStore.messagesOfUser(target);
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(messages.get(i));
        }
        sb.append("]}");
        HttpUtil.sendJson(ex, 200, sb.toString());
    }

    /** POST /api/admin/deleteMessage?token=xxx  body: {"id": 123} */
    public void handleDeleteMessage(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String operator = sessions.getUser(token);
        if (operator == null || !isAdmin(operator)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权访问\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        Long id = extractLong(body, "id");
        if (id == null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 id\"}");
            return;
        }

        if (messageStore.deleteMessage(id)) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"已删除\"}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"删除失败\"}");
        }
    }

    /** POST /api/admin/deleteMessages?token=xxx  body: {"ids":[1,2,3]} */
    public void handleDeleteMessages(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String operator = sessions.getUser(token);
        if (operator == null || !isAdmin(operator)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权访问\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        List<Long> ids = extractLongArray(body, "ids");
        if (ids.isEmpty()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"没有选中任何消息\"}");
            return;
        }

        int n = messageStore.deleteMessages(ids);
        HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"已删除 " + n + " 条消息\"}");
    }

    /** 从 JSON 里提取数字字段 */
    private Long extractLong(String json, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Long.parseLong(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    /** 从 JSON 里提取数字数组 */
    private List<Long> extractLongArray(String json, String key) {
        List<Long> result = new java.util.ArrayList<>();
        if (json == null) return result;
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return result;
        int lb = json.indexOf('[', keyIdx);
        int rb = json.indexOf(']', lb);
        if (lb < 0 || rb < 0) return result;
        String inner = json.substring(lb + 1, rb);
        for (String s : inner.split(",")) {
            try { result.add(Long.parseLong(s.trim())); } catch (Exception ignored) {}
        }
        return result;
    }

    private boolean isAdmin(String username) {
        User u = userStore.getUser(username);
        return u != null && u.isAdmin;
    }

    /** POST /api/admin/clearMessages?token=xxx  清空所有消息（保留用户和头像） */
    public void handleClearMessages(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String operator = sessions.getUser(token);
        if (operator == null || !isAdmin(operator)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权访问\"}");
            return;
        }
        try (java.sql.Connection conn = InfrastructureLayer.DatabaseManager.getConnection();
             java.sql.Statement st = conn.createStatement()) {
            int msgCount = st.executeUpdate("DELETE FROM messages");
            int imgCount = st.executeUpdate("DELETE FROM images WHERE type = 'message'");
            HttpUtil.sendJson(ex, 200,
                    "{\"ok\":true,\"msg\":\"已清空 " + msgCount + " 条消息、" + imgCount + " 张聊天图片\"}");
        } catch (java.sql.SQLException e) {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"清空失败：" + HttpUtil.escapeJson(e.getMessage()) + "\"}");
        }
    }
}