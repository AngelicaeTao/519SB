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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 管理所有 SSE 客户端连接，负责广播和踢人 */
public class SseManager {

    private final Map<OutputStream, String> clients = new ConcurrentHashMap<>();

    /** 广播专用虚拟线程池，避免慢客户端拖累其他 */
    private final ExecutorService broadcaster = Executors.newVirtualThreadPerTaskExecutor();

    public void register(OutputStream os, String username) {
        clients.put(os, username);
    }

    public void unregister(OutputStream os) {
        clients.remove(os);
    }

    /** 异步广播：每个客户端一个虚拟线程去写 */
    public void broadcast(String data) {
        if (data == null) return;
        byte[] payload = ("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<OutputStream, String> e : clients.entrySet()) {
            OutputStream os = e.getKey();
            broadcaster.submit(() -> {
                try {
                    synchronized (os) {
                        os.write(payload);
                        os.flush();
                    }
                } catch (IOException ex) {
                    clients.remove(os);
                }
            });
        }
    }

    /** 给单个连接发送一条消息（用于历史回放，同步） */
    public void sendTo(OutputStream os, String data) throws IOException {
        synchronized (os) {
            os.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 主动踢出某个用户的所有连接 */
    public void kickUser(String username) {
        String kickJson = "{\"type\":\"kick\",\"reason\":\"账号已被管理员删除\"}";
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
            clients.remove(os);
        }
    }

    /** 返回当前在线的用户名列表（去重） */
    public Set<String> listOnlineUsers() {
        return new HashSet<>(clients.values());
    }
}