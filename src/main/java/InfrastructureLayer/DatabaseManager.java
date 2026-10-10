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
                    created_at INTEGER NOT NULL
                )
            """);

            // 图片表
            st.execute("""
                CREATE TABLE IF NOT EXISTS images (
                    id TEXT PRIMARY KEY,
                    data BLOB NOT NULL,
                    type TEXT NOT NULL DEFAULT 'message',
                    created_at INTEGER NOT NULL
                )
            """);

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
                    views INTEGER NOT NULL DEFAULT 0
                )
            """);

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
                    created_at INTEGER NOT NULL
                )
            """);

            // 兼容旧库：images 表可能没有 type 字段
            try {
                st.execute("ALTER TABLE images ADD COLUMN type TEXT NOT NULL DEFAULT 'message'");
            } catch (SQLException ignored) {}

            // 老数据库迁移：给 post_comments 补 parent_id 列
            try {
                st.execute("ALTER TABLE post_comments ADD COLUMN parent_id INTEGER DEFAULT NULL");
                System.out.println("[DB] 已为 post_comments 添加 parent_id 列");
            } catch (SQLException ignored) {}

            // 索引
            st.execute("CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(created_at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_messages_username ON messages(username)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_images_created ON images(created_at)");
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