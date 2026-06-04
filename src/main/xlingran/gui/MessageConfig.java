package xlingran.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 读取预置 Message.yml（不生成 YAML）。
 */
public final class MessageConfig {

    private final JavaPlugin plugin;
    private final Logger logger;
    private volatile YamlConfiguration config;

    public MessageConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "Message.yml");
        if (!file.exists()) {
            plugin.saveResource("Message.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        mergeDefaults(loaded);
        config = loaded;
    }

    public void reload() {
        load();
    }

    private void mergeDefaults(YamlConfiguration target) {
        try (InputStream in = plugin.getResource("Message.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                target.setDefaults(defaults);
            }
        } catch (Exception e) {
            logger.warning("[XLRHopper] 无法合并 jar 内 Message.yml 默认值: " + e.getMessage());
        }
    }

    public String message(String path) {
        return TextPlaceholders.color(resolveRaw(path));
    }

    public String message(String path, Map<String, String> vars) {
        return TextPlaceholders.apply(resolveRaw(path), vars);
    }

    private String resolveRaw(String path) {
        YamlConfiguration cfg = config;
        if (cfg == null) {
            return fallback(path);
        }
        String key = "Messages." + path;
        String raw = cfg.getString(key);
        if (raw == null || raw.isEmpty()) {
            var defaults = cfg.getDefaults();
            if (defaults != null) {
                raw = defaults.getString(key);
            }
        }
        if (raw == null || raw.isEmpty()) {
            logger.warning("[XLRHopper] Message.yml 缺少或为空: " + key);
            return fallback(path);
        }
        return raw;
    }

    private static String fallback(String path) {
        return "&c[XLRHopper] 缺少消息配置: &f" + path;
    }
}
