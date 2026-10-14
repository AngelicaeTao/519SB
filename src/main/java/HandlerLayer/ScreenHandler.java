package HandlerLayer;

import ConfigurationLayer.Config;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.ScreenShareManager;
import InfrastructureLayer.SessionManager;
import InfrastructureLayer.SseManager;
import DataLayer.User;
import DataLayer.UserStore;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 屏幕共享接口。
 *
 *   POST /screen/start        抢占分享位（同一时刻只能一个人）
 *   POST /screen/stop         结束分享
 *   POST /screen/frame        提交一帧 JPEG（原始字节，不是 base64）
 *   POST /screen/ping         分享者空闲期的心跳（只续期，不发帧）
 *   POST /screen/viewer-ping  观看端心跳（证明「我还在看」）
 *   GET  /screen/stream       观看流（multipart/x-mixed-replace，可直接塞进 img）
 *   GET  /screen/status       当前状态
 *
 * 为什么帧走原始字节：30 帧/秒下 base64 要多传 1/3 的流量还要多一次解码，
 * 白白放大带宽 —— 而带宽正是这个方案最贵的东西。
 */
public class ScreenHandler {

    private final ScreenShareManager screen;
    private final SessionManager sessions;
    private final UserStore userStore;
    private final SseManager sse;

    /** 上一帧的时间戳，用于服务端节流 */
    private volatile long lastFrameAt = 0;

    public ScreenHandler(ScreenShareManager screen, SessionManager sessions,
                         UserStore userStore, SseManager sse) {
        this.screen = screen;
        this.sessions = sessions;
        this.userStore = userStore;
        this.sse = sse;
    }

    // ==================== 路由入口 ====================

    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        if (path.equals("/screen/start") && method.equalsIgnoreCase("POST")) { handleStart(ex); return; }
        if (path.equals("/screen/stop") && method.equalsIgnoreCase("POST")) { handleStop(ex); return; }
        if (path.equals("/screen/frame") && method.equalsIgnoreCase("POST")) { handleFrame(ex); return; }
        if (path.equals("/screen/ping") && method.equalsIgnoreCase("POST")) { handlePing(ex); return; }
        if (path.equals("/screen/viewer-ping") && method.equalsIgnoreCase("POST")) { handleViewerPing(ex); return; }
        if (path.equals("/screen/stream") && method.equalsIgnoreCase("GET")) { handleStream(ex); return; }
        if (path.equals("/screen/status") && method.equalsIgnoreCase("GET")) { handleStatus(ex); return; }

