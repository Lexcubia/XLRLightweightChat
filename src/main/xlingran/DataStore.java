package xlingran;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

final class DataStore {

    private static final String BOX_EMPTY_MARKER = "__empty__";

    private final File file;
    private final Logger logger;

    DataStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "data.yml");
        this.logger = logger;
    }

    void load(HopperTemplateManager manager, PlayerBoxManager boxManager) {
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
                loadBoxes(playerSec, uuid, boxManager);
                ConfigurationSection templates = playerSec.getConfigurationSection("templates");
                if (templates != null) {
                    for (String templateName : templates.getKeys(false)) {
                        ConfigurationSection tSec = templates.getConfigurationSection(templateName);
                        if (tSec == null) {
                            continue;
                        }
                        HopperTemplate template = new HopperTemplate();
                        template.setWhitelist(tSec.getBoolean("whitelist", true));
                        template.setAutoDestroy(tSec.getBoolean("auto-destroy", false));
                        String linkedBox = tSec.getString("linked-box");
                        if (linkedBox != null && !linkedBox.isEmpty()) {
                            template.setLinkedBoxName(linkedBox);
                        }
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

    private void loadBoxes(ConfigurationSection playerSec, UUID uuid, PlayerBoxManager boxManager) {
        ConfigurationSection boxes = playerSec.getConfigurationSection("boxes");
        if (boxes == null) {
            return;
        }
        Map<String, ItemStack[]> loaded = new HashMap<>();
        for (String boxName : boxes.getKeys(false)) {
            ConfigurationSection slotSec = boxes.getConfigurationSection(boxName);
            ItemStack[] arr = new ItemStack[PlayerBoxManager.BOX_CAPACITY];
            if (slotSec != null) {
                for (String key : slotSec.getKeys(false)) {
                    if (BOX_EMPTY_MARKER.equals(key)) {
                        continue;
                    }
                    try {
                        int index = Integer.parseInt(key);
                        if (index >= 0 && index < PlayerBoxManager.BOX_CAPACITY) {
                            ItemStack stack = slotSec.getItemStack(key);
                            if (stack != null && !stack.getType().isAir()) {
                                arr[index] = stack;
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            loaded.put(boxName, arr);
        }
        boxManager.putPlayerBoxes(uuid, loaded);
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
        List<ItemStack> migrated = new java.util.ArrayList<>();
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

    void save(HopperTemplateManager manager, PlayerBoxManager boxManager) {
        FileConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();

        Set<UUID> playerIds = new HashSet<>();
        playerIds.addAll(manager.getAllPlayerTemplates().keySet());
        playerIds.addAll(boxManager.getAllPlayerBoxes().keySet());

        for (UUID uuid : playerIds) {
            String path = "players." + uuid;
            String enabled = manager.getEnabledTemplateName(uuid);
            if (enabled != null && !enabled.isEmpty()) {
                config.set(path + ".enabled-template", enabled);
            } else {
                config.set(path + ".enabled-template", null);
            }
            savePlayerBoxes(config, path, uuid, boxManager);
            Map<String, HopperTemplate> templates = manager.getTemplates(uuid);
            for (Map.Entry<String, HopperTemplate> templateEntry : templates.entrySet()) {
                String name = templateEntry.getKey();
                HopperTemplate t = templateEntry.getValue();
                String base = path + ".templates." + name;
                config.set(base + ".whitelist", t.isWhitelist());
                config.set(base + ".auto-destroy", t.isAutoDestroy());
                if (t.getLinkedBoxName() != null && !t.getLinkedBoxName().isEmpty()) {
                    config.set(base + ".linked-box", t.getLinkedBoxName());
                } else {
                    config.set(base + ".linked-box", null);
                }
                config.set(base + ".filter-items", ItemStackUtil.serializeList(t.getFilterPrototypes()));
                if (t.getDurabilityThreshold() != null) {
                    config.set(base + ".durability-threshold", t.getDurabilityThreshold());
                } else {
                    config.set(base + ".durability-threshold", null);
                }
                Map<String, Integer> enchantMap = new HashMap<>();
                for (Map.Entry<Enchantment, Integer> enchEntry : t.getEnchantMinLevels().entrySet()) {
                    enchantMap.put(enchEntry.getKey().getKey().getKey(), enchEntry.getValue());
                }
                if (!enchantMap.isEmpty()) {
                    config.set(base + ".enchant-filters", enchantMap);
                } else {
                    config.set(base + ".enchant-filters", null);
                }
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            logger.severe("[XLRHopper] 无法保存 data.yml: " + e.getMessage());
        }
    }

    private void savePlayerBoxes(FileConfiguration config, String path, UUID uuid, PlayerBoxManager boxManager) {
        Map<String, ItemStack[]> boxes = boxManager.getAllPlayerBoxes().get(uuid);
        if (boxes == null || boxes.isEmpty()) {
            return;
        }
        String boxesPath = path + ".boxes";
        ConfigurationSection existing = config.getConfigurationSection(boxesPath);
        if (existing != null) {
            for (String oldName : existing.getKeys(false)) {
                if (!boxes.containsKey(oldName)) {
                    config.set(boxesPath + "." + oldName, null);
                }
            }
        }
        for (Map.Entry<String, ItemStack[]> boxEntry : boxes.entrySet()) {
            String boxPath = boxesPath + "." + boxEntry.getKey();
            ItemStack[] contents = boxEntry.getValue();
            boolean hasItem = false;
            if (contents != null) {
                for (int i = 0; i < PlayerBoxManager.BOX_CAPACITY; i++) {
                    ItemStack stack = contents[i];
                    if (stack != null && !stack.getType().isAir()) {
                        config.set(boxPath + "." + i, stack);
                        hasItem = true;
                    } else {
                        config.set(boxPath + "." + i, null);
                    }
                }
            }
            if (!hasItem) {
                config.set(boxPath + "." + BOX_EMPTY_MARKER, true);
            } else {
                config.set(boxPath + "." + BOX_EMPTY_MARKER, null);
            }
        }
    }
}
