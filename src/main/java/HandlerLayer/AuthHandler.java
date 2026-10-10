package HandlerLayer;

import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import ViewLayer.GatePage;
import ViewLayer.LoginPage;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/** 门禁 + 登录、注册、修改密码、登出 */
public class AuthHandler {

    private static final String ACCESS_CODE = "519666233";
    private static final String GATE_COOKIE = "gate_ok";

    private final UserStore userStore;
    private final SessionManager sessions;

    public AuthHandler(UserStore userStore, SessionManager sessions) {
        this.userStore = userStore;
        this.sessions = sessions;
    }

    public void handleRoot(HttpExchange ex) throws IOException {
        if (!checkGate(ex)) {
            HttpUtil.sendHtml(ex, 200, GatePage.HTML);
        } else {
            HttpUtil.sendHtml(ex, 200, LoginPage.HTML);
        }
    }

    public void handleLoginPage(HttpExchange ex) throws IOException {
        if (!checkGate(ex)) {
            HttpUtil.sendHtml(ex, 200, GatePage.HTML);
        } else {
            HttpUtil.sendHtml(ex, 200, LoginPage.HTML);
        }
    }

    public void handleVerifyCode(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"msg\":\"method not allowed\"}");
            return;
        }
        String body = HttpUtil.readBody(ex);
        String code = HttpUtil.extractJsonString(body, "code");

        if (code != null && code.trim().equals(ACCESS_CODE)) {
            String cookie = GATE_COOKIE + "=1; Path=/; Max-Age=" + (30 * 24 * 3600) + "; SameSite=Lax";
            HttpUtil.sendJsonWithCookie(ex, 200, "{\"ok\":true}", cookie);
        } else {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"验证码错误\"}");
        }
    }

    private boolean checkGate(HttpExchange ex) {
        return "1".equals(HttpUtil.getCookie(ex, GATE_COOKIE));
    }

    /** 注册：用户名 + 密码 + 昵称 */
    public void handleRegister(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"msg\":\"method not allowed\"}");
            return;
        }
        String body = HttpUtil.readBody(ex);
        String username = HttpUtil.extractJsonString(body, "username");
        String password = HttpUtil.extractJsonString(body, "password");
        String nickname = HttpUtil.extractJsonString(body, "nickname");

        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"用户名和密码不能为空\"}");
            return;
        }
        if (nickname == null || nickname.isBlank()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"昵称不能为空\"}");
            return;
        }
        if (username.length() > 20 || password.length() > 50 || nickname.length() > 20) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"长度超出限制\"}");
            return;
        }

        if (userStore.register(username, password, nickname)) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"注册成功\"}");
        } else {
            HttpUtil.sendJson(ex, 409, "{\"ok\":false,\"msg\":\"用户名已存在\"}");
        }
    }

    /** 登录，返回 token + 用户信息 */
    public void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"msg\":\"method not allowed\"}");
            return;
        }
        String body = HttpUtil.readBody(ex);
        String username = HttpUtil.extractJsonString(body, "username");
        String password = HttpUtil.extractJsonString(body, "password");

        if (userStore.verify(username, password)) {
            User u = userStore.getUser(username);
            String token = sessions.createSession(username);
            String resp = "{\"ok\":true,\"token\":\"" + token + "\""
                    + ",\"username\":\"" + HttpUtil.escapeJson(u.username) + "\""
                    + ",\"nickname\":\"" + HttpUtil.escapeJson(u.nickname) + "\""
                    + ",\"avatarId\":" + (u.avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(u.avatarId) + "\"")
                    + ",\"isAdmin\":" + u.isAdmin + "}";
            HttpUtil.sendJson(ex, 200, resp);
        } else {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"用户名或密码错误\"}");
        }
    }

    /** 修改密码（不验证旧密码，登录页的修改密码 tab 用） */
    public void handleChangePassword(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"msg\":\"method not allowed\"}");
            return;
        }
        String body = HttpUtil.readBody(ex);
        String username = HttpUtil.extractJsonString(body, "username");
        String newPassword = HttpUtil.extractJsonString(body, "newPassword");

        if (username == null || username.isBlank() || newPassword == null || newPassword.isEmpty()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"用户名和新密码不能为空\"}");
            return;
        }
        if (newPassword.length() > 50) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"密码过长\"}");
            return;
        }

        if (userStore.changePassword(username, newPassword)) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"密码修改成功\"}");
        } else {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"用户不存在\"}");
        }
    }

    public void handleLogout(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        sessions.removeSession(token);
        HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
    }
}