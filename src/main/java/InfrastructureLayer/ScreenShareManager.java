package InfrastructureLayer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 屏幕共享状态管理。
 *
 * 方案是「定时截屏推流」而非 WebRTC：
 * 采集端把屏幕画到 canvas、编码成 JPEG、POST 给服务器；
 * 服务器把每一帧用 multipart/x-mixed-replace 转发给所有观看者，
 * 观看端用一个 &lt;img&gt; 就能显示，不需要额外解码代码。
 *
 * 三条不变量：
 * 1. **同一时刻最多一个人分享** —— 用 CAS 抢占用标记，抢不到就是 409。
 * 2. **观看者有上限** —— 每一路观看都要服务器复制一份帧，不设上限等于自造带宽放大。
 * 3. **分享者掉线要自动释放占用** —— 否则他一断线，别人永远抢不到这个标记。
 */
public class ScreenShareManager {

    /** 当前分享者；null 表示没人在分享 */
    private final AtomicReference<String> sharer = new AtomicReference<>(null);

    /** 分享者的显示名（昵称），用于前端提示「谁在分享」 */
    private volatile String sharerNickname = "";

    /** 最近一帧（JPEG 字节）。新加入的观看者先立刻收到它，不用干等下一帧 */
    private volatile byte[] latestFrame = null;

    /** 最近一次收到帧的时间，用于掉线检测 */
    private volatile long lastFrameAt = 0;

    /**
     * 观看者：输出流 + 用户名 + 本次观看的唯一编号 + 最后一次心跳时间，
     * 以及一个**单帧邮箱**和一条专属写线程。
     *
     * 为什么要独立写线程：原来是在发布帧的请求线程里直接写所有观看者的 socket。
     * 只要有一个观看者读得慢或已经死掉，那次 write 就会长时间阻塞，
     * 而发布帧是每秒十几次的 POST —— 浏览器的 HTTP/1.1 对同一域名只有 6 条并发连接，
     * 这几条被卡住之后连「停止分享」的请求都发不出去，整页看起来就是卡死。
     *
     * 现在的做法：发布者只把帧塞进邮箱（非阻塞），由每个观看者自己的线程去写。
     * 慢的观看者只会拖慢自己，绝不影响发布者。
     */
    private final class Viewer {
        final String username;
        final String viewId;
        final OutputStream os;

        volatile long lastSeen;

        /** 单帧邮箱：只保留最新一帧，新的直接覆盖旧的（画面本就只要最新） */
        private byte[] mailbox;
        /** 写线程是否已完成一次写操作的时间戳；用于发现卡死的写入 */
        volatile long lastWriteAt;
        volatile boolean closed;

        Viewer(OutputStream os, String username, String viewId) {
            this.os = os;
            this.username = username == null ? "" : username;
            this.viewId = viewId == null ? "" : viewId;
            long now = System.currentTimeMillis();
            this.lastSeen = now;
            this.lastWriteAt = now;
        }

        /** 非阻塞投递：覆盖旧帧 */
        synchronized void offer(byte[] chunk) {
            mailbox = chunk;
            notifyAll();
        }

        synchronized void close() {
            closed = true;
            mailbox = null;
            notifyAll();
        }

        void run() {
            try {
                while (!closed) {
                    byte[] chunk;
                    synchronized (this) {
                        while (mailbox == null && !closed) {
                            try { wait(500); } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        if (closed) return;
                        chunk = mailbox;
                        mailbox = null;
                    }
                    os.write(chunk);
                    os.flush();
                    lastWriteAt = System.currentTimeMillis();
                    // 屏幕共享流量很大，必须计入统计，否则流量面板会严重偏低
                    TrafficMonitor.accountOut(username, chunk.length);
                }
            } catch (IOException e) {
                // 观看者已经断开——只影响他自己
            } catch (RuntimeException e) {
                // 写线程里不该把异常抛到外面去
            } finally {
                removeViewer(os);
                try { os.close(); } catch (IOException ignored) {}
            }
        }
    }

