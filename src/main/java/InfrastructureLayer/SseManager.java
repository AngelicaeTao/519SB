package InfrastructureLayer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理所有 SSE 客户端连接，负责广播和踢人。
 *
 * ## 为什么每个客户端要有一条专属写线程 + 有界队列
 *
 * 最初是「谁要广播谁就直接写所有客户端的 socket」。问题在于：
 * 一个已经不响应的客户端（浏览器被强杀、断网，TCP 处于半开）会让那次 write
 * 长时间阻塞，而阻塞的是**调用广播的那个 HTTP 请求线程**。
 *
 * 真实故障：只要有这样一个半死的 SSE 连接，**发消息、撤回消息、发帖/评论**
 * 这些需要广播的请求就会卡住不返回——它们的处理线程被堵在那个 write 上。
 * 浏览器对同一域名只有 6 条并发连接，几条被卡住之后整页看起来就是卡死。
 *
 * 现在改成：广播只把消息投进各客户端的队列（**绝不碰 socket**），
 * 由每个客户端自己的写线程去写。慢的/死的客户端只会拖住它自己，
 * 无论谁调用 broadcast 都不会被阻塞。
 */
public class SseManager {

    /**
     * 单个 SSE 客户端。
     *
     * 队列有上限：客户端读得慢时丢弃最旧的消息（聊天场景下新消息比旧消息重要），
     * 避免慢客户端把服务器内存吃光。
     */
    private static final class Client {
        static final int MAX_QUEUE = 256;

        final OutputStream os;
        final String username;
        final String ip;

        /** 最后一次收发时间，用于空闲超时回收 */
        volatile long lastActivity;
        /** 最后一次成功写出的时间，用于发现卡死的客户端 */
        volatile long lastWriteAt;

        private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
        private boolean closed;
        /** 队列排空后就关闭（用于踢人：先把 kick 消息发出去，再断开） */
        private boolean closeAfterDrain;

        Client(OutputStream os, String username, String ip) {
            this.os = os;
            this.username = username;
            this.ip = ip;
            long now = System.currentTimeMillis();
            this.lastActivity = now;
            this.lastWriteAt = now;
        }

        /** 非阻塞投递；队列满则丢最旧的 */
        synchronized void offer(byte[] payload) {
            if (closed) return;
            if (queue.size() >= MAX_QUEUE) queue.pollFirst();
            queue.addLast(payload);
            notifyAll();
        }

        /** 排空后关闭（踢人用） */
        synchronized void offerThenClose(byte[] payload) {
            if (closed) return;
            offer(payload);
            closeAfterDrain = true;
            notifyAll();
        }

        synchronized void close() {
            closed = true;
            queue.clear();
            notifyAll();
        }

        synchronized boolean isClosed() {
            return closed;
        }

