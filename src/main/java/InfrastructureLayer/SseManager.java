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
    private final ExecutorService broadcaster = Executors.newVirtualThreadPerTaskExecutor();

    public void register(OutputStream os, String username) {
        clients.put(os, username);
    }

    public void unregister(OutputStream os) {
        clients.remove(os);
    }

    /** 统计某个用户当前的 SSE 连接数 */
    public int countConnectionsFor(String username) {
        if (username == null) return 0;
        int count = 0;
        for (String u : clients.values()) {
            if (username.equals(u)) count++;
        }
        return count;
    }

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

    public void sendTo(OutputStream os, String data) throws IOException {
        synchronized (os) {
            os.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        }
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
            clients.remove(os);
        }
    }

    public Set<String> listOnlineUsers() {
        return new HashSet<>(clients.values());
    }
}