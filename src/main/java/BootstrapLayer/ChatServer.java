package BootstrapLayer;

import ConfigurationLayer.Config;
import DataLayer.MessageStore;
import DataLayer.UserStore;
import HandlerLayer.AdminHandler;
import HandlerLayer.AuthHandler;
import HandlerLayer.ChatHandler;
import HandlerLayer.ImageHandler;
import HandlerLayer.ProfileHandler;
import InfrastructureLayer.DatabaseManager;
import InfrastructureLayer.SessionManager;
import InfrastructureLayer.SseManager;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

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

    public static void main(String[] args) throws Exception {
        // 0. 初始化数据库
        DatabaseManager.init();

        // 1. 加载证书
        if (!Files.exists(Paths.get(Config.KEYSTORE_PATH))) {
            System.err.println("找不到 keystore 文件：" + Config.KEYSTORE_PATH);
            System.err.println("请先双击 setup.bat 生成证书。");
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

        // 2. 组装依赖
        UserStore userStore = new UserStore();
        userStore.ensureDefaultAdmin(Config.ADMIN_USERNAME, Config.ADMIN_PASSWORD);

        SessionManager sessions = new SessionManager();
        MessageStore store = new MessageStore(Config.MAX_HISTORY);
        SseManager sse = new SseManager();

        AuthHandler authHandler = new AuthHandler(userStore, sessions);
        ChatHandler chatHandler = new ChatHandler(store, sse, sessions, userStore);
        ImageHandler imageHandler = new ImageHandler(sessions);
        ProfileHandler profileHandler = new ProfileHandler(userStore, sessions);
        AdminHandler adminHandler = new AdminHandler(userStore, store, sessions, sse);

        // 3. 创建 HTTPS 服务器
        HttpsServer server = HttpsServer.create(
                new InetSocketAddress(Config.BIND_ADDRESS, Config.PORT), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));

        // 4. 注册路由
        server.createContext("/",              authHandler::handleRoot);
        server.createContext("/login",         authHandler::handleLoginPage);
        server.createContext("/api/verifyCode", authHandler::handleVerifyCode);
        server.createContext("/api/register",  authHandler::handleRegister);
        server.createContext("/api/login",     authHandler::handleLogin);
        // 注意：/api/changePassword 已删除（改密码走 /api/profile/changePassword，需登录+旧密码）
        server.createContext("/api/logout",    authHandler::handleLogout);

        server.createContext("/chat",   chatHandler::handleIndex);
        server.createContext("/send",   chatHandler::handleSend);
        server.createContext("/events", chatHandler::handleEvents);
        server.createContext("/recall", chatHandler::handleRecall);
        server.createContext("/online", chatHandler::handleOnline);
        server.createContext("/history", chatHandler::handleHistory);
        server.createContext("/image",  imageHandler);

        server.createContext("/profile",                    profileHandler::handlePage);
        server.createContext("/api/profile/update",         profileHandler::handleUpdate);
        server.createContext("/api/profile/changePassword", profileHandler::handleChangePassword);

        server.createContext("/admin",                    adminHandler::handlePage);
        server.createContext("/api/admin/users",          adminHandler::handleListUsers);
        server.createContext("/api/admin/updateUser",     adminHandler::handleUpdateUser);
        server.createContext("/api/admin/userMessages",   adminHandler::handleUserMessages);
        server.createContext("/api/admin/deleteUser",     adminHandler::handleDeleteUser);
        server.createContext("/api/admin/deleteMessage",  adminHandler::handleDeleteMessage);
        server.createContext("/api/admin/deleteMessages", adminHandler::handleDeleteMessages);
        server.createContext("/api/admin/clearMessages",  adminHandler::handleClearMessages);

        // 5. 虚拟线程（每个请求一个虚拟线程，SSE 长连接不再占用固定线程池）
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // 6. 定时清理 Session 和 RateLimiter 内存
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(() -> {
            try { sessions.cleanup(); } catch (Exception ignored) {}
        }, 10, 10, TimeUnit.MINUTES);

        server.start();

        System.out.println("==========================================");
        System.out.println("  " + Config.SITE_TITLE + " 服务器已启动");
        System.out.println("==========================================");
        System.out.println("  本机测试:  https://localhost:" + Config.PORT + "/");
        System.out.println("  默认管理员: " + Config.ADMIN_USERNAME + " / " + Config.ADMIN_PASSWORD);
        System.out.println();
        System.out.println("  外网访问请另开一个命令行窗口，运行：");
        System.out.println("    ngrok http https://localhost:" + Config.PORT);
        System.out.println("  然后把 ngrok 给出的地址发给朋友。");
        System.out.println("==========================================");
    }
}