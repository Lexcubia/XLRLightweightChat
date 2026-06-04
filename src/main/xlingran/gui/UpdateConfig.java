package xlingran.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 读取 plugins/XLRHopper/Update.yml（漏斗等级与传输参数）。
 */
public final class UpdateConfig {

    private final JavaPlugin plugin;
    private final Logger logger;
    private HopperLevelDef defaultLevel = fallbackDefault();
    private Map<String, HopperLevelDef> levels = Collections.emptyMap();

    public UpdateConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "Update.yml");
        if (!file.exists()) {
            plugin.saveResource("Update.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        mergeDefaults(config);
        defaultLevel = parseSection("default", config.getConfigurationSection("default"), true);
        if (defaultLevel == null) {
            defaultLevel = fallbackDefault();
            logger.warning("[XLRHopper] Update.yml 缺少 default，使用内置默认");
        }
        Map<String, HopperLevelDef> loaded = new LinkedHashMap<>();
        ConfigurationSection levelsSec = config.getConfigurationSection("levels");
        if (levelsSec != null) {
            for (String id : levelsSec.getKeys(false)) {
                ConfigurationSection sec = levelsSec.getConfigurationSection(id);
                if (sec == null) {
                    continue;
                }
                HopperLevelDef def = parseSection(id, sec, false);
                if (def != null) {
                    loaded.put(id.toLowerCase(Locale.ROOT), def);
                }
            }
        }
        levels = Collections.unmodifiableMap(loaded);
    }

    public void reload() {
        load();
    }

    public HopperLevelDef getDefault() {
        return defaultLevel;
    }

    public HopperLevelDef getLevel(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return levels.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> levelIds() {
        return new ArrayList<>(levels.keySet());
    }

    public boolean isValidLevel(String id) {
        return id != null && levels.containsKey(id.toLowerCase(Locale.ROOT));
    }

    private HopperLevelDef parseSection(String id, ConfigurationSection sec, boolean isDefault) {
        if (sec == null) {
            return null;
        }
        int transferTick = sec.getInt("transfer-tick", defaultLevel.transferTick());
        int maxItem = sec.getInt("max-item", defaultLevel.maxItem());
        if (!validateTransferTick(id, transferTick) || !validateMaxItem(id, maxItem)) {
            return null;
        }
        String name = sec.getString("name", isDefault ? "&7默认漏斗" : "&7" + id);
        List<String> lore = sec.getStringList("Lore");
        if (lore == null || lore.isEmpty()) {
            lore = List.of();
        }
        return new HopperLevelDef(id, name, lore, transferTick, maxItem);
    }

    private boolean validateTransferTick(String id, int transferTick) {
        if (transferTick < 8 || transferTick % 8 != 0) {
            logger.warning("[XLRHopper] Update.yml " + id + " transfer-tick 无效(须>=8且为8的倍数): " + transferTick);
            return false;
        }
        return true;
    }

    private boolean validateMaxItem(String id, int maxItem) {
        if (maxItem < 1) {
            logger.warning("[XLRHopper] Update.yml " + id + " max-item 无效(须>=1): " + maxItem);
            return false;
        }
        return true;
    }

    private static HopperLevelDef fallbackDefault() {
        return new HopperLevelDef("default", "&7默认漏斗", List.of(), 24, 1);
    }

    private void mergeDefaults(YamlConfiguration target) {
        try (InputStream in = plugin.getResource("Update.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                target.setDefaults(defaults);
            }
        } catch (Exception e) {
            logger.warning("[XLRHopper] 无法合并 jar 内 Update.yml 默认值: " + e.getMessage());
        }
    }
}
