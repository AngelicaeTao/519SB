package InfrastructureLayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** IP 级限流器：请求频率 + 并发 + 管理员登录锁定 + 注册频率 */
public class IpRateLimiter {

    private final int maxRequestsPerMinute;
    private final int maxConcurrentPerIp;
    private final int maxConcurrentGlobal;
    private final int adminLoginMaxAttempts;
    private final long adminLoginLockMs;
    private final int registerMaxPerHour;

    private static class Window {
        long start;
        int count;
    }

    private final Map<String, Window> requestWindows = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> concurrentPerIp = new ConcurrentHashMap<>();
    private final AtomicInteger concurrentGlobal = new AtomicInteger(0);

    private static class AdminLoginRecord {
        long lockUntil;
        int failCount;
        long windowStart;
    }
    private final Map<String, AdminLoginRecord> adminLoginRecords = new ConcurrentHashMap<>();
    private final Map<String, Window> registerWindows = new ConcurrentHashMap<>();

    public IpRateLimiter(int maxRequestsPerMinute, int maxConcurrentPerIp, int maxConcurrentGlobal,
                         int adminLoginMaxAttempts, long adminLoginLockMs, int registerMaxPerHour) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxConcurrentPerIp = maxConcurrentPerIp;
        this.maxConcurrentGlobal = maxConcurrentGlobal;
        this.adminLoginMaxAttempts = adminLoginMaxAttempts;
        this.adminLoginLockMs = adminLoginLockMs;
        this.registerMaxPerHour = registerMaxPerHour;
    }

    /**
     * 各计数表允许的最大条目数。
     *
     * 不设上限时，攻击者可以不断伪造来源（比如落在可信代理后面的 XFF）制造新 key，
     * 让这些 Map 无限增长吃内存。超过上限后新来源共用"溢出桶"，
     * 代价是这些来源共享一份配额——宁可误伤，也不能让内存被撑爆。
     */
    private static final int MAX_WINDOW_ENTRIES = 20000;
    private static final int MAX_ADMIN_RECORD_ENTRIES = 5000;

    private final Window overflowWindow = new Window();
    private final AdminLoginRecord overflowAdminRecord = new AdminLoginRecord();

    /** 取计数窗口；表满且是新来源时退回到共享的溢出桶 */
    private Window windowFor(Map<String, Window> map, String ip) {
        String key = (ip == null) ? "unknown" : ip;
        Window w = map.get(key);
        if (w != null) return w;

        if (map.size() >= MAX_WINDOW_ENTRIES) {
            // 先清一次过期条目，清不掉就用溢出桶
            long now = System.currentTimeMillis();
            map.entrySet().removeIf(e -> now - e.getValue().start > 120_000);
            if (map.size() >= MAX_WINDOW_ENTRIES) return overflowWindow;
        }
        return map.computeIfAbsent(key, k -> new Window());
    }

    public boolean allowRequest(String ip) {
        long now = System.currentTimeMillis();
        Window w = windowFor(requestWindows, ip);
        synchronized (w) {
            if (now - w.start >= 60_000) { w.start = now; w.count = 1; return true; }
            if (w.count >= maxRequestsPerMinute) return false;
            w.count++;
            return true;
        }
    }

    public boolean enterConcurrent(String ip) {
        if (concurrentGlobal.incrementAndGet() > maxConcurrentGlobal) {
            concurrentGlobal.decrementAndGet();
            return false;
        }
        String key = (ip == null) ? "unknown" : ip;
        // 并发计数会在 exitConcurrent 里降到 0 后被 cleanup 回收，
        // 这里额外兜一层：表异常膨胀时直接计入全局，不再为单个来源建条目
        if (concurrentPerIp.size() >= MAX_WINDOW_ENTRIES && !concurrentPerIp.containsKey(key)) {
            concurrentGlobal.decrementAndGet();
            return false;
        }
        AtomicInteger cnt = concurrentPerIp.computeIfAbsent(key, k -> new AtomicInteger(0));
        if (cnt.incrementAndGet() > maxConcurrentPerIp) {
            cnt.decrementAndGet();
            concurrentGlobal.decrementAndGet();
            return false;
        }
        return true;
    }

    public void exitConcurrent(String ip) {
        concurrentGlobal.decrementAndGet();
        AtomicInteger cnt = concurrentPerIp.get(ip == null ? "unknown" : ip);
        if (cnt != null) cnt.decrementAndGet();
    }

    public boolean isAdminLoginLocked(String ip) {
        long now = System.currentTimeMillis();
        AdminLoginRecord r = adminLoginRecords.get(ip == null ? "unknown" : ip);
        if (r != null && now < r.lockUntil) return true;
        // 计数表满时失败次数记在溢出桶上，这里必须一并检查，否则锁定会被绕过
        synchronized (overflowAdminRecord) {
            return now < overflowAdminRecord.lockUntil;
        }
    }

    public void recordAdminLoginFailure(String ip) {
        long now = System.currentTimeMillis();
        String key = (ip == null) ? "unknown" : ip;
        AdminLoginRecord r = adminLoginRecords.get(key);
        if (r == null) {
            if (adminLoginRecords.size() >= MAX_ADMIN_RECORD_ENTRIES) {
                adminLoginRecords.entrySet().removeIf(e -> now - e.getValue().windowStart > 7200_000);
                if (adminLoginRecords.size() >= MAX_ADMIN_RECORD_ENTRIES) {
                    r = overflowAdminRecord;   // 表满：新来源共用一份锁定状态
                }
            }
            if (r == null) r = adminLoginRecords.computeIfAbsent(key, k -> new AdminLoginRecord());
        }
        synchronized (r) {
            if (now - r.windowStart > adminLoginLockMs) {
                r.windowStart = now;
                r.failCount = 0;
            }
            r.failCount++;
            if (r.failCount >= adminLoginMaxAttempts) {
                r.lockUntil = now + adminLoginLockMs;
            }
        }
    }

    public void recordAdminLoginSuccess(String ip) {
        adminLoginRecords.remove(ip == null ? "unknown" : ip);
    }

    public boolean allowRegister(String ip) {
        long now = System.currentTimeMillis();
        Window w = windowForRegister(ip);
        synchronized (w) {
            if (now - w.start >= 3600_000) { w.start = now; w.count = 1; return true; }
            if (w.count >= registerMaxPerHour) return false;
            w.count++;
            return true;
        }
    }

    /** 注册窗口比请求窗口长得多，过期清理阈值也不同，单独走一份逻辑 */
    private Window windowForRegister(String ip) {
        String key = (ip == null) ? "unknown" : ip;
        Window w = registerWindows.get(key);
        if (w != null) return w;

        long now = System.currentTimeMillis();
        if (registerWindows.size() >= MAX_WINDOW_ENTRIES) {
            registerWindows.entrySet().removeIf(e -> now - e.getValue().start > 7200_000);
            if (registerWindows.size() >= MAX_WINDOW_ENTRIES) return overflowWindow;
        }
        return registerWindows.computeIfAbsent(key, k -> new Window());
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        requestWindows.entrySet().removeIf(e -> now - e.getValue().start > 120_000);
        registerWindows.entrySet().removeIf(e -> now - e.getValue().start > 7200_000);
        adminLoginRecords.entrySet().removeIf(e -> now - e.getValue().windowStart > 7200_000);
        concurrentPerIp.entrySet().removeIf(e -> e.getValue().get() <= 0);
    }
}