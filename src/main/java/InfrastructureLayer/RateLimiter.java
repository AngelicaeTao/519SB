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

    /** 清理过期条目，避免内存泄漏 */
    public void cleanup(long maxWindowMs) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().start > maxWindowMs);
    }
}