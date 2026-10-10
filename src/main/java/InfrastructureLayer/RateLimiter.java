package InfrastructureLayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 简易频率限制器：固定窗口计数 */
public class RateLimiter {

    private static class Window {
        long start;
        int count;
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 是否允许通过
     * @param key       限制维度（如 "login:admin" / "send:alice"）
     * @param maxCount  窗口内最大次数
     * @param windowMs  窗口时长（毫秒）
     */
    public boolean allow(String key, int maxCount, long windowMs) {
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            if (now - w.start >= windowMs) {
                w.start = now;
                w.count = 1;
                return true;
            }
            if (w.count >= maxCount) return false;
            w.count++;
            return true;
        }
    }
}