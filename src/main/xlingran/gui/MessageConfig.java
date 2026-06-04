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
    private YamlConfiguration config;

    public MessageConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "Message.yml");
        if (!file.exists()) {
            plugin.saveResource("Message.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        mergeDefaults();
    }

    public void reload() {
        load();
    }

    private void mergeDefaults() {
        try (InputStream in = plugin.getResource("Message.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        } catch (Exception e) {
            logger.warning("[XLRHopper] 无法合并 jar 内 Message.yml 默认值: " + e.getMessage());
        }
    }

    public String message(String path) {
        return TextPlaceholders.color(config.getString("Messages." + path, ""));
    }

    public String message(String path, Map<String, String> vars) {
        return TextPlaceholders.apply(config.getString("Messages." + path, ""), vars);
    }
}
