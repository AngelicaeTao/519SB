package HandlerLayer;

import ConfigurationLayer.Config;
import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.IpRateLimiter;
import InfrastructureLayer.RateLimiter;
import InfrastructureLayer.SessionManager;
import InfrastructureLayer.SseManager;
import ViewLayer.GatePage;
import ViewLayer.LoginPage;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/** 门禁 + 登录、注册、登出 */
public class AuthHandler {

    private static final String GATE_COOKIE = "gate_ok";

    private final UserStore userStore;
    private final SessionManager sessions;
    private final SseManager sse;
    private final IpRateLimiter limiter;
    private final RateLimiter loginLimiter = new RateLimiter();

    public AuthHandler(UserStore userStore, SessionManager sessions, SseManager sse, IpRateLimiter limiter) {
        this.userStore = userStore;
        this.sessions = sessions;
        this.sse = sse;
        this.limiter = limiter;
    }

    public void handleRoot(HttpExchange ex) throws IOException {
        if (!checkGate(ex)) {
            HttpUtil.sendHtml(ex, 200, GatePage.HTML);
        } else {
            ex.getResponseHeaders().set("Location", "/posts");
            ex.sendResponseHeaders(302, -1);
            ex.close();
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
        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        String code = HttpUtil.extractJsonString(body, "code");

        if (code != null && code.trim().equals(Config.ACCESS_CODE)) {
            String cookie = GATE_COOKIE + "=1; Path=/; Max-Age=" + (30 * 24 * 3600) + "; SameSite=Lax";
            HttpUtil.sendJsonWithCookie(ex, 200, "{\"ok\":true}", cookie);
        } else {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"验证码错误\"}");
        }
    }

    private boolean checkGate(HttpExchange ex) {
        return "1".equals(HttpUtil.getCookie(ex, GATE_COOKIE));
    }

    /** 注册：加频率限制 */
    public void handleRegister(HttpExchange ex) throws IOException {
        if (!checkGate(ex)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"未通过验证\"}");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"msg\":\"method not allowed\"}");
            return;
        }

        String ip = BootstrapLayer.ChatServer.getClientIp(ex);
        if (!limiter.allowRegister(ip)) {
            HttpUtil.sendJson(ex, 429, "{\"ok\":false,\"msg\":\"注册过于频繁，请稍后再试\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
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
        if (username.length() > 20 || nickname.length() > 20) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"用户名或昵称过长\"}");
            return;
        }

        String pwdErr = checkPasswordStrength(password);
        if (pwdErr != null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"" + pwdErr + "\"}");
            return;
        }

        if (userStore.register(username, password, nickname)) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"注册成功\"}");
        } else {
            HttpUtil.sendJson(ex, 409, "{\"ok\":false,\"msg\":\"用户名已存在\"}");
        }
    }

    /** 登录：管理员走专用锁定机制，防爆破 */
    public void handleLogin(HttpExchange ex) throws IOException {
        if (!checkGate(ex)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"未通过验证\"}");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"msg\":\"method not allowed\"}");
            return;
        }

        String ip = BootstrapLayer.ChatServer.getClientIp(ex);
        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        String username = HttpUtil.extractJsonString(body, "username");
        String password = HttpUtil.extractJsonString(body, "password");

        if (username == null || username.isBlank()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"请输入用户名\"}");
            return;
        }

        boolean isAdminLogin = Config.ADMIN_USERNAME.equals(username);

        // 管理员登录：先检查是否被锁
        if (isAdminLogin && limiter.isAdminLoginLocked(ip)) {
            HttpUtil.sendJson(ex, 429, "{\"ok\":false,\"msg\":\"管理员登录已锁定，请稍后再试\"}");
            return;
        }

        // 普通登录：频率限制
        String limitKey = "login:" + username;
        if (!loginLimiter.allow(limitKey, Config.LOGIN_MAX_ATTEMPTS, Config.LOGIN_WINDOW_MS)) {
            HttpUtil.sendJson(ex, 429, "{\"ok\":false,\"msg\":\"登录尝试过于频繁，请稍后再试\"}");
            return;
        }

        if (userStore.verify(username, password)) {
            // 成功
            if (isAdminLogin) {
                limiter.recordAdminLoginSuccess(ip);
            }

            User u = userStore.getUser(username);

            // 单设备登录：踢掉该用户的所有旧会话和 SSE 连接
            sessions.removeAllSessionsOfUser(username);
            sse.kickUser(username, "你的账号已在其他设备登录，若非本人操作请立即修改密码");

            String token = sessions.createSession(username);
            String resp = "{\"ok\":true,\"token\":\"" + token + "\""
                    + ",\"username\":\"" + HttpUtil.escapeJson(u.username) + "\""
                    + ",\"nickname\":\"" + HttpUtil.escapeJson(u.nickname) + "\""
                    + ",\"avatarId\":" + (u.avatarId == null ? "null" : "\"" + HttpUtil.escapeJson(u.avatarId) + "\"")
                    + ",\"isAdmin\":" + u.isAdmin + "}";
            HttpUtil.sendJson(ex, 200, resp);
        } else {
            // 失败
            if (isAdminLogin) {
                limiter.recordAdminLoginFailure(ip);
            }
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"用户名或密码错误\"}");
        }
    }

    public void handleLogout(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        sessions.removeSession(token);
        HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
    }

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