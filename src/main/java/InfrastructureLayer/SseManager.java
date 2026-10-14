package InfrastructureLayer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 管理所有 SSE 客户端连接，负责广播和踢人 */
public class SseManager {

    private final Map<OutputStream, String> clients = new ConcurrentHashMap<>();
    /** 每个连接对应的来源 IP，用于注销时修正计数 */
    private final Map<OutputStream, String> clientIps = new ConcurrentHashMap<>();
    /** 每个连接对应的用户名，用于注销时修正计数 */
    private final Map<OutputStream, String> clientUsers = new ConcurrentHashMap<>();
    /** 每个连接的最后收发时间，用于空闲超时回收 */
    private final Map<OutputStream, Long> clientActivity = new ConcurrentHashMap<>();
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

    /** 把预占的名额绑定到具体的输出流上 */
    public void bind(OutputStream os, String username, String ip) {
        if (os == null || username == null) return;
        String key = (ip == null || ip.isEmpty()) ? "unknown" : ip;
        clients.put(os, username);
        clientIps.put(os, key);
        clientUsers.put(os, username);
        clientActivity.put(os, System.currentTimeMillis());
    }

    /** 刷新某连接的最后活动时间（心跳、发消息时调用），使其不被空闲超时回收 */
    public void touch(OutputStream os) {
        if (os == null) return;
        if (clients.containsKey(os)) {
            clientActivity.put(os, System.currentTimeMillis());
        }
    }

    /** 该连接已空闲的毫秒数；未知连接返回 0（视为活跃，避免误杀） */
    public long idleMillis(OutputStream os) {
        if (os == null) return 0;
        Long last = clientActivity.get(os);
        if (last == null) return 0;
        return System.currentTimeMillis() - last;
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
        removeQuietly(os);
    }

    public int globalConnections() {
        return Math.max(0, globalCount.get());
    }

    public void broadcast(String data) {
        if (data == null) return;
        byte[] payload = ("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8);

        // 同步写入：每个连接一个虚拟线程的做法会在客户端数量 × 消息频率下
        // 产生无上限的线程提交，属于可被放大的资源消耗面。
        for (Map.Entry<OutputStream, String> e : clients.entrySet()) {
            OutputStream os = e.getKey();
            try {
                synchronized (os) {
                    os.write(payload);
                    os.flush();
                }
                // 流量统计：SSE 推送按接收方归属
                TrafficMonitor.accountOut(e.getValue(), payload.length);
            } catch (IOException ex) {
                removeQuietly(os);
            }
        }
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

    /** 从注册表移除并修正计数（不关闭流，由持有者关闭） */
    private void removeQuietly(OutputStream os) {
        if (os == null) return;
        if (clients.remove(os) == null) return;
        globalCount.decrementAndGet();
        String ip = clientIps.remove(os);
        clientUsers.remove(os);
        clientActivity.remove(os);
        if (ip != null) {
            AtomicInteger c = perIpCount.get(ip);
            if (c != null) c.decrementAndGet();
        }
    }

    public void sendTo(OutputStream os, String data) throws IOException {
        byte[] payload = ("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8);
        synchronized (os) {
            os.write(payload);
        }
        String user = clients.get(os);
        if (user != null) TrafficMonitor.accountOut(user, payload.length);
        touch(os);
    }

    public void kickUser(String username) {
        kickUser(username, "你的账号已在其他设备登录");
    }

    public void kickUser(String username, String reason) {
        if (username == null) return;
        if (reason == null) reason = "你已被踢下线";

        String escaped = reason.replace("\\", "\\\\").replace("\"", "\\\"");
        String kickJson = "{\"type\":\"kick\",\"reason\":\"" + escaped + "\"}";
        byte[] payload = ("data: " + kickJson + "\n\n").getBytes(StandardCharsets.UTF_8);

        List<OutputStream> toClose = new ArrayList<>();
        for (Map.Entry<OutputStream, String> e : clients.entrySet()) {
            if (username.equals(e.getValue())) {
                try {
                    synchronized (e.getKey()) {
                        e.getKey().write(payload);
                        e.getKey().flush();
                    }
                } catch (IOException ignored) {}
                toClose.add(e.getKey());
            }
        }
        for (OutputStream os : toClose) {
            try { os.close(); } catch (IOException ignored) {}
            removeQuietly(os);
        }
    }

    public Set<String> listOnlineUsers() {
        return new HashSet<>(clients.values());
    }
}