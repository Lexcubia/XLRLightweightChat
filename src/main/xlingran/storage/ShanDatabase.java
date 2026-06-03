package xlingran.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public final class ShanDatabase {

    public static final String FILE_NAME = "shan.db";

    private final JavaPlugin plugin;
    private final Logger logger;
    private Connection connection;

    public ShanDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            File dbFile = new File(plugin.getDataFolder(), FILE_NAME);
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        return connection;
    }

    public void initSchema() throws SQLException {
        try (Statement st = getConnection().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, enabled_template TEXT)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        whitelist INTEGER NOT NULL DEFAULT 0,
                        auto_destroy INTEGER NOT NULL DEFAULT 0,
                        auto_craft_enabled INTEGER NOT NULL DEFAULT 0,
                        auto_smelt_enabled INTEGER NOT NULL DEFAULT 0,
                        durability_threshold INTEGER,
                        UNIQUE(player_uuid, name)
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS template_item_lists (
                        template_id INTEGER NOT NULL,
                        list_type TEXT NOT NULL,
                        item_index INTEGER NOT NULL,
                        item_blob TEXT NOT NULL,
                        PRIMARY KEY (template_id, list_type, item_index)
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS template_enchants (
                        template_id INTEGER NOT NULL,
                        enchant_key TEXT NOT NULL,
                        min_level INTEGER NOT NULL,
                        PRIMARY KEY (template_id, enchant_key)
                    )""");
            var rs = st.executeQuery("SELECT COUNT(*) AS c FROM schema_version");
            if (rs.next() && rs.getInt("c") == 0) {
                st.execute("INSERT INTO schema_version (version) VALUES (1)");
            }
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warning("[XLRHopper] 关闭 shan.db 失败: " + e.getMessage());
            }
            connection = null;
        }
    }
}
