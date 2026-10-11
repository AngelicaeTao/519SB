package ConfigurationLayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/** 全局配置 */
public class Config {

    public static final int PORT;
    public static final String KEYSTORE_PATH;
    public static final String KEYSTORE_PASSWORD;
    public static final String SITE_TITLE;
    public static final int THREAD_POOL_SIZE;
    public static final int MAX_HISTORY;
    public static final int MAX_IMAGE_SIZE;
    public static final int IMAGE_MAX_WIDTH;
    public static final double IMAGE_QUALITY;
    public static final String DB_FILE;

    /** 门禁验证码 */
    public static final String ACCESS_CODE;

    /** 登录失败限制：X 次 / Y 毫秒 */
    public static final int LOGIN_MAX_ATTEMPTS;
    public static final long LOGIN_WINDOW_MS;

    /** 消息发送限制：X 条 / Y 毫秒 */
    public static final int SEND_MAX_COUNT;
    public static final long SEND_WINDOW_MS;

    /** 默认管理员账号密码 */
    public static final String ADMIN_USERNAME;
    public static final String ADMIN_PASSWORD;

    /** Session 有效期（天） */
    public static final int SESSION_TTL_DAYS;

    /** 可信反向代理地址（逗号分隔）。只有直连请求来自这些地址时，才采信 X-Forwarded-For */
    public static final String TRUSTED_PROXIES;

    /** 单个用户可上传的图片总字节上限（默认 200MB），防止灌满磁盘 */
    public static final long MAX_IMAGE_BYTES_PER_USER;

    /** 单个用户可上传的图片条数上限（默认 300） */
    public static final int MAX_IMAGE_COUNT_PER_USER;

    /** SSE 连接空闲多久后自动断开（毫秒，默认 10 分钟），防止挂死的长连接长期占用名额 */
    public static final long SSE_IDLE_TIMEOUT_MS;

    /** 启动完成后是否自动打开浏览器（默认开） */
    public static final boolean AUTO_OPEN_BROWSER;

    /** 自动打开的目标路径，例如 /chat 或 /posts */
    public static final String AUTO_OPEN_PATH;

    /** 服务器只绑定 127.0.0.1，外网通过 ngrok 转发访问 */
    public static final String BIND_ADDRESS = "127.0.0.1";

    static {
        Properties props = new Properties();

        if (Files.exists(Paths.get("config.properties"))) {
            try (InputStream is = Files.newInputStream(Paths.get("config.properties"));
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                props.load(reader);
                System.out.println("[Config] 已加载 config.properties");
            } catch (IOException e) {
                System.err.println("[Config] 读取 config.properties 失败，使用默认值：" + e.getMessage());
            }
        } else {
            System.out.println("[Config] 未找到 config.properties，使用默认配置");
        }

        PORT = Integer.parseInt(props.getProperty("port", "8443"));
        KEYSTORE_PATH = props.getProperty("keystore.path", "keystore.p12");
        KEYSTORE_PASSWORD = props.getProperty("keystore.password", "changeit");
        SITE_TITLE = props.getProperty("site.title", "519SB-Chat");
        THREAD_POOL_SIZE = Integer.parseInt(props.getProperty("thread.pool.size", "16"));
        MAX_HISTORY = Integer.parseInt(props.getProperty("max.history", "200"));
        MAX_IMAGE_SIZE = Integer.parseInt(props.getProperty("max.image.size", "5242880"));
        IMAGE_MAX_WIDTH = Integer.parseInt(props.getProperty("image.max.width", "800"));
        IMAGE_QUALITY = Double.parseDouble(props.getProperty("image.quality", "0.6"));
        DB_FILE = props.getProperty("db.file", "data/chat.db");

        ACCESS_CODE = props.getProperty("access.code", "519666233");

        LOGIN_MAX_ATTEMPTS = Integer.parseInt(props.getProperty("login.max.attempts", "5"));
        LOGIN_WINDOW_MS = Long.parseLong(props.getProperty("login.window.ms", "60000"));

        SEND_MAX_COUNT = Integer.parseInt(props.getProperty("send.max.count", "5"));
        SEND_WINDOW_MS = Long.parseLong(props.getProperty("send.window.ms", "3000"));

        ADMIN_USERNAME = props.getProperty("admin.username", "admin");
        ADMIN_PASSWORD = props.getProperty("admin.password", "admin123");

        SESSION_TTL_DAYS = Integer.parseInt(props.getProperty("session.ttl.days", "30"));

        TRUSTED_PROXIES = props.getProperty("trusted.proxies", "127.0.0.1,::1");

        MAX_IMAGE_BYTES_PER_USER = Long.parseLong(
                props.getProperty("max.image.bytes.per.user", String.valueOf(200L * 1024 * 1024)));
        MAX_IMAGE_COUNT_PER_USER = Integer.parseInt(
                props.getProperty("max.image.count.per.user", "300"));

        SSE_IDLE_TIMEOUT_MS = Long.parseLong(
                props.getProperty("sse.idle.timeout.ms", String.valueOf(10L * 60 * 1000)));

        AUTO_OPEN_BROWSER = Boolean.parseBoolean(props.getProperty("auto.open.browser", "true"));
        // 默认落在登录页；改成 /chat 或 /posts 可以直接进聊天室或论坛
        AUTO_OPEN_PATH = props.getProperty("auto.open.path", "/login");
    }
}