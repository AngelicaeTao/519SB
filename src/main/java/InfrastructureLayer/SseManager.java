package InfrastructureLayer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 管理所有 SSE 客户端连接，负责广播和踢人 */
public class SseManager {

    /** OutputStream → username */
    private final Map<OutputStream, String> clients = new ConcurrentHashMap<>();

    public void register(OutputStream os, String username) {
        clients.put(os, username);
    }

    public void unregister(OutputStream os) {
        clients.remove(os);
    }

    /** 广播一条 SSE 消息 */
    public void broadcast(String data) {
        byte[] payload = ("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8);
        for (Map.Entry<OutputStream, String> e : clients.entrySet()) {
            try {
                e.getKey().write(payload);
                e.getKey().flush();
            } catch (IOException ex) {
                clients.remove(e.getKey());
            }
        }
    }

    /** 给单个连接发送一条消息（用于历史回放） */
    public void sendTo(OutputStream os, String data) throws IOException {
        os.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    /** 主动踢出某个用户的所有连接，并推送 kick 事件 */
    public void kickUser(String username) {
        String kickJson = "{\"type\":\"kick\",\"reason\":\"账号已被管理员删除\"}";
        byte[] payload = ("data: " + kickJson + "\n\n").getBytes(StandardCharsets.UTF_8);

        List<OutputStream> toClose = new ArrayList<>();
        for (Map.Entry<OutputStream, String> e : clients.entrySet()) {
            if (username.equals(e.getValue())) {
                try {
                    e.getKey().write(payload);
                    e.getKey().flush();
                } catch (IOException ignored) {}
                toClose.add(e.getKey());
            }
        }
        for (OutputStream os : toClose) {
            try { os.close(); } catch (IOException ignored) {}
            clients.remove(os);
        }
    }
}