package BootstrapLayer;

import ConfigurationLayer.Config;
import DataLayer.MessageStore;
import DataLayer.PostStore;
import DataLayer.UserStore;
import HandlerLayer.*;
import InfrastructureLayer.DatabaseManager;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.IpRateLimiter;
import InfrastructureLayer.SessionManager;
import InfrastructureLayer.SseManager;
import com.sun.net.httpserver.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatServer {

    // ===== 限流配置（改这里即可）=====
    public static final int RATE_LIMIT_PER_MINUTE = 300;
    public static final int RATE_LIMIT_CONCURRENT_PER_IP = 30;
    public static final int RATE_LIMIT_CONCURRENT_GLOBAL = 500;
    public static final int ADMIN_LOGIN_MAX_ATTEMPTS = 3;
    public static final long ADMIN_LOGIN_LOCK_MS = 10L * 60 * 1000;
    public static final int REGISTER_MAX_PER_HOUR = 5;
    public static final int SSE_MAX_PER_USER = 3;
    public static final long MAX_BODY_NORMAL = 64L * 1024;
    public static final long MAX_BODY_UPLOAD = 10L * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        DatabaseManager.init();

        if (!Files.exists(Paths.get(Config.KEYSTORE_PATH))) {
            System.err.println("找不到 keystore 文件：" + Config.KEYSTORE_PATH);
            return;
        }

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(Paths.get(Config.KEYSTORE_PATH))) {
            ks.load(is, Config.KEYSTORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, Config.KEYSTORE_PASSWORD.toCharArray());
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, null);

        UserStore userStore = new UserStore();
        userStore.ensureDefaultAdmin(Config.ADMIN_USERNAME, Config.ADMIN_PASSWORD);

        SessionManager sessions = new SessionManager();
        MessageStore store = new MessageStore(Config.MAX_HISTORY);
        SseManager sse = new SseManager();
        PostStore postStore = new PostStore();

        IpRateLimiter limiter = new IpRateLimiter(
                RATE_LIMIT_PER_MINUTE,
                RATE_LIMIT_CONCURRENT_PER_IP,
                RATE_LIMIT_CONCURRENT_GLOBAL,
                ADMIN_LOGIN_MAX_ATTEMPTS,
                ADMIN_LOGIN_LOCK_MS,
                REGISTER_MAX_PER_HOUR);

        AuthHandler authHandler = new AuthHandler(userStore, sessions, sse, limiter);
        ChatHandler chatHandler = new ChatHandler(store, sse, sessions, userStore);
        ImageHandler imageHandler = new ImageHandler(sessions);
        ProfileHandler profileHandler = new ProfileHandler(userStore, sessions);
        AdminHandler adminHandler = new AdminHandler(userStore, store, sessions, sse);
        PostHandler postHandler = new PostHandler(postStore, sessions, userStore);

        HttpsServer server = HttpsServer.create(
                new InetSocketAddress(Config.BIND_ADDRESS, Config.PORT), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));

        server.createContext("/",              wrap(authHandler::handleRoot, limiter, MAX_BODY_NORMAL));
        server.createContext("/login",         wrap(authHandler::handleLoginPage, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/verifyCode", wrap(authHandler::handleVerifyCode, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/register",  wrap(authHandler::handleRegister, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/login",     wrap(authHandler::handleLogin, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/logout",    wrap(authHandler::handleLogout, limiter, MAX_BODY_NORMAL));

        server.createContext("/chat",   wrap(chatHandler::handleIndex, limiter, MAX_BODY_NORMAL));
        server.createContext("/send",   wrap(chatHandler::handleSend, limiter, MAX_BODY_NORMAL));
        server.createContext("/events", wrap(chatHandler::handleEvents, limiter, MAX_BODY_NORMAL));
        server.createContext("/recall", wrap(chatHandler::handleRecall, limiter, MAX_BODY_NORMAL));
        server.createContext("/online", wrap(chatHandler::handleOnline, limiter, MAX_BODY_NORMAL));
        server.createContext("/history", wrap(chatHandler::handleHistory, limiter, MAX_BODY_NORMAL));
        server.createContext("/image",  wrap(imageHandler, limiter, MAX_BODY_UPLOAD));

        server.createContext("/profile",                    wrap(profileHandler::handlePage, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/profile/update",         wrap(profileHandler::handleUpdate, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/profile/changePassword", wrap(profileHandler::handleChangePassword, limiter, MAX_BODY_NORMAL));

        server.createContext("/admin",                    wrap(adminHandler::handlePage, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/admin/users",          wrap(adminHandler::handleListUsers, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/admin/updateUser",     wrap(adminHandler::handleUpdateUser, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/admin/userMessages",   wrap(adminHandler::handleUserMessages, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/admin/deleteUser",     wrap(adminHandler::handleDeleteUser, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/admin/deleteMessage",  wrap(adminHandler::handleDeleteMessage, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/admin/deleteMessages", wrap(adminHandler::handleDeleteMessages, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/admin/clearMessages",  wrap(adminHandler::handleClearMessages, limiter, MAX_BODY_NORMAL));

        server.createContext("/posts",     wrap(postHandler, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/posts", wrap(postHandler, limiter, MAX_BODY_NORMAL));

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(() -> {
            try { sessions.cleanup(); } catch (Exception ignored) {}
            try { limiter.cleanup(); } catch (Exception ignored) {}
        }, 5, 5, TimeUnit.MINUTES);

        server.start();

        System.out.println("==========================================");
        System.out.println("  " + Config.SITE_TITLE + " 服务器已启动");
        System.out.println("==========================================");
        System.out.println("  本机测试:  https://localhost:" + Config.PORT + "/");
        System.out.println("  默认管理员: " + Config.ADMIN_USERNAME + " / " + Config.ADMIN_PASSWORD);
        System.out.println("  限流: " + RATE_LIMIT_PER_MINUTE + " 请求/分钟/IP, 并发 " + RATE_LIMIT_CONCURRENT_PER_IP);
        System.out.println("==========================================");
    }

    private static HttpHandler wrap(HttpHandler original, IpRateLimiter limiter, long maxBody) {
        return ex -> {
            String ip = getClientIp(ex);
            String path = ex.getRequestURI().getPath();
            boolean isSSE = path.startsWith("/events");

            if (!limiter.allowRequest(ip)) {
                HttpUtil.sendJson(ex, 429, "{\"ok\":false,\"msg\":\"请求过于频繁，请稍后再试\"}");
                return;
            }

            long contentLength = -1;
            String cl = ex.getRequestHeaders().getFirst("Content-Length");
            if (cl != null) {
                try { contentLength = Long.parseLong(cl); } catch (Exception ignored) {}
            }
            if (contentLength > maxBody) {
                HttpUtil.sendJson(ex, 413, "{\"ok\":false,\"msg\":\"请求体过大\"}");
                return;
            }

            if (isSSE) {
                original.handle(ex);
                return;
            }

            if (!limiter.enterConcurrent(ip)) {
                HttpUtil.sendJson(ex, 429, "{\"ok\":false,\"msg\":\"并发请求过多，请稍后再试\"}");
                return;
            }
            try {
                original.handle(ex);
            } finally {
                limiter.exitConcurrent(ip);
            }
        };
    }

    public static String getClientIp(HttpExchange ex) {
        String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            if (!first.isEmpty()) return first;
        }
        InetSocketAddress addr = ex.getRemoteAddress();
        if (addr != null && addr.getAddress() != null) {
            return addr.getAddress().getHostAddress();
        }
        return "unknown";
    }
}