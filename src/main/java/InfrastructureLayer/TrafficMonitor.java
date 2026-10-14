package InfrastructureLayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 流量监测。
 *
 * 统计每个自然月内的收发字节数：
 * - 请求体（上传、发帖、发消息等）
 * - 响应体（页面、JSON、图片/语音下载、SSE 推送）
 *
 * 设计要点：
 * 1. **内存累积 + 定期落盘**。每个请求都写一次 SQLite 会拖垮性能，
 *    所以先在内存里加，由定时任务每 30 秒批量 UPSERT 到数据库。
 * 2. **按自然月分桶**。跨月时先落盘旧月份的数据再开始新的统计，
 *    不会把上个月的量算到这个月。
 * 3. **失败不影响业务**。任何统计异常都只记日志，绝不抛给调用方——
 *    流量统计坏了不该让聊天发不出去。
 */
public final class TrafficMonitor {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 当前统计月份，形如 2026-10 */
    private static volatile String currentPeriod = monthKey();

    /** 尚未落盘的增量：username -> [in, out]。key 用 "" 表示无法归属到具体用户的流量 */
    private static final ConcurrentHashMap<String, long[]> deltas = new ConcurrentHashMap<>();

    /** 令牌 -> 用户名。由启动时注入，避免这里反向依赖 SessionManager */
    private static volatile Function<String, String> userResolver;

    /** 上次检查月份的时间，避免每个请求都做一次日期格式化 */
    private static final AtomicLong lastPeriodCheck = new AtomicLong(0);

    private TrafficMonitor() {}

    /** 注入 token -> username 的解析器 */
    public static void setUserResolver(Function<String, String> resolver) {
        userResolver = resolver;
    }

    public static String currentPeriod() {
        return currentPeriod;
    }

    private static String monthKey() {
        return LocalDate.now().format(MONTH_FMT);
    }

    /**
     * 跨月处理：发现进入新月份时，先把上个月的增量落盘，再清空重新统计。
     * 每秒最多检查一次，避免频繁格式化日期。
     */
    private static void checkRollover() {
        long now = System.currentTimeMillis();
        long last = lastPeriodCheck.get();
        if (now - last < 1000) return;
        if (!lastPeriodCheck.compareAndSet(last, now)) return;

        String key = monthKey();
        if (!key.equals(currentPeriod)) {
            String old = currentPeriod;
            currentPeriod = key;
            // 用旧月份落盘，否则上个月的量会被算到新的月份里
            flushFor(old);
        }
    }

    /** 记一次请求体流量 */
    public static void accountIn(com.sun.net.httpserver.HttpExchange ex, long bytes) {
        if (bytes <= 0) return;
        try {
            checkRollover();
            add(resolveUser(ex), bytes, 0);
        } catch (RuntimeException e) {
            System.err.println("[Traffic] 计入流量失败：" + e.getMessage());
        }
    }

    /** 记一次响应体流量 */
    public static void accountOut(com.sun.net.httpserver.HttpExchange ex, long bytes) {
        if (bytes <= 0) return;
        try {
            checkRollover();
            add(resolveUser(ex), 0, bytes);
        } catch (RuntimeException e) {
            System.err.println("[Traffic] 计入流量失败：" + e.getMessage());
        }
    }

    /** 按用户名直接记账入向流量（与 accountOut 对称，便于测试与非 HTTP 场景） */
    public static void accountIn(String username, long bytes) {
        if (bytes <= 0) return;
        try {
            checkRollover();
            add(username, bytes, 0);
        } catch (RuntimeException e) {
            System.err.println("[Traffic] 计入流量失败：" + e.getMessage());
        }
    }

    /** 按用户名直接记账，供 SSE 推送使用（那里拿不到 exchange） */
    public static void accountOut(String username, long bytes) {
        if (bytes <= 0) return;
        try {
            checkRollover();
            add(username, 0, bytes);
        } catch (RuntimeException e) {
            System.err.println("[Traffic] 计入流量失败：" + e.getMessage());
        }
    }

