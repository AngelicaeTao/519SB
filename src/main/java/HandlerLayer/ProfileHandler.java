package HandlerLayer;

import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import ViewLayer.ProfilePage;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/** 个人中心：查看/修改自己的昵称、头像、密码 */
public class ProfileHandler {

    private final UserStore userStore;
    private final SessionManager sessions;

    public ProfileHandler(UserStore userStore, SessionManager sessions) {
        this.userStore = userStore;
        this.sessions = sessions;
    }

    /** GET /profile  个人中心页面 */
    public void handlePage(HttpExchange ex) throws IOException {
        HttpUtil.sendHtml(ex, 200, ProfilePage.HTML);
    }

    /** POST /api/profile/update?token=xxx  body: {"nickname":"..","avatarId":".."} */
    public void handleUpdate(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        String nickname = HttpUtil.extractJsonString(body, "nickname");
        String avatarId = HttpUtil.extractJsonString(body, "avatarId");
        // avatarId 特殊处理：可能是 JSON null，也可能是空字符串
        if (body.contains("\"avatarId\":null")) avatarId = null;

        if (nickname == null || nickname.isBlank()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"昵称不能为空\"}");
            return;
        }
        if (nickname.length() > 20) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"昵称过长\"}");
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

    /** POST /api/profile/changePassword?token=xxx  body: {"newPassword":".."} */
    public void handleChangePassword(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"未登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
        String newPassword = HttpUtil.extractJsonString(body, "newPassword");

        if (newPassword == null || newPassword.isEmpty()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"新密码不能为空\"}");
            return;
        }
        if (newPassword.length() > 50) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"密码过长\"}");
            return;
        }

        if (userStore.changePassword(username, newPassword)) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"密码修改成功\"}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"修改失败\"}");
        }
    }
}