        ex.sendResponseHeaders(404, -1);
        ex.close();
    }

    private String auth(HttpExchange ex) {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        return sessions.getUser(token);
    }

    /**
     * 通知所有人分享状态变了，前端据此显示/隐藏观看入口。
     *
     * 这里会调用 sse.broadcast，而 SseManager 的广播只入队、不写 socket，
     * 所以即使存在半死的 SSE 连接，也不会把当前这个 HTTP 请求线程卡住。
     */
    private void notifyChanged(boolean sharing, String nickname) {
        screen.releaseIfStale();
        String cur = screen.currentSharer();
        sse.broadcast("{\"type\":\"screen_changed\",\"sharing\":" + sharing
                + ",\"sharer\":" + (cur == null ? "null" : "\"" + HttpUtil.escapeJson(cur) + "\"")
                + ",\"sharerNickname\":" + (nickname == null || nickname.isBlank()
                        ? "null" : "\"" + HttpUtil.escapeJson(nickname) + "\"")
                + ",\"viewers\":" + screen.viewerCount()
                + "}");
    }

    /** 把当前观看人数广播给所有人（分享者据此决定推帧还是只发心跳） */
    private void notifyViewersChanged() {
        sse.broadcast("{\"type\":\"screen_viewers\",\"viewers\":" + screen.viewerCount() + "}");
    }

    // ==================== 抢占 / 结束 ====================

    private void handleStart(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "POST")) return;
        String username = auth(ex);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        User u = userStore.getUser(username);
        String nickname = (u == null || u.nickname == null) ? username : u.nickname;

        if (!screen.tryStart(username, nickname)) {
            // 抢不到就把当前占用者告诉前端，方便提示「XXX 正在分享」
            String cur = screen.currentSharer();
            String curNick = screen.currentSharerNickname();
            HttpUtil.sendJson(ex, 409, "{\"ok\":false,\"msg\":\"已经有其他人在分享屏幕\""
                    + ",\"sharer\":" + (cur == null ? "null" : "\"" + HttpUtil.escapeJson(cur) + "\"")
                    + ",\"sharerNickname\":" + (curNick == null || curNick.isBlank()
                            ? "null" : "\"" + HttpUtil.escapeJson(curNick) + "\"")
                    + "}");
            return;
        }

        lastFrameAt = 0;
        System.out.println("[Screen] " + username + " 开始分享屏幕");
        notifyChanged(true, nickname);
        HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
    }

    private void handleStop(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "POST")) return;
        String username = auth(ex);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        // closeAllViewers 只把各观看者的写线程标记为关闭，不做阻塞 I/O。
        // 这里跑的是分享者「停止分享」的请求线程，一旦卡住就是页面卡死。
        if (screen.stop(username, false)) {
            System.out.println("[Screen] " + username + " 结束分享屏幕");
            screen.closeAllViewers();
            notifyChanged(false, null);
            HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
        } else {
            // 不是分享者（或已经结束）都不算错误，前端不必弹错
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"msg\":\"当前没有在分享\"}");
        }
    }

    // ==================== 帧提交 ====================

    private void handleFrame(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "POST")) return;
        String username = auth(ex);
        if (username == null) {
            HttpUtil.sendText(ex, 401, "unauthorized");
            return;
        }

        // 服务端节流：比目标帧率高出一半的帧直接丢弃。
        // 客户端因为掉帧重试、或有人手工灌请求时，不至于把带宽和 CPU 打满。
        long now = System.currentTimeMillis();
        long minInterval = Math.max(1, 1000L / Math.max(1, Config.SCREEN_FPS) * 2 / 3);
        if (now - lastFrameAt < minInterval) {
            HttpUtil.sendText(ex, 200, "throttled");
            return;
        }
        lastFrameAt = now;

        byte[] jpeg = HttpUtil.readBodyBytes(ex, Config.SCREEN_MAX_FRAME_BYTES);
        if (jpeg.length == 0) {
            HttpUtil.sendText(ex, 400, "empty");
            return;
        }

        if (!screen.publishFrame(username, jpeg)) {
            // 不是当前分享者：可能已经超时被自动释放了，告诉前端让它停下来
            HttpUtil.sendText(ex, 409, "not-sharing");
            return;
        }
        HttpUtil.sendText(ex, 200, "ok");
    }

    /**
     * 分享者心跳：只续期占用，不发帧。
     *
     * 前端在「分享开着但没人在看」时会改用这个接口，把空闲期的流量
     * 从每秒十几帧（每帧几十 KB）降到几百字节一次。
     * 服务端靠「多久没收到帧」判断分享者是否掉线，没有心跳的话
     * 一停下来就会被判掉线、占用位被释放。
     */
    private void handlePing(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "POST")) return;
        String username = auth(ex);
        if (username == null) {
            HttpUtil.sendText(ex, 401, "unauthorized");
            return;
        }
        if (!screen.touch(username)) {
            // 已经不是分享者了（多半是超时被释放），让前端收尾
            HttpUtil.sendText(ex, 409, "not-sharing");
            return;
        }
        HttpUtil.sendText(ex, 200, "ok");
    }

    /**
     * 观看端心跳：证明「我还在看」。
     *
     * 观看者异常断开（断网、关笔记本盖子、浏览器被强杀）时 TCP 会进入半开状态，
     * 服务器往那个流写帧不会立刻报错，于是分享者会对着一个已经没人的连接
     * 持续满帧推流。这个几百字节的心跳是代价最小的发现手段：
     * 扫不到心跳的连接会被 sweepViewers 清掉，观看数归零后分享者自动停推。
     *
     * 心跳按 vid（每次观看的唯一编号）精确定位，不能按用户名——
     * 按用户名续期会让同一个人刷新页面留下的僵尸连接被一起续期、永不过期。
     */
    private void handleViewerPing(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "POST")) return;
        String username = auth(ex);
        if (username == null) {
            HttpUtil.sendText(ex, 401, "unauthorized");
            return;
        }
        // 优先按观看会话编号精确定位；没有编号的旧客户端才退回按用户名
        String vid = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "vid");
        int n;
        if (vid != null && !vid.isEmpty()) {
            n = screen.touchViewer(vid) ? 1 : 0;
        } else {
            n = screen.touchViewers(username);
        }
        HttpUtil.sendText(ex, 200, "{\"ok\":true,\"viewers\":" + n + "}");
    }

    // ==================== 观看流 ====================

    private void handleStream(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "GET")) return;
        String username = auth(ex);
        if (username == null) {
            ex.sendResponseHeaders(401, -1);
            ex.close();
            return;
        }
        if (!screen.isSharing()) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }

        OutputStream os = ex.getResponseBody();
        // 人数上限必须在发送响应头**之前**判断，否则只能先开流再失败
        if (screen.viewersFull()) {
            // 观看人数到上限：直接 429，避免每一路都再复制一份帧把带宽吃光
            ex.sendResponseHeaders(429, -1);
            ex.close();
            return;
        }

        ex.getResponseHeaders().set("Content-Type",
                "multipart/x-mixed-replace; boundary=" + ScreenShareManager.BOUNDARY);
        // 必须禁用一切缓存与代理缓冲，否则画面会卡成一帧一帧的
        ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Pragma", "no-cache");
        ex.sendResponseHeaders(200, 0);   // 0 = chunked，长度未知的持续流

        // 注册必须在响应头发出**之后**。
        // 注册就意味着 ScreenShareManager 可能立刻往这个流写帧，
        // 而 sendResponseHeaders 之前写响应体会抛「headers not sent yet」。
        String vid = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "vid");
        if (!screen.addViewer(os, username, vid)) {
            try { os.close(); } catch (IOException ignored) {}
            return;
        }

        // 立刻通知分享者「有人在看了」。
        // 分享者在无人观看时是只发心跳、不推帧的，没有这条通知它永远不会开始推，
        // 观看者就只能对着空白画面等。
        notifyViewersChanged();

        try {
            // 这里只等待，绝不碰 os。
            // 帧的写入由每个观看者专属的写线程负责；同一个 OutputStream 有两个
            // 写者会把 MJPEG 分块写串，而且一旦在这里写就可能被慢观看者卡住。
            // 首帧已经由 addViewer 塞进该观看者的邮箱了。
            while (screen.isSharing()) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            // 只解除登记；流的关闭交给写线程（os 只能有一个写者）
            screen.removeViewer(os);
            notifyViewersChanged();
        }
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "GET")) return;
        HttpUtil.sendJson(ex, 200, screen.statusJson());
    }
}