    private static String resolveUser(com.sun.net.httpserver.HttpExchange ex) {
        Function<String, String> r = userResolver;
        if (r == null || ex == null) return "";
        try {
            String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
            if (token == null) return "";
            String u = r.apply(token);
            return (u == null) ? "" : u;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void add(String username, long in, long out) {
        String key = (username == null || username.isEmpty()) ? "" : username;
        long[] cur = deltas.computeIfAbsent(key, k -> new long[2]);
        synchronized (cur) {
            cur[0] += in;
            cur[1] += out;
        }
    }

    /** 把内存增量落盘（当前月份），定时任务调用 */
    public static void flush() {
        flushFor(currentPeriod);
    }

    /** 把内存增量落盘到指定月份 */
    public static synchronized void flushFor(String period) {
        if (deltas.isEmpty()) return;

        // 先取出并清零，避免落盘期间新到的流量被漏算或重复计算。
        // 用三个平行列表而不是 Map.Entry，省得纠缠泛型。
        List<String> names = new ArrayList<>();
        List<Long> ins = new ArrayList<>();
        List<Long> outs = new ArrayList<>();

        for (Map.Entry<String, long[]> entry : deltas.entrySet()) {
            long[] v = entry.getValue();
            long in, out;
            synchronized (v) {
                in = v[0];
                out = v[1];
                v[0] = 0;
                v[1] = 0;
            }
            if (in == 0 && out == 0) continue;
            names.add(entry.getKey());
            ins.add(in);
            outs.add(out);
        }
        // 清零后的空条目移除，避免 map 无限增长
        deltas.entrySet().removeIf(entry -> {
            long[] v = entry.getValue();
            synchronized (v) { return v[0] == 0 && v[1] == 0; }
        });

        if (names.isEmpty()) return;

        String sql = "INSERT INTO traffic_month(period, username, bytes_in, bytes_out) VALUES(?,?,?,?) "
                + "ON CONFLICT(period, username) DO UPDATE SET "
                + "bytes_in = bytes_in + excluded.bytes_in, "
                + "bytes_out = bytes_out + excluded.bytes_out";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < names.size(); i++) {
                ps.setString(1, period);
                ps.setString(2, names.get(i));
                ps.setLong(3, ins.get(i));
                ps.setLong(4, outs.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            System.err.println("[Traffic] 落盘失败（数据退回内存，下次重试）：" + ex.getMessage());
            for (int i = 0; i < names.size(); i++) {
                add(names.get(i), ins.get(i), outs.get(i));
            }
        }
    }

    /**
     * 某月份的汇总（数据库已落盘部分 + 内存未落盘部分）。
     * 返回 [总入, 总出, 按用户明细]。
     */
    public static Summary summary(String period) {
        String p = (period == null) ? currentPeriod : period;
        long in = 0, out = 0;
        List<String[]> perUser = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT username, bytes_in, bytes_out FROM traffic_month WHERE period = ?")) {
            ps.setString(1, p);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String u = rs.getString("username");
                    long i = rs.getLong("bytes_in");
                    long o = rs.getLong("bytes_out");
                    in += i;
                    out += o;
                    perUser.add(new String[]{u == null ? "" : u, String.valueOf(i), String.valueOf(o)});
                }
            }
        } catch (SQLException e) {
            System.err.println("[Traffic] 汇总查询失败：" + e.getMessage());
        }

        // 当前月份还要加上尚未落盘的内存增量
        if (p.equals(currentPeriod)) {
            for (Map.Entry<String, long[]> e : deltas.entrySet()) {
                long[] v = e.getValue();
                long i, o;
                synchronized (v) { i = v[0]; o = v[1]; }
                if (i == 0 && o == 0) continue;
                in += i;
                out += o;
                perUser.add(new String[]{e.getKey(), String.valueOf(i), String.valueOf(o)});
            }
        }

        // 同名用户可能既在库里又在内存里，合并一次
        Map<String, long[]> merged = new java.util.LinkedHashMap<>();
        for (String[] row : perUser) {
            String u = row[0];
            long[] cur = merged.computeIfAbsent(u, k -> new long[2]);
            cur[0] += Long.parseLong(row[1]);
            cur[1] += Long.parseLong(row[2]);
        }

        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : merged.entrySet()) {
            rows.add(new String[]{e.getKey(), String.valueOf(e.getValue()[0]), String.valueOf(e.getValue()[1])});
        }
        rows.sort(Comparator.comparingLong((String[] r) -> Long.parseLong(r[1]) + Long.parseLong(r[2])).reversed());

        return new Summary(in, out, rows);
    }

    /** 人类可读的字节数 */
    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** 在控制台打印当月汇总 */
    public static void printSummary() {
        Summary s = summary(null);
        System.out.println();
        System.out.println("================ 流量监测 " + currentPeriod + " ================");
        System.out.println("  合计上传(收) : " + human(s.bytesIn) + "   (" + s.bytesIn + " 字节)");
        System.out.println("  合计下载(发) : " + human(s.bytesOut) + "   (" + s.bytesOut + " 字节)");
        System.out.println("  总计         : " + human(s.bytesIn + s.bytesOut));

        if (s.perUser.isEmpty()) {
            System.out.println("  （本月还没有流量记录）");
        } else {
            System.out.println("  按用户（前 10）：");
            int n = Math.min(10, s.perUser.size());
            for (int i = 0; i < n; i++) {
                String[] r = s.perUser.get(i);
                long uin = Long.parseLong(r[1]);
                long uout = Long.parseLong(r[2]);
                System.out.printf("    %-20s 收 %-10s 发 %-10s 合计 %s%n",
                        r[0].isEmpty() ? "(未登录)" : r[0],
                        human(uin), human(uout), human(uin + uout));
            }
            if (s.perUser.size() > n) {
                System.out.println("    ... 其余 " + (s.perUser.size() - n) + " 个用户");
            }
        }
        System.out.println("======================================================");
        System.out.println();
    }

    /** 汇总结果 */
    public static final class Summary {
        public final long bytesIn;
        public final long bytesOut;
        /** [username, bytesIn, bytesOut] 按合计降序 */
        public final List<String[]> perUser;

        Summary(long bytesIn, long bytesOut, List<String[]> perUser) {
            this.bytesIn = bytesIn;
            this.bytesOut = bytesOut;
            this.perUser = perUser;
        }
    }
}
