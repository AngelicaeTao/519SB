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
        return DriverManager.getConnection(DB_URL);
    }

    public static void init() {
        File dbFile = new File(Config.DB_FILE);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
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
                    created_at INTEGER NOT NULL
                )
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(created_at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_images_created ON images(created_at)");

            System.out.println("[DB] 数据库初始化完成：" + DB_URL);
        } catch (SQLException e) {
            System.err.println("[DB] 初始化失败：" + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}