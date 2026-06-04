package xlingran.storage;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import xlingran.HopperTemplate;
import xlingran.HopperTemplateManager;
import xlingran.ItemStackUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 一次性从旧版 plugins/XLRHopper/data.yml 导入模板（只读，不写入 YAML）。
 */
public final class LegacyDataYmlMigrator {

    private final File file;
    private final Logger logger;

    public LegacyDataYmlMigrator(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "data.yml");
        this.logger = logger;
    }

    public void load(HopperTemplateManager manager) {
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String uuidStr : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection playerSec = players.getConfigurationSection(uuidStr);
                if (playerSec == null) {
                    continue;
                }
                ConfigurationSection templates = playerSec.getConfigurationSection("templates");
                if (templates != null) {
                    for (String templateName : templates.getKeys(false)) {
                        ConfigurationSection tSec = templates.getConfigurationSection(templateName);
                        if (tSec == null) {
                            continue;
                        }
                        HopperTemplate template = new HopperTemplate();
                        template.setWhitelist(tSec.getBoolean("whitelist", false));
                        template.setAutoDestroy(tSec.getBoolean("auto-destroy", false));
                        template.setAutoCraftEnabled(tSec.getBoolean("auto-craft-enabled", false));
                        template.setAutoSmeltEnabled(tSec.getBoolean("auto-smelt-enabled", false));
                        loadAutoCraftTargets(tSec, template);
                        loadAutoSmeltOutputs(tSec, template);
                        loadFilterItems(tSec, template);
                        if (tSec.contains("durability-threshold")) {
                            template.setDurabilityThreshold(tSec.getInt("durability-threshold"));
                        }
                        loadEnchantFilters(tSec, template);
                        manager.putTemplate(uuid, templateName, template);
                    }
                }
                String enabled = playerSec.getString("enabled-template");
                if (enabled != null && !enabled.isEmpty()) {
                    manager.setEnabledTemplate(uuid, enabled);
                }
            } catch (IllegalArgumentException ex) {
                logger.warning("[XLRHopper] 无效 UUID: " + uuidStr);
            }
        }
    }

    private void loadAutoCraftTargets(ConfigurationSection tSec, HopperTemplate template) {
        List<?> serialized = tSec.getList("auto-craft-targets");
        if (serialized != null && !serialized.isEmpty()) {
            template.setAutoCraftTargets(ItemStackUtil.deserializeList(serialized));
        }
    }

    private void loadAutoSmeltOutputs(ConfigurationSection tSec, HopperTemplate template) {
        List<?> serialized = tSec.getList("auto-smelt-outputs");
        if (serialized != null && !serialized.isEmpty()) {
            template.setAutoSmeltOutputs(ItemStackUtil.deserializeList(serialized));
        }
    }

    private void loadFilterItems(ConfigurationSection tSec, HopperTemplate template) {
        List<?> serialized = tSec.getList("filter-items");
        if (serialized != null && !serialized.isEmpty()) {
            template.setFilterPrototypes(ItemStackUtil.deserializeList(serialized));
            return;
        }
        List<String> matNames = tSec.getStringList("materials");
        if (matNames.isEmpty()) {
            return;
        }
        List<ItemStack> migrated = new ArrayList<>();
        for (String matName : matNames) {
            Material mat = Material.matchMaterial(matName);
            if (mat != null) {
                migrated.add(new ItemStack(mat, 1));
            }
        }
        template.setFilterPrototypes(migrated);
    }

    private void loadEnchantFilters(ConfigurationSection tSec, HopperTemplate template) {
        ConfigurationSection enchants = tSec.getConfigurationSection("enchant-filters");
        if (enchants == null) {
            return;
        }
        for (String key : enchants.getKeys(false)) {
            Enchantment enchant = resolveEnchantment(key);
            if (enchant == null) {
                logger.warning("[XLRHopper] 未知附魔: " + key);
                continue;
            }
            int level = enchants.getInt(key);
            if (level > 0) {
                template.setEnchantMinLevel(enchant, level);
            }
        }
    }

    private static Enchantment resolveEnchantment(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        NamespacedKey namespacedKey = NamespacedKey.minecraft(key.toLowerCase());
        return Registry.ENCHANTMENT.get(namespacedKey);
    }
}