        void run() {
            boolean mustCloseStream = false;
            try {
                while (true) {
                    byte[] payload;
                    synchronized (this) {
                        while (queue.isEmpty()) {
                            if (closed || closeAfterDrain) return;
                            try { wait(1000); } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        payload = queue.pollFirst();
                    }
                    os.write(payload);
                    os.flush();
                    lastWriteAt = System.currentTimeMillis();
                    TrafficMonitor.accountOut(username, payload.length);
                }
            } catch (IOException e) {
                // 客户端已断开——只影响它自己
            } catch (RuntimeException e) {
                // 写线程里不该把异常抛到外面
            } finally {
                synchronized (this) {
                    // 踢人场景：kick 消息已经发完，这时才真正断开连接，
                    // 让浏览器的 EventSource 立刻感知并重连（重连会被拒绝）。
                    mustCloseStream = closeAfterDrain;
                    closed = true;
                    queue.clear();
                }
                if (mustCloseStream) {
                    try { os.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    private final Map<OutputStream, Client> clients = new ConcurrentHashMap<>();
    /** 每 IP 的 SSE 连接数，用于限制单机压连接 */
    private final Map<String, AtomicInteger> perIpCount = new ConcurrentHashMap<>();
    private final AtomicInteger globalCount = new AtomicInteger(0);

    /**
     * 预占一个 SSE 连接名额（此时响应头还没发出去，超限可以正常返回 429）。
     * 成功返回 true，之后必须调用 {@link #bind} 或 {@link #releaseReservation}。
     */
    public boolean tryRegister(String ip, int maxPerIp, int maxGlobal) {
        String key = (ip == null || ip.isEmpty()) ? "unknown" : ip;

        if (globalCount.incrementAndGet() > maxGlobal) {
            globalCount.decrementAndGet();
            return false;
        }

        AtomicInteger ipCount = perIpCount.computeIfAbsent(key, k -> new AtomicInteger(0));
        if (ipCount.incrementAndGet() > maxPerIp) {
            ipCount.decrementAndGet();
            globalCount.decrementAndGet();
            return false;
        }
        return true;
    }

    /** 放弃已预占但未绑定的名额 */
    public void releaseReservation(String ip) {
        globalCount.decrementAndGet();
        String key = (ip == null || ip.isEmpty()) ? "unknown" : ip;
        AtomicInteger c = perIpCount.get(key);
        if (c != null) c.decrementAndGet();
    }

    /**
     * 把预占的名额绑定到具体的输出流上，并启动这个客户端的写线程。
     *
     * ★ 绑定之后，**任何代码都不应再直接写这个 OutputStream**，
     * 一律走 {@link #sendTo} / {@link #sendComment} / {@link #broadcast}，
     * 否则会有两个写者把 SSE 帧写串。
     */
    public void bind(OutputStream os, String username, String ip) {
        if (os == null || username == null) return;
        String key = (ip == null || ip.isEmpty()) ? "unknown" : ip;
        Client c = new Client(os, username, key);
        if (clients.putIfAbsent(os, c) != null) return;
        Thread.ofVirtual().name("sse-writer").start(c::run);
    }

    /** 刷新某连接的最后活动时间（心跳、发消息时调用），使其不被空闲超时回收 */
    public void touch(OutputStream os) {
        if (os == null) return;
        Client c = clients.get(os);
        if (c != null) c.lastActivity = System.currentTimeMillis();
    }

    /** 该连接已空闲的毫秒数；未知连接返回 0（视为活跃，避免误杀） */
    public long idleMillis(OutputStream os) {
        if (os == null) return 0;
        Client c = clients.get(os);
        if (c == null) return 0;
        return System.currentTimeMillis() - c.lastActivity;
    }

    /** 尝试注册并立即绑定（非流式场景用） */
    public boolean tryRegister(OutputStream os, String username, String ip,
                               int maxPerIp, int maxGlobal) {
        if (os == null || username == null) return false;
        if (!tryRegister(ip, maxPerIp, maxGlobal)) return false;
        bind(os, username, ip);
        return true;
    }

    public void unregister(OutputStream os) {
        if (os == null) return;
        Client c = clients.get(os);
        removeQuietly(os);
        if (c != null) c.close();       // 停掉写线程
    }

    public int globalConnections() {
        return Math.max(0, globalCount.get());
    }

    /**
     * 广播一条 data 消息。**只入队，不写 socket**，因此绝不会阻塞调用方。
     */
    public void broadcast(String data) {
        if (data == null) return;
        byte[] payload = ("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8);
        for (Client c : clients.values()) {
            c.offer(payload);
        }
    }

    /**
     * 给单个连接发一条 data 消息（同样只入队）。
     *
     * 保留 throws IOException 是为了不改变既有调用点的形态，
     * 但这个方法现在不会因为对端慢而阻塞，也不再抛 IO 异常。
     */
    public void sendTo(OutputStream os, String data) throws IOException {
        if (os == null || data == null) return;
        Client c = clients.get(os);
        if (c == null) return;
        c.offer(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        touch(os);
    }

    /** 发一个 SSE 注释行（`: keepalive`），用于保活 */
    public void sendComment(OutputStream os, String comment) {
        if (os == null || comment == null) return;
        Client c = clients.get(os);
        if (c == null) return;
        c.offer((": " + comment + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 通知所有客户端「帖子有变动」，供帖子列表自动刷新。
     *
     * 注意：所有 SSE 消息都以 data.type 区分，聊天室客户端对未知 type 会
     * 兜底当成聊天消息渲染。所以新增事件类型时，必须同步在 chat.html 里
     * 加一条忽略分支，否则会在聊天室冒出一条空消息。
     *
     * @param action create / update / delete
     * @param postId 相关帖子 id；未知传 0
     */
    public void broadcastPostsChanged(String action, long postId) {
        String act = (action == null) ? "update" : action.replace("\"", "");
        broadcast("{\"type\":\"posts_changed\",\"action\":\"" + act + "\",\"id\":" + postId + "}");
    }

    /**
     * 从注册表移除并修正计数。**不动写线程与队列**——
     * 踢人时要先让写线程把 kick 消息发出去，所以这两件事必须分开。
     */
    private void removeQuietly(OutputStream os) {
        if (os == null) return;
        Client c = clients.remove(os);
        if (c == null) return;
        globalCount.decrementAndGet();
        AtomicInteger cnt = perIpCount.get(c.ip);
        if (cnt != null) cnt.decrementAndGet();
    }

    public void kickUser(String username) {
        kickUser(username, "你的账号已在其他设备登录");
    }

    public void kickUser(String username, String reason) {
        if (username == null) return;
        if (reason == null) reason = "你已被踢下线";

        String escaped = reason.replace("\\", "\\\\").replace("\"", "\\\"");
        byte[] payload = ("data: {\"type\":\"kick\",\"reason\":\"" + escaped + "\"}\n\n")
                .getBytes(StandardCharsets.UTF_8);

        List<Client> targets = new ArrayList<>();
        for (Client c : clients.values()) {
            if (username.equals(c.username)) targets.add(c);
        }
        for (Client c : targets) {
            // 顺序很重要：先从注册表摘掉（在线列表立刻更新、名额释放），
            // 再让写线程把 kick 消息发完并断开连接。
            removeQuietly(c.os);
            c.offerThenClose(payload);
        }
    }

    public Set<String> listOnlineUsers() {
        Set<String> users = new HashSet<>();
        for (Client c : clients.values()) {
            if (!c.isClosed()) users.add(c.username);
        }
        return users;
    }

    /**
     * 清理长时间写不出去的客户端。
     *
     * 队列有界的话，卡死的客户端只会不断丢消息而不会耗尽内存；
     * 但它会一直占着一个名额，所以还是要主动踢掉。
     *
     * @return 清掉的连接数
     */
    public int sweepStuck(long timeoutMs) {
        long now = System.currentTimeMillis();
        List<Client> dead = new ArrayList<>();
        for (Client c : clients.values()) {
            if (now - c.lastWriteAt > timeoutMs) dead.add(c);
        }
        for (Client c : dead) {
            removeQuietly(c.os);
            c.close();
            try { c.os.close(); } catch (IOException ignored) {}
        }
        return dead.size();
    }
}
