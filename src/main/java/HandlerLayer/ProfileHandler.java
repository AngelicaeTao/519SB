package HandlerLayer;

import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import ViewLayer.ProfilePage;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.regex.Pattern;

/** 个人中心：查看/修改自己的昵称、头像、密码 */
public class ProfileHandler {

    private static final Pattern AVATAR_ID_PATTERN = Pattern.compile("^[a-f0-9]{32}$");

    private final UserStore userStore;
    private final SessionManager sessions;

    public ProfileHandler(UserStore userStore, SessionManager sessions) {
        this.userStore = userStore;
        this.sessions = sessions;
    }

    public void handlePage(HttpExchange ex) throws IOException {
        HttpUtil.sendHtml(ex, 200, ProfilePage.HTML);
    }

    public void handleUpdate(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "POST")) return;
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        String nickname = HttpUtil.extractJsonString(body, "nickname");
        String avatarId = HttpUtil.extractJsonString(body, "avatarId");
        if (body.contains("\"avatarId\":null")) avatarId = null;
        if (avatarId != null && avatarId.isEmpty()) avatarId = null;

        if (nickname == null || nickname.isBlank()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"昵称不能为空\"}");
            return;
        }
        if (nickname.length() > 20) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"昵称过长\"}");
            return;
        }
        if (avatarId != null && !AVATAR_ID_PATTERN.matcher(avatarId).matches()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"头像 ID 格式错误\"}");
            return;
        }

        if (userStore.updateProfile(username, nickname, avatarId)) {
            User u = userStore.getUser(username);
            String resp = "{\"ok\":true,\"nickname\":\"" + HttpUtil.escapeJson(u.nickname)
                    + "\",\"avatarId\":" + (u.avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(u.avatarId) + "\"")
                    + "}";
            HttpUtil.sendJson(ex, 200, resp);
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"更新失败\"}");
        }
    }

    /** 修改密码：需要登录 + 旧密码校验 */
    public void handleChangePassword(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "POST")) return;
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        String oldPassword = HttpUtil.extractJsonString(body, "oldPassword");
        String newPassword = HttpUtil.extractJsonString(body, "newPassword");

        if (oldPassword == null || oldPassword.isEmpty()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"请输入旧密码\"}");
            return;
        }
        if (newPassword == null || newPassword.isEmpty()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"新密码不能为空\"}");
            return;
        }

        String pwdErr = checkPasswordStrength(newPassword);
        if (pwdErr != null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"" + pwdErr + "\"}");
            return;
        }

        if (!userStore.verify(username, oldPassword)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"旧密码错误\"}");
            return;
        }

        if (userStore.changePassword(username, newPassword)) {
            // 密码已变更，所有旧会话一律作废——改密码的目的通常就是"怀疑账号被别人登录着"，
            // 如果旧 token 还能用，用户会误以为已经安全了。
            // 当前这个客户端重新签发一个 token，避免改完密码把自己也踢出去。
            sessions.removeAllSessionsOfUser(username);
            String newToken = sessions.createSession(username);
            if (newToken == null) {
                HttpUtil.sendJson(ex, 200,
                        "{\"ok\":true,\"msg\":\"密码修改成功，请重新登录\",\"token\":null}");
                return;
            }
            HttpUtil.sendJson(ex, 200,
                    "{\"ok\":true,\"msg\":\"密码修改成功，其它设备已全部下线\","
                            + "\"token\":\"" + HttpUtil.escapeJson(newToken) + "\"}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"修改失败\"}");
        }
    }

    /** 密码强度校验 */
    private String checkPasswordStrength(String pwd) {
        if (pwd.length() < 6) return "密码至少 6 位";
        if (pwd.length() > 50) return "密码最多 50 位";
        boolean hasLetter = false, hasDigit = false;
        for (char c : pwd.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) return "密码必须同时包含字母和数字";
        return null;
    }
}