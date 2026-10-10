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
        userStore.ensureDefaultAdmin();     // 没有管理员时自动创建 admin / admin123

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
        // 门禁 / 登录相关
        server.createContext("/",                   authHandler::handleRoot);
        server.createContext("/login",              authHandler::handleLoginPage);
        server.createContext("/api/verifyCode",     authHandler::handleVerifyCode);
        server.createContext("/api/register",       authHandler::handleRegister);
        server.createContext("/api/login",          authHandler::handleLogin);
        server.createContext("/api/changePassword", authHandler::handleChangePassword);
        server.createContext("/api/logout",         authHandler::handleLogout);

        // 聊天相关
        server.createContext("/chat",   chatHandler::handleIndex);
        server.createContext("/send",   chatHandler::handleSend);
        server.createContext("/events", chatHandler::handleEvents);
        server.createContext("/image",  imageHandler);

        // 个人中心
        server.createContext("/profile",                     profileHandler::handlePage);
        server.createContext("/api/profile/update",          profileHandler::handleUpdate);
        server.createContext("/api/profile/changePassword",  profileHandler::handleChangePassword);

        // 管理员
        server.createContext("/admin",                    adminHandler::handlePage);
        server.createContext("/api/admin/users",          adminHandler::handleListUsers);
        server.createContext("/api/admin/updateUser",     adminHandler::handleUpdateUser);
        server.createContext("/api/admin/userMessages",   adminHandler::handleUserMessages);
        server.createContext("/api/admin/deleteUser",     adminHandler::handleDeleteUser);
        server.createContext("/api/admin/deleteMessage",  adminHandler::handleDeleteMessage);
        server.createContext("/api/admin/deleteMessages", adminHandler::handleDeleteMessages);

        server.setExecutor(Executors.newFixedThreadPool(Config.THREAD_POOL_SIZE));
        server.start();

        System.out.println("==========================================");
        System.out.println("  " + Config.SITE_TITLE + " 服务器已启动");
        System.out.println("==========================================");
        System.out.println("  本机测试:  https://localhost:" + Config.PORT + "/");
        System.out.println("  默认管理员: admin / admin123");
        System.out.println();
        System.out.println("  外网访问请另开一个命令行窗口，运行：");
        System.out.println("    ngrok http https://localhost:" + Config.PORT);
        System.out.println("  然后把 ngrok 给出的地址发给朋友。");
        System.out.println("==========================================");
    }
}