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

    public boolean allowRequest(String ip) {
        long now = System.currentTimeMillis();
        Window w = requestWindows.computeIfAbsent(ip, k -> new Window());
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
        AtomicInteger cnt = concurrentPerIp.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (cnt.incrementAndGet() > maxConcurrentPerIp) {
            cnt.decrementAndGet();
            concurrentGlobal.decrementAndGet();
            return false;
        }
        return true;
    }

    public void exitConcurrent(String ip) {
        concurrentGlobal.decrementAndGet();
        AtomicInteger cnt = concurrentPerIp.get(ip);
        if (cnt != null) cnt.decrementAndGet();
    }

    public boolean isAdminLoginLocked(String ip) {
        AdminLoginRecord r = adminLoginRecords.get(ip);
        return r != null && System.currentTimeMillis() < r.lockUntil;
    }

    public void recordAdminLoginFailure(String ip) {
        long now = System.currentTimeMillis();
        AdminLoginRecord r = adminLoginRecords.computeIfAbsent(ip, k -> new AdminLoginRecord());
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
        adminLoginRecords.remove(ip);
    }

    public boolean allowRegister(String ip) {
        long now = System.currentTimeMillis();
        Window w = registerWindows.computeIfAbsent(ip, k -> new Window());
        synchronized (w) {
            if (now - w.start >= 3600_000) { w.start = now; w.count = 1; return true; }
            if (w.count >= registerMaxPerHour) return false;
            w.count++;
            return true;
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        requestWindows.entrySet().removeIf(e -> now - e.getValue().start > 120_000);
        registerWindows.entrySet().removeIf(e -> now - e.getValue().start > 7200_000);
        adminLoginRecords.entrySet().removeIf(e -> now - e.getValue().windowStart > 7200_000);
        concurrentPerIp.entrySet().removeIf(e -> e.getValue().get() <= 0);
    }
}