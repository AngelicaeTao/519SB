package InfrastructureLayer;

import ConfigurationLayer.Config;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite 数据库连接管理 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:" + Config.DB_FILE;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] 找不到 SQLite 驱动，请检查 pom.xml 依赖");
            throw new RuntimeException(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA busy_timeout = 5000");
        }
        return conn;
    }

    public static void init() {
        File dbFile = new File(Config.DB_FILE);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            // WAL 模式
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");

            // 用户表
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    username TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    nickname TEXT NOT NULL,
                    avatar_id TEXT,
                    is_admin INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
            """);

            // 消息表
            st.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL,
                    text TEXT NOT NULL,
                    time TEXT NOT NULL,
                    type TEXT NOT NULL DEFAULT 'text',
                    created_at INTEGER NOT NULL,
                    reply TEXT
                )
            """);
            // 兼容旧库：补 reply 列（回复引用）。已存在时抛异常，忽略即可
            try {
                st.execute("ALTER TABLE messages ADD COLUMN reply TEXT");
            } catch (SQLException ignored) {}

            // 文件消息的原始文件名（用于在消息里显示、并作为下载名）
            try {
                st.execute("ALTER TABLE messages ADD COLUMN filename TEXT");
            } catch (SQLException ignored) {}

            // 个性化配色：用户选择的主题名
            try {
                st.execute("ALTER TABLE users ADD COLUMN theme TEXT");
            } catch (SQLException ignored) {}

            // 历史昵称：每次昵称变更留一条记录，供个人中心回看
            st.execute("""
                CREATE TABLE IF NOT EXISTS nickname_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL,
                    nickname TEXT NOT NULL,
                    changed_at INTEGER NOT NULL
                )
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_nickname_history_user " +
                    "ON nickname_history(username, changed_at)");

            // 流量按月分桶。username 为空串表示无法归属到具体用户（未登录访问）
            st.execute("""
                CREATE TABLE IF NOT EXISTS traffic_month (
                    period TEXT NOT NULL,
                    username TEXT NOT NULL,
                    bytes_in INTEGER NOT NULL DEFAULT 0,
                    bytes_out INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (period, username)
                )
            """);

            // 图片表
            st.execute("""
                CREATE TABLE IF NOT EXISTS images (
                    id TEXT PRIMARY KEY,
                    data BLOB NOT NULL,
                    type TEXT NOT NULL DEFAULT 'message',
                    created_at INTEGER NOT NULL,
                    username TEXT,
                    mime TEXT,
                    filename TEXT
                )
            """);

            // 老库补列。必须放在建表之后——放在前面时新库里表还不存在，
            // ALTER 会失败并被静默忽略，导致全新部署缺列、上传直接报错。
            try {
                st.execute("ALTER TABLE images ADD COLUMN filename TEXT");
            } catch (SQLException ignored) {}

            // 会话表
            st.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    token TEXT PRIMARY KEY,
                    username TEXT NOT NULL,
                    last_active INTEGER NOT NULL
                )
            """);

            // 帖子表
            st.execute("""
                CREATE TABLE IF NOT EXISTS posts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    author TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    views INTEGER NOT NULL DEFAULT 0,
                    images TEXT
                )
            """);

            // 老库迁移：补 images 列
            try {
                st.execute("ALTER TABLE posts ADD COLUMN images TEXT");
            } catch (SQLException ignored) {}

            // 帖子点赞表
            st.execute("""
                CREATE TABLE IF NOT EXISTS post_likes (
                    post_id INTEGER NOT NULL,
                    username TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY (post_id, username)
                )
            """);

            // 帖子评论表
            st.execute("""
                CREATE TABLE IF NOT EXISTS post_comments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    post_id INTEGER NOT NULL,
                    username TEXT NOT NULL,
                    content TEXT NOT NULL,
                    parent_id INTEGER DEFAULT NULL,
                    created_at INTEGER NOT NULL,
                    images TEXT
                )
            """);

            // 兼容旧库：评论配图列
            try {
                st.execute("ALTER TABLE post_comments ADD COLUMN images TEXT");
            } catch (SQLException ignored) {}

            // 兼容旧库：images 表可能没有 type 字段
            try {
                st.execute("ALTER TABLE images ADD COLUMN type TEXT NOT NULL DEFAULT 'message'");
            } catch (SQLException ignored) {}

            // 图片归属用户，用于每用户上传配额
            try {
                st.execute("ALTER TABLE images ADD COLUMN username TEXT");
            } catch (SQLException ignored) {}

            // 图片真实 MIME（由文件头判定），用于按真实类型返回 Content-Type
            try {
                st.execute("ALTER TABLE images ADD COLUMN mime TEXT");
            } catch (SQLException ignored) {}

            // ====== 视频功能已移除，迁移历史数据 ======
            // 旧的 video 类型消息改成 file，这样它们仍能作为附件下载，
            // 而不是在页面上显示成一串看不懂的 id。
            try {
                int n = st.executeUpdate("UPDATE messages SET type = 'file' WHERE type = 'video'");
                int m = st.executeUpdate("UPDATE images SET type = 'file' WHERE type = 'video'");
                if (n > 0 || m > 0) {
                    System.out.println("[DB] 视频功能已移除，迁移历史记录：消息 " + n + " 条，媒体 " + m + " 个");
                }
            } catch (SQLException ignored) {}

            // ====== 兼容旧库：messages 表可能没有 created_at 列 ======
            // 老的 chat.db 里没有这一列时，MessageStore 里带 m.created_at 的
            // SELECT 会整个报错，导致历史和分页全部为空。这里补上。
            try {
                st.execute("ALTER TABLE messages ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0");
                System.out.println("[DB] 已为 messages 添加 created_at 列");
            } catch (SQLException ignored) {
                // 列已存在
            }

            // 老数据库迁移：给 post_comments 补 parent_id 列
            try {
                st.execute("ALTER TABLE post_comments ADD COLUMN parent_id INTEGER DEFAULT NULL");
                System.out.println("[DB] 已为 post_comments 添加 parent_id 列");
            } catch (SQLException ignored) {}

            // 索引
            st.execute("CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(created_at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_messages_username ON messages(username)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_images_created ON images(created_at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_images_username ON images(username)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_sessions_last_active ON sessions(last_active)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_posts_author ON posts(author)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_posts_created ON posts(created_at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_comments_post ON post_comments(post_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_comments_parent ON post_comments(parent_id)");

            System.out.println("[DB] 数据库初始化完成：" + DB_URL);
        } catch (SQLException e) {
            System.err.println("[DB] 初始化失败：" + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}