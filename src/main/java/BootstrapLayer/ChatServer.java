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
import InfrastructureLayer.ScreenShareManager;
import InfrastructureLayer.TrafficMonitor;
import com.sun.net.httpserver.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
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
    public static final int SSE_MAX_PER_USER = 20;
    public static final int SSE_MAX_PER_IP = 10;
    public static final int SSE_MAX_GLOBAL = 500;
    public static final long MAX_BODY_NORMAL = 64L * 1024;
    /**
     * 上传接口的请求体上限。
     *
     * 必须大于最大的单文件上限：文件类 20MB；图片/动图走 base64 会放大 1/3，
     * 5MB 原始数据约 6.7MB，所以 25MB 足够覆盖全部情况且留有余量。
     * 这里只是粗粒度的 DoS 边界；真正的限制是 Config.mediaMaxBytes(type)。
     */
    public static final long MAX_BODY_UPLOAD = 25L * 1024 * 1024;

    /**
     * 屏幕共享接口的请求体上限。
     * 单帧由 Config.SCREEN_MAX_FRAME_BYTES 把关，这里给足余量即可。
     */
    public static final long MAX_BODY_SCREEN = 2L * 1024 * 1024;

    /**
     * SSE 连接多久没成功写出过数据就算卡死（毫秒）。
     * 对端半死时写线程会一直阻塞，这条连接既占名额又让在线列表失真。
     */
    public static final long SSE_STUCK_TIMEOUT_MS = 60_000;

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
        // 流量统计需要把请求归属到用户，注入解析器以避免反向依赖
        TrafficMonitor.setUserResolver(sessions::getUser);
        MessageStore store = new MessageStore(Config.MAX_HISTORY);
        SseManager sse = new SseManager();
        PostStore postStore = new PostStore();
        ScreenShareManager screen = new ScreenShareManager();

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
        PostHandler postHandler = new PostHandler(postStore, sessions, userStore, sse);
        UserHandler userHandler = new UserHandler(userStore, sessions);
        ScreenHandler screenHandler = new ScreenHandler(screen, sessions, userStore, sse);

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
        server.createContext("/users",  wrap(chatHandler::handleUsers, limiter, MAX_BODY_NORMAL));
        server.createContext("/history", wrap(chatHandler::handleHistory, limiter, MAX_BODY_NORMAL));
        server.createContext("/image",  wrap(imageHandler, limiter, MAX_BODY_UPLOAD));

        server.createContext("/profile",                    wrap(profileHandler::handlePage, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/profile/update",         wrap(profileHandler::handleUpdate, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/profile/changePassword", wrap(profileHandler::handleChangePassword, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/profile/nicknames",      wrap(profileHandler::handleNicknameHistory, limiter, MAX_BODY_NORMAL));
        server.createContext("/api/profile/theme",           wrap(profileHandler::handleTheme, limiter, MAX_BODY_NORMAL));

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

        server.createContext("/user",      wrap(userHandler, limiter, MAX_BODY_NORMAL));

        // 屏幕共享。这几个接口刻意**不走通用的每分钟请求数限流**：
        // 帧提交是每秒 30 次的高频调用，普通的限流窗口会被瞬间打满。
        // 真正的约束是「只有当前分享者能提交帧」+ 服务端按目标帧率节流。
        server.createContext("/screen", wrapScreen(screenHandler::handle, limiter, MAX_BODY_SCREEN));

        // 前端共用脚本。都是静态资源、不含用户数据：
        //   theme.js  按 localStorage 里的配色偏好注入覆盖样式
        //   media.js  动态图识别与上传（聊天/帖子/评论共用）
        for (String asset : new String[]{"theme.js", "media.js"}) {
            final String name = asset;
            server.createContext("/" + name, ex -> {
                try {
                    if (HttpUtil.rejectWrongMethod(ex, "GET")) return;
                    HttpUtil.sendJs(ex, 200, InfrastructureLayer.WebResources.load(name));
                } catch (IOException e) {
                    ex.close();
                }
            });
        }

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(() -> {
            try { sessions.cleanup(); } catch (Exception ignored) {}
            try { limiter.cleanup(); } catch (Exception ignored) {}
        }, 5, 5, TimeUnit.MINUTES);

        // 流量：内存累积，每 30 秒落盘一次；每 5 分钟在控制台打一次当月汇总。
        // 每个请求都写库会拖垮性能，所以走批量。
        cleaner.scheduleAtFixedRate(() -> {
            try { TrafficMonitor.flush(); } catch (Exception ignored) {}
        }, 30, 30, TimeUnit.SECONDS);

        cleaner.scheduleAtFixedRate(() -> {
            try { TrafficMonitor.printSummary(); } catch (Exception ignored) {}
        }, 5, 5, TimeUnit.MINUTES);

        // 屏幕共享掉线兜底：「同一时刻只能一个人分享」靠占用标记实现，
        // 分享者如果直接关掉页面（没走 /screen/stop），标记必须有人来收，
        // 否则别人永远抢不到。这里每 3 秒扫一次，收到就通知所有人。
        // 屏幕共享掉线兜底。每 3 秒做三件事：
        // 1) 踢掉心跳超时的观看者；
        // 2) 把观看人数与"上次广播出去的值"对账——fanOut 写帧失败时会静默移除
        //    观看者，如果不在这里对账，分享者会一直以为还有人看、继续满帧推流；
        // 3) 分享者长时间没推帧就释放占用位（他可能直接关掉了页面）。
        final int[] lastViewerCount = {-1};
        final int[] lastEpoch = {-1};
        cleaner.scheduleAtFixedRate(() -> {
            try {
                // SSE：踢掉已经写不出去的连接（对端半死时写线程会一直卡着）。
                // 这些连接不仅会占着名额，还会让在线列表失真。
                if (sse.sweepStuck(SSE_STUCK_TIMEOUT_MS) > 0) {
                    try {
                        StringBuilder sb = new StringBuilder();
                        for (String u : sse.listOnlineUsers()) {
                            if (sb.length() > 0) sb.append(',');
                            sb.append('"').append(HttpUtil.escapeJson(u)).append('"');
                        }
                        sse.broadcast("{\"type\":\"online_list\",\"users\":[" + sb + "]}");
                    } catch (Exception ignored) {}
                }
                screen.sweepViewers();
                int vc = screen.viewerCount();
                int ep = screen.viewerEpoch();
                // 人数变了、或发生过静默增减，都广播一次
                if (vc != lastViewerCount[0] || ep != lastEpoch[0]) {
                    lastViewerCount[0] = vc;
                    lastEpoch[0] = ep;
                    sse.broadcast("{\"type\":\"screen_viewers\",\"viewers\":" + vc + "}");
                }
                if (screen.releaseIfStale()) {
                    sse.broadcast("{\"type\":\"screen_changed\",\"sharing\":false,"
                            + "\"sharer\":null,\"sharerNickname\":null,"
                            + "\"viewers\":" + screen.viewerCount() + "}");
                }
            } catch (Exception ignored) {}
        }, 3, 3, TimeUnit.SECONDS);

        // 退出前把剩余增量落盘，避免重启丢掉最后几十秒的统计
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { TrafficMonitor.flush(); } catch (Exception ignored) {}
        }, "traffic-flush"));

        server.start();

        System.out.println("==========================================");
        System.out.println("  " + Config.SITE_TITLE + " 服务器已启动");
        System.out.println("==========================================");
        System.out.println("  本机测试:  https://localhost:" + Config.PORT + "/");
        System.out.println("  默认管理员: " + Config.ADMIN_USERNAME + " / " + Config.ADMIN_PASSWORD);
        System.out.println("  限流: " + RATE_LIMIT_PER_MINUTE + " 请求/分钟/IP, 并发 " + RATE_LIMIT_CONCURRENT_PER_IP);
        System.out.println("  流量统计: 每月重置，每 5 分钟在控制台打印一次当月汇总");
        System.out.println("==========================================");

        // 启动时先看一眼当月已有多少流量
        try { TrafficMonitor.printSummary(); } catch (Exception ignored) {}

        maybeOpenBrowser();
    }

    /**
     * 启动后自动打开浏览器，优先打开 ngrok 的公网入口（队友直接能访问的那个地址），
     * 拿不到公网地址时退回本机地址。失败只提示，不影响服务运行。
     */
    private static void maybeOpenBrowser() {
        if (!Config.AUTO_OPEN_BROWSER) return;

        String path = Config.AUTO_OPEN_PATH;
        if (path == null || path.isBlank()) path = "/login";
        if (!path.startsWith("/")) path = "/" + path;

        // 触碰到本机端口再开浏览器，避免标签页先于服务就绪打开而报连接失败
        if (!waitForServerReady(5000)) {
            System.out.println("[Browser] 服务尚未就绪，跳过自动打开");
            return;
        }

        String publicUrl = waitForNgrokPublicUrl(12_000);

        String url;
        boolean viaNgrok;
        if (publicUrl != null) {
            url = publicUrl + path;
            viaNgrok = true;
        } else {
            url = "https://localhost:" + Config.PORT + path;
            viaNgrok = false;
        }

        if (openInBrowser(url)) {
            System.out.println("  已自动打开浏览器: " + url);
            if (viaNgrok) {
                System.out.println("  （公网入口；ngrok 免费版首次访问会先弹一个提示页，点一下继续即可）");
            } else {
                System.out.println("  （未检测到 ngrok 隧道，打开的是本机地址）");
                System.out.println("  （自签名证书，浏览器可能提示不安全，选择继续访问即可）");
            }
        } else {
            System.out.println("[Browser] 自动打开失败，请手动访问: " + url);
        }
    }

    /**
     * 向 ngrok 本地 API 询问当前隧道地址。
     *
     * start.bat 是「先开 ngrok、再开服务器」，所以这里通常一问就有；
     * 但如果是手动启动、ngrok 还没连上，就重试一段时间。
     *
     * @return 公网地址（如 https://xxx.ngrok-free.app），拿不到返回 null
     */
    private static String waitForNgrokPublicUrl(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String url = fetchNgrokPublicUrl(1200);
            if (url != null) return url;
            try { Thread.sleep(500); } catch (InterruptedException e) { return null; }
        }
        return null;
    }

    /** 调 http://127.0.0.1:4040/api/tunnels（ngrok agent 的本地 API，无需认证） */
    private static String fetchNgrokPublicUrl(long timeoutMs) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(timeoutMs))
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest
                    .newBuilder(java.net.URI.create("http://127.0.0.1:4040/api/tunnels"))
                    .timeout(java.time.Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) return null;

            // 优先取 https 隧道；没有就退回 http
            java.util.regex.Pattern p = java.util.regex.Pattern
                    .compile("\"public_url\"\\s*:\\s*\"(https?://[^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(resp.body());
            String fallback = null;
            while (m.find()) {
                String u = m.group(1);
                if (u.startsWith("https://")) return trim(u);
                if (fallback == null) fallback = u;
            }
            return trim(fallback);
        } catch (Exception ignored) {
            // ngrok 没在跑、端口被占用、web 界面被改到别的端口等，都走本机地址兜底
            return null;
        }
    }

    private static String trim(String url) {
        if (url == null) return null;
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u.isEmpty() ? null : u;
    }

    /** 轮询自身端口直到可以建立连接，或超时 */
    private static boolean waitForServerReady(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress("127.0.0.1", Config.PORT), 300);
                return true;
            } catch (Exception ignored) {
                try { Thread.sleep(120); } catch (InterruptedException e) { return false; }
            }
        }
        return false;
    }

    /** 依次尝试 Desktop API 和各系统命令行，任一成功即返回 */
    private static boolean openInBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(new java.net.URI(url));
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 无图形环境或 AWT 不可用时继续走命令行兜底
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        String[][] candidates;
        if (os.contains("win")) {
            // cmd /c start 的第一个参数是窗口标题，不能省，否则带引号的 URL 会被当成标题
            candidates = new String[][]{
                    {"cmd", "/c", "start", "", url},
                    {"rundll32", "url.dll,FileProtocolHandler", url}
            };
        } else if (os.contains("mac")) {
            candidates = new String[][]{{"open", url}};
        } else {
            candidates = new String[][]{{"xdg-open", url}};
        }

        for (String[] cmd : candidates) {
            try {
                new ProcessBuilder(cmd).redirectErrorStream(true).start();
                return true;
            } catch (Exception ignored) {
                // 换下一种方式
            }
        }
        return false;
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

            // 写操作的跨站防护：放在并发计数之前，跨站请求不占用并发额度
            if (HttpUtil.rejectCrossSitePost(ex)) {
                return;
            }

            if (!limiter.enterConcurrent(ip)) {
                HttpUtil.sendJson(ex, 429, "{\"ok\":false,\"msg\":\"并发请求过多，请稍后再试\"}");
                return;
            }
            try {
                original.handle(ex);
            } catch (HttpUtil.BodyTooLargeException e) {
                // 流式读取时才发现超限（chunked 等未声明 Content-Length 的请求）
                try {
                    HttpUtil.sendJson(ex, 413, "{\"ok\":false,\"msg\":\"请求体过大\"}");
                } catch (IOException ignored) {}
            } finally {
                limiter.exitConcurrent(ip);
            }
        };
    }

    /**
     * 屏幕共享专用包装：与通用 wrap 的区别是**跳过每分钟请求数限流**。
     *
     * 帧提交每秒 30 次，一分钟就是 1800 次，而通用限流是 300 次/分钟/IP——
     * 走通用包装的话分享一开始就会被自己限流打断。
     * 这里保留并发计数（长连接确实占资源）与跨站校验。
     */
    private static HttpHandler wrapScreen(HttpHandler original, IpRateLimiter limiter, long maxBody) {
        return ex -> {
            String ip = getClientIp(ex);

            long contentLength = -1;
            String cl = ex.getRequestHeaders().getFirst("Content-Length");
            if (cl != null) {
                try { contentLength = Long.parseLong(cl); } catch (Exception ignored) {}
            }
            if (contentLength > maxBody) {
                HttpUtil.sendJson(ex, 413, "{\"ok\":false,\"msg\":\"请求体过大\"}");
                return;
            }

            if (HttpUtil.rejectCrossSitePost(ex)) return;

            if (!limiter.enterConcurrent(ip)) {
                HttpUtil.sendJson(ex, 429, "{\"ok\":false,\"msg\":\"并发请求过多，请稍后再试\"}");
                return;
            }
            try {
                original.handle(ex);
            } catch (HttpUtil.BodyTooLargeException e) {
                try {
                    HttpUtil.sendJson(ex, 413, "{\"ok\":false,\"msg\":\"请求体过大\"}");
                } catch (IOException ignored) {}
            } finally {
                limiter.exitConcurrent(ip);
            }
        };
    }

    public static String getClientIp(HttpExchange ex) {
        InetSocketAddress addr = ex.getRemoteAddress();
        String direct = (addr != null && addr.getAddress() != null)
                ? addr.getAddress().getHostAddress()
                : "unknown";

        // 只有直连方是可信反向代理时才采信 X-Forwarded-For。
        // 否则任何客户端都能自带该头伪造来源 IP，从而绕过 IP 限流/注册限制/管理员锁定。
        if (!isTrustedProxy(direct)) return direct;

        String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff == null) return direct;
        // 限长，避免超长请求头制造无谓的内存与字符串开销
        if (xff.length() > 256) xff = xff.substring(0, 256);

        int comma = xff.indexOf(',');
        String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
        if (first.isEmpty()) return direct;

        // 去掉可选的端口部分（[v6]:port 或 v4:port）
        if (first.startsWith("[")) {
            int end = first.indexOf(']');
            if (end > 0) first = first.substring(1, end);
        } else {
            int colon = first.indexOf(':');
            if (colon > 0 && first.indexOf(':', colon + 1) < 0) first = first.substring(0, colon);
        }

        // 必须是合法 IP 才采纳，避免任意字符串进入限流器的 Map 造成键膨胀
        return IPV4_PATTERN.matcher(first).matches() ? first : direct;
    }

    private static boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        return TRUSTED_PROXIES.contains(ip);
    }

    private static final java.util.Set<String> TRUSTED_PROXIES = buildTrustedProxies();

    private static final java.util.regex.Pattern IPV4_PATTERN = java.util.regex.Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private static java.util.Set<String> buildTrustedProxies() {
        java.util.Set<String> set = new java.util.HashSet<>();
        set.add("127.0.0.1");
        set.add("::1");
        set.add("0:0:0:0:0:0:0:1");
        for (String raw : Config.TRUSTED_PROXIES.split(",")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            // 允许写成 "127.0.0.1:8443" 或 "[::1]:8443" 的形式
            if (s.startsWith("[")) {
                int end = s.indexOf(']');
                if (end > 0) s = s.substring(1, end);
            } else if (s.indexOf(':') > 0 && s.indexOf(':', s.indexOf(':') + 1) < 0) {
                s = s.substring(0, s.indexOf(':'));
            }
            if (!s.isEmpty()) set.add(s);
        }
        return java.util.Collections.unmodifiableSet(set);
    }
}