    private final Map<OutputStream, Viewer> viewers = new ConcurrentHashMap<>();

    private final AtomicInteger viewerCount = new AtomicInteger(0);

    /**
     * 观看人数发生变化时递增。
     *
     * 存在的理由：fanOut 写帧失败时会**静默**移除观看者（没法从这里直接通知 SSE，
     * 那会让这个类依赖上层）。如果只有"清扫清掉了人"才广播，那么
     * 「观看者关掉浏览器 → 写帧失败移除 → 清扫发现无人在清 → 不广播」
     * 这条链路上，分享者会永远以为还有人看而继续推流。
     * 定时任务拿这个计数与上次广播值对账，就能兜住所有静默变化。
     */
    private final AtomicInteger viewerEpoch = new AtomicInteger(0);

    public int viewerEpoch() {
        return viewerEpoch.get();
    }

    // ==================== 分享者占用 ====================

    /**
     * 尝试开始分享。
     *
     * @return true 表示抢到了占用；false 表示已经有人在分享
     */
    public boolean tryStart(String username, String nickname) {
        if (username == null) return false;
        // 先看有没有过期占用，过期就顺手释放，避免掉线者把位置一直占着
        releaseIfStale();
        if (!sharer.compareAndSet(null, username)) return false;
        sharerNickname = (nickname == null || nickname.isBlank()) ? username : nickname;
        lastFrameAt = System.currentTimeMillis();
        latestFrame = null;
        return true;
    }

    /** 结束分享；只有当前分享者本人（或 force=true 的强制清理）能结束 */
    public boolean stop(String username, boolean force) {
        String cur = sharer.get();
        if (cur == null) return false;
        if (!force && !cur.equals(username)) return false;
        if (!sharer.compareAndSet(cur, null)) return false;
        sharerNickname = "";
        latestFrame = null;
        return true;
    }

    public boolean isSharing() {
        releaseIfStale();
        return sharer.get() != null;
    }

    public String currentSharer() {
        releaseIfStale();
        return sharer.get();
    }

    public String currentSharerNickname() {
        releaseIfStale();
        return sharerNickname;
    }

    /**
     * 超过空闲阈值没收到帧，就认为分享者掉线并释放占用。
     *
     * @return true 表示本次确实释放了（调用方可据此发通知）
     */
    public boolean releaseIfStale() {
        String cur = sharer.get();
        if (cur == null) return false;
        long idle = System.currentTimeMillis() - lastFrameAt;
        if (idle > ConfigurationLayer.Config.SCREEN_IDLE_TIMEOUT_MS) {
            if (sharer.compareAndSet(cur, null)) {
                sharerNickname = "";
                latestFrame = null;
                System.out.println("[Screen] " + cur + " 的屏幕共享因超时自动结束（"
                        + idle + "ms 无新帧）");
                return true;
            }
        }
        return false;
    }

    // ==================== 帧接收与扇出 ====================

    /**
     * 只续期、不发帧。
     *
     * 用途：分享者开着共享但暂时没人在看时，前端不想白白推帧（每帧几十 KB），
     * 但占用标记又必须被持续证明"人还在"，否则会被空闲超时释放。
     * 于是发一个几百字节的心跳来续期。
     *
     * @return true 表示续期成功（调用者确实是当前分享者）
     */
    public boolean touch(String username) {
        if (username == null) return false;
        if (!username.equals(sharer.get())) return false;
        lastFrameAt = System.currentTimeMillis();
        return true;
    }

    /**
     * 接收一帧并转发给所有观看者。
     *
     * @param username 提交者；不是当前分享者则丢弃（防止别人往共享里塞帧）
     * @return true 表示已接收
     */
    public boolean publishFrame(String username, byte[] jpeg) {
        if (username == null || jpeg == null || jpeg.length == 0) return false;
        if (!username.equals(sharer.get())) return false;

        lastFrameAt = System.currentTimeMillis();
        latestFrame = jpeg;
        fanOut(jpeg);
        return true;
    }

