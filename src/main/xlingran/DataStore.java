package xlingran;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

final class DataStore {

    private final File file;
    private final Logger logger;

    DataStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "data.yml");
        this.logger = logger;
    }

    void load(HopperTemplateManager manager) {
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
                        template.setWhitelist(tSec.getBoolean("whitelist", true));
                        List<String> matNames = tSec.getStringList("materials");
                        for (String matName : matNames) {
                            Material mat = Material.matchMaterial(matName);
                            if (mat != null) {
                                template.getMaterials().add(mat);
                            }
                        }
                        template.getTitleRules().addAll(tSec.getStringList("title-rules"));
                        template.getLoreRules().addAll(tSec.getStringList("lore-rules"));
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

    void save(HopperTemplateManager manager) {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, HopperTemplate>> entry : manager.getAllPlayerTemplates().entrySet()) {
            UUID uuid = entry.getKey();
            String path = "players." + uuid;
            String enabled = manager.getEnabledTemplateName(uuid);
            if (enabled != null && !enabled.isEmpty()) {
                config.set(path + ".enabled-template", enabled);
            }
            for (Map.Entry<String, HopperTemplate> templateEntry : entry.getValue().entrySet()) {
                String name = templateEntry.getKey();
                HopperTemplate t = templateEntry.getValue();
                String base = path + ".templates." + name;
                config.set(base + ".whitelist", t.isWhitelist());
                List<String> mats = t.getMaterials().stream().map(Enum::name).toList();
                config.set(base + ".materials", mats);
                config.set(base + ".title-rules", t.getTitleRules());
                config.set(base + ".lore-rules", t.getLoreRules());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            logger.severe("[XLRHopper] 无法保存 data.yml: " + e.getMessage());
        }
    }
}
