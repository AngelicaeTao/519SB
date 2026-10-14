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

    /**
     * 已加载的配置项。
     * 保留引用是为了支持"按用途分配额"这类运行期查询——
     * 键名里带用途（max.image.bytes.per.user.comment），没法预先声明成常量。
     */
    private static final Properties PROPS = new Properties();

    /**
     * 上传用途白名单：只有这些 type 会被接受。
     *
     * 注意 file 是"任意文件"，服务端不做内容识别，因此读取时必须强制
     * 以附件下载（绝不允许内联渲染），详见 ImageHandler.handleGet。
     */
    public static final String[] MEDIA_TYPES =
            {"message", "avatar", "post", "comment", "voice", "anim", "file"};

    /** 聊天里可以出现的消息类型；其它一律归为 text */
    public static final String[] MESSAGE_TYPES = {"text", "image", "voice", "anim", "file"};

    /** 需要校验"这个附件是不是你上传的"的消息类型（正文即媒体 id） */
    public static final String[] ATTACHMENT_MESSAGE_TYPES = {"image", "voice", "anim", "file"};

    /**
     * 消息类型 -> 该消息所用的附件在 images 表里的 type。
     *
     * 两者**大多同名**（voice/anim/file），但图片是个例外：
     * 聊天图片上传时用的用途是 message（配额与保留期都挂在 message 上），
     * 而消息类型叫 image。校验附件归属时必须按这里的映射取用途，
     * 否则会把合法的图片全部判为"不属于你"。
     */
    public static String uploadTypeForMessage(String messageType) {
        if ("image".equals(messageType)) return "message";
        return messageType;
    }

    /**
     * 某用途下单个文件的最大字节数。
     * 文件类附件明显比图片大，但不设上限就等于给了灌满磁盘的入口。
     */
    public static long mediaMaxBytes(String type) {
        String v = PROPS.getProperty("max.media.size." + type);
        if (v != null) {
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException ignored) {}
        }
        switch (type == null ? "" : type) {
            case "file":  return 20L * 1024 * 1024;    // 20MB
            default:      return MAX_IMAGE_SIZE;       // 图片/语音/动图沿用旧的单文件上限
        }
    }

    /**
     * 某用途下单个用户的字节上限。
     *
     * 查找顺序：max.image.bytes.per.user.&lt;type&gt; → （仅 message/post）旧的通用键 → 按用途默认。
     *
     * 通用键只能管 message/post：它当初是为聊天图和帖子图设计的。
     * 如果让它对所有用途生效，配置文件里那行 max.image.bytes.per.user=200MB
     * 会把头像的上限也顶成 200MB，按用途区分就失去意义了。
     */
    public static long imageBytesLimit(String type) {
        String v = PROPS.getProperty("max.image.bytes.per.user." + type);
        if (v == null && usesLegacyKey(type)) v = PROPS.getProperty("max.image.bytes.per.user");
        if (v == null) return defaultBytesFor(type);
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultBytesFor(type);
        }
    }

    /** 某用途下单个用户的条数上限，查找顺序同上 */
    public static int imageCountLimit(String type) {
        String v = PROPS.getProperty("max.image.count.per.user." + type);
        if (v == null && usesLegacyKey(type)) v = PROPS.getProperty("max.image.count.per.user");
        if (v == null) return defaultCountFor(type);
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultCountFor(type);
        }
    }

    /** 该用途是否沿用旧的通用配额键（历史行为） */
    private static boolean usesLegacyKey(String type) {
        return "message".equals(type) || "post".equals(type);
    }

    private static long defaultBytesFor(String type) {
        switch (type) {
            case "avatar":  return 5L * 1024 * 1024;      // 头像很小
            case "comment": return 150L * 1024 * 1024;    // 评论图
            case "voice":   return 100L * 1024 * 1024;    // 语音
            case "anim":    return 100L * 1024 * 1024;    // 动态图（未压缩，比静态图大）
            case "file":    return 500L * 1024 * 1024;    // 文件
            case "post":    return 200L * 1024 * 1024;    // 帖子图（沿用原默认）
            default:        return 200L * 1024 * 1024;    // message
        }
    }

    private static int defaultCountFor(String type) {
        switch (type) {
            case "avatar":  return 10;
            case "comment": return 200;
            case "voice":   return 100;
            case "anim":    return 100;
            case "file":    return 100;
            case "post":    return 300;
            default:        return 300;
        }
    }

    /**
     * 某用途的媒体保留时长（毫秒）。返回 0 表示永久保留。
     *
     * 只有聊天相关的（message/voice）会定期清理：聊天记录本身有条数上限，
     * 消息被裁掉之后对应的媒体留着也没人看得到。
     * 帖子图和评论图不清理——帖子和评论是长期存在的，图删了会变裂图。
     */
    public static long imageRetentionMs(String type) {
        String v = PROPS.getProperty("image.retention.ms." + type);
        if (v != null) {
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException ignored) {}
        }
        if ("message".equals(type)) return 24L * 3600 * 1000;
        if ("voice".equals(type)) return 7L * 24 * 3600 * 1000;
        // 文件是用户有意分享的，永久保留
        return 0;
    }

    /** SSE 连接空闲多久后自动断开（毫秒，默认 10 分钟），防止挂死的长连接长期占用名额 */
    public static final long SSE_IDLE_TIMEOUT_MS;

    /** 启动完成后是否自动打开浏览器（默认开） */
    public static final boolean AUTO_OPEN_BROWSER;

    /** 自动打开的目标路径，例如 /chat 或 /posts */
    public static final String AUTO_OPEN_PATH;

    /** 服务器只绑定 127.0.0.1，外网通过 ngrok 转发访问 */
    public static final String BIND_ADDRESS = "127.0.0.1";

    static {
        // 装载进静态 PROPS，供 imageBytesLimit/imageCountLimit 这类按用途查询使用
        Properties props = PROPS;

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