    /**
     * 把一帧投递给所有观看者。
     *
     * **绝不阻塞**：这里只是把帧放进每个观看者的单帧邮箱，
     * 真正的 socket 写入由各观看者自己的写线程完成。
     * 慢的或已死的观看者不会拖住发布帧的请求线程。
     */
    private void fanOut(byte[] jpeg) {
        if (viewers.isEmpty()) return;
        byte[] chunk = buildChunk(jpeg);
        for (Viewer v : viewers.values()) {
            v.offer(chunk);
        }
    }

    /**
     * 拼一个 MJPEG 分块。
     *
     * multipart/x-mixed-replace 的标准格式是「--边界 + 头 + 空行 + 数据 + CRLF」，
     * 第一个分块必须**直接以 --边界 开头**，前面不能有多余的 CRLF，否则浏览器会
     * 把它当成前一个分块的结尾而丢弃首帧。
     */
    public static byte[] buildChunk(byte[] jpeg) {
        StringBuilder head = new StringBuilder();
        head.append("--").append(BOUNDARY).append("\r\n");
        head.append("Content-Type: image/jpeg\r\n");
        head.append("Content-Length: ").append(jpeg.length).append("\r\n\r\n");
        byte[] headBytes = head.toString().getBytes(StandardCharsets.US_ASCII);

        byte[] out = new byte[headBytes.length + jpeg.length + 2];
        System.arraycopy(headBytes, 0, out, 0, headBytes.length);
        System.arraycopy(jpeg, 0, out, headBytes.length, jpeg.length);
        out[out.length - 2] = '\r';
        out[out.length - 1] = '\n';
        return out;
    }

    public static final String BOUNDARY = "519sb-screen-frame";

    // ==================== 观看者 ====================

    /** 观看人数是否已达上限。用于在发送响应头**之前**判断，避免先开流再失败。 */
    public boolean viewersFull() {
        return viewerCount.get() >= ConfigurationLayer.Config.SCREEN_MAX_VIEWERS;
    }

    /**
     * 加入观看。超过上限返回 false。
     * 调用方负责在结束时调用 {@link #removeViewer}，并在加入后通知分享者。
     * 这里会为这个观看者起一条专属写线程。
     */
    public boolean addViewer(OutputStream os, String username, String viewId) {
        if (os == null) return false;
        if (viewersFull()) return false;
        Viewer v = new Viewer(os, username, viewId);
        if (viewers.putIfAbsent(os, v) != null) return true;
        viewerCount.incrementAndGet();
        viewerEpoch.incrementAndGet();
        // 先把最近一帧塞进邮箱，新观众不用干等下一帧。
        // 注意：必须由写线程去写，handler 不能再碰这个流——
        // 同一个 OutputStream 有两个写者会把 MJPEG 分块写串。
        byte[] first = latestFrame;
        if (first != null) v.offer(buildChunk(first));
        // 每条观看连接一条虚拟线程：写入阻塞时只影响它自己
        Thread.ofVirtual().name("screen-writer-" + viewerCount.get()).start(v::run);
        return true;
    }

    /**
     * 观看端心跳：只续期**这一个**观看会话。
     *
     * 必须按 viewId 精确定位，不能按用户名。按用户名续期会导致：
     * 同一个人的多个观看连接（刷新页面、多开标签）里只要还有一条活着，
     * 其他已经死掉的连接也会被一起续期，永远清不掉——
     * 分享者就会一直以为还有人看而满帧推流。
     *
     * @return true 表示确实续期到了这个会话
     */
    public boolean touchViewer(String viewId) {
        if (viewId == null || viewId.isEmpty()) return false;
        for (Viewer v : viewers.values()) {
            if (viewId.equals(v.viewId)) {
                v.lastSeen = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    /** 按用户名续期（兼容没有 viewId 的旧客户端） */
    public int touchViewers(String username) {
        if (username == null) return 0;
        long now = System.currentTimeMillis();
        int n = 0;
        for (Viewer v : viewers.values()) {
            if (v.viewId.isEmpty() && username.equals(v.username)) {
                v.lastSeen = now;
                n++;
            }
        }
        return n;
    }

    /**
     * 清除超时未心跳的观看者，以及写操作已经卡死的观看者。
     *
     * @return true 表示确实清掉了（调用方据此广播新的观看人数）
     */
    public boolean sweepViewers() {
        long now = System.currentTimeMillis();
        long limit = ConfigurationLayer.Config.SCREEN_VIEWER_TIMEOUT_MS;
        boolean changed = false;
        for (Map.Entry<OutputStream, Viewer> e : viewers.entrySet()) {
            Viewer v = e.getValue();
            if (now - v.lastSeen > limit) {
                System.out.println("[Screen] 观看者 " + v.username + " 心跳超时，移出观看列表");
                removeViewer(e.getKey());
                changed = true;
                continue;
            }
            // 邮箱里还堆着没写出去的帧、而且很久没成功写过了 → 这个观看者的
            // socket 已经卡死（断网、暂停读取）。踢掉它，否则它的写线程会一直挂着。
            if (now - v.lastWriteAt > limit) {
                System.out.println("[Screen] 观看者 " + v.username + " 写入卡死，移出观看列表");
                removeViewer(e.getKey());
                changed = true;
            }
        }
        return changed;
    }

    public void removeViewer(OutputStream os) {
        if (os == null) return;
        Viewer v = viewers.remove(os);
        if (v != null) {
            v.close();          // 让写线程退出
            viewerCount.decrementAndGet();
            // 静默移除（写帧失败）也要留下痕迹，靠定时对账广播出去
            viewerEpoch.incrementAndGet();
        }
    }

    public int viewerCount() {
        return Math.max(0, viewerCount.get());
    }

    /** 最近一帧；没有则返回 null */
    public byte[] latestFrame() {
        return latestFrame;
    }

    /**
     * 结束分享时清空观看者。
     *
     * **必须非阻塞**：这个方法是分享者点「停止分享」的请求线程在调用，
     * 如果在这里对每个观看者的 socket 做 close，遇到一个死连接就会把
     * 停止请求本身卡住，表现就是"点停止页面卡死"。
     * 所以这里只把写线程标记为关闭，由它们自己收尾关闭自己的流。
     */
    public void closeAllViewers() {
        for (OutputStream os : viewers.keySet()) {
            Viewer v = viewers.get(os);
            if (v != null) v.close();
        }
    }

    /** 用于状态查询：谁在分享、几个观众 */
    public String statusJson() {
        releaseIfStale();
        String cur = sharer.get();
        return "{\"ok\":true"
                + ",\"sharing\":" + (cur != null)
                + ",\"sharer\":" + (cur == null ? "null" : "\"" + HttpUtil.escapeJson(cur) + "\"")
                + ",\"sharerNickname\":" + (cur == null ? "null" : "\"" + HttpUtil.escapeJson(sharerNickname) + "\"")
                + ",\"viewers\":" + viewerCount()
                + ",\"fps\":" + ConfigurationLayer.Config.SCREEN_FPS
                + ",\"maxWidth\":" + ConfigurationLayer.Config.SCREEN_MAX_WIDTH
                + ",\"quality\":" + ConfigurationLayer.Config.SCREEN_QUALITY
                // 前端在「无人观看」时会降频推送保活，需要知道超时阈值才能定频率：
                // 推得太稀会被服务端判定掉线而释放占用位。
                + ",\"idleTimeoutMs\":" + ConfigurationLayer.Config.SCREEN_IDLE_TIMEOUT_MS
                // 观看端心跳间隔也由服务端下发，避免两边各写一套
                + ",\"viewerTimeoutMs\":" + ConfigurationLayer.Config.SCREEN_VIEWER_TIMEOUT_MS
                + "}";
    }
}
