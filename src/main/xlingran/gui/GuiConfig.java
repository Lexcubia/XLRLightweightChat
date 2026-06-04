package xlingran.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 读取预置 Gui.yml（不生成 YAML）。
 */
public final class GuiConfig {

    public static final int ROWS_TEMPLATE_LIST = 3;
    public static final int ROWS_TEMPLATE_SETTINGS = 5;
    public static final int ROWS_FILTER_ENCHANTS = 6;
    public static final int ROWS_HOPPER_SETTING = 3;
    public static final int MAX_STORAGE_ROWS = 6;

    private final JavaPlugin plugin;
    private final Logger logger;
    private YamlConfiguration config;
    private String toggleOn;
    private String toggleOff;
    private String filterModeOn;
    private String filterModeOff;
    private Map<String, String> enchantNames = Collections.emptyMap();

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "Gui.yml");
        if (!file.exists()) {
            plugin.saveResource("Gui.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        mergeDefaults();
        toggleOn = config.getString("toggle.on", "&a开");
        toggleOff = config.getString("toggle.off", "&c关");
        filterModeOn = config.getString("filtermode.on", "&a白名单模式");
        filterModeOff = config.getString("filtermode.off", "&c黑名单模式");
        enchantNames = loadEnchantNames();
    }

    public void reload() {
        load();
    }

    private void mergeDefaults() {
        try (InputStream in = plugin.getResource("Gui.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        } catch (Exception e) {
            logger.warning("[XLRHopper] 无法合并 jar 内 Gui.yml 默认值: " + e.getMessage());
        }
    }

    private Map<String, String> loadEnchantNames() {
        ConfigurationSection sec = config.getConfigurationSection("enchant-names");
        if (sec == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (String key : sec.getKeys(false)) {
            map.put(normalizeEnchantKey(key), sec.getString(key, key));
        }
        return map;
    }

    public static String normalizeEnchantKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public String color(String text) {
        return TextPlaceholders.color(text);
    }

    public String apply(String template, Map<String, String> vars) {
        return TextPlaceholders.apply(template, vars);
    }

    public String toggle(boolean on) {
        return color(on ? toggleOn : toggleOff);
    }

    public String filterMode(boolean whitelist) {
        return color(whitelist ? filterModeOn : filterModeOff);
    }

    public String getEnchantDisplayName(Enchantment enchant) {
        if (enchant == null) {
            return "";
        }
        String key = enchant.getKey().getKey();
        String zh = enchantNames.get(normalizeEnchantKey(key));
        if (zh != null && !zh.isEmpty()) {
            return zh;
        }
        return fallbackChinese(key);
    }

    private static String fallbackChinese(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    public String templateListTitle() {
        return color(config.getString("HopperTemplateList.name", "&6漏斗模板"));
    }

    public int templateListSize() {
        return ROWS_TEMPLATE_LIST * 9;
    }

    public String templateSettingsTitle(String templateName) {
        return apply(config.getString("TemplateSet.name", "&e模板设置: &b%modename%"),
                Map.of("modename", templateName != null ? templateName : ""));
    }

    public int templateSettingsSize() {
        return ROWS_TEMPLATE_SETTINGS * 9;
    }

    public String filterEnchantsTitle() {
        return color(config.getString("FilterEnchantGui.name", "&6过滤附魔属性"));
    }

    public int filterEnchantsSize() {
        return ROWS_FILTER_ENCHANTS * 9;
    }

    public String hopperSettingTitle() {
        return color(config.getString("HopperSetting.name", "&e漏斗设置"));
    }

    public int hopperSettingSize() {
        return ROWS_HOPPER_SETTING * 9;
    }

    public int storageSize(String key) {
        int rows = config.getInt(key + ".rows", 6);
        rows = Math.max(1, Math.min(MAX_STORAGE_ROWS, rows));
        return rows * 9;
    }

    public String storageTitle(String key) {
        return color(config.getString(key + ".name", key));
    }

    public Material fillerMaterial() {
        return resolveMaterial(config.getString("TemplateSet.Filler.material", "BLACK_STAINED_GLASS_PANE"),
                Material.BLACK_STAINED_GLASS_PANE);
    }

    public Material hopperSettingFiller() {
        return resolveMaterial(config.getString("HopperSetting.Filler.material", "BLACK_STAINED_GLASS_PANE"),
                Material.BLACK_STAINED_GLASS_PANE);
    }

    public GuiButtonDef templateListItem(boolean enabled) {
        String path = enabled ? "HopperTemplateList.SetTemplate" : "HopperTemplateList.Template";
        return readButton(path, Material.NAME_TAG, "&a%Template%",
                List.of("&7左键 开/关", "&7右键 编辑", "&a当前模式: %toggle%"), 0);
    }

    public GuiButtonDef templateButton(String key) {
        return readButton("TemplateSet." + key, Material.STONE, "&e" + key, List.of(), 0);
    }

    public int templateButtonSlot(String key, int fallback) {
        int slot = config.getInt("TemplateSet." + key + ".slot", fallback);
        return clampSlot(slot, ROWS_TEMPLATE_SETTINGS, fallback);
    }

    public GuiButtonDef hopperSettingButton(String key) {
        return readButton("HopperSetting." + key, Material.STONE, "&e" + key, List.of(), 0);
    }

    public int hopperSettingSlot(String key, int fallback) {
        int slot = config.getInt("HopperSetting." + key + ".slot", fallback);
        return clampSlot(slot, ROWS_HOPPER_SETTING, fallback);
    }

    public GuiButtonDef filterEnchantBook(boolean configured) {
        String path = configured ? "FilterEnchantGui.SetEnchant" : "FilterEnchantGui.Enchant";
        List<String> fallbackLore = configured
                ? List.of("&a当前过滤: &e%Enchant% %EnchantLevel%", "&7左键 修改等级", "&7右键 清除过滤")
                : List.of("&7左键 设置最低等级");
        return readButton(path, configured ? Material.ENCHANTED_BOOK : Material.BOOK,
                "&e%Enchant%", fallbackLore, 0);
    }

    public List<String> durabilityLore(boolean configured, Integer threshold) {
        List<String> lines = new ArrayList<>();
        if (configured && threshold != null) {
            for (String line : config.getStringList("TemplateSet.FilterDurability.SetLore")) {
                lines.add(apply(line, Map.of("Durability", String.valueOf(threshold))));
            }
        } else {
            for (String line : config.getStringList("TemplateSet.FilterDurability.Lore")) {
                lines.add(color(line));
            }
        }
        return lines;
    }

    public List<String> resolveLore(List<String> raw, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (String line : raw) {
            out.add(apply(line, vars));
        }
        return out;
    }

    private GuiButtonDef readButton(String path, Material fallbackMat, String fallbackName,
                                    List<String> fallbackLore, int fallbackSlot) {
        Material mat = resolveMaterial(config.getString(path + ".material"), fallbackMat);
        String name = config.getString(path + ".name", fallbackName);
        List<String> lore = config.getStringList(path + ".Lore");
        if (lore.isEmpty()) {
            lore = fallbackLore;
        }
        int slot = config.getInt(path + ".slot", fallbackSlot);
        boolean showEnchant = config.getBoolean(path + ".enchant", false);
        return new GuiButtonDef(mat, name, lore, slot, showEnchant);
    }

    private int clampSlot(int slot, int rows, int fallback) {
        int max = rows * 9 - 1;
        if (slot < 0 || slot > max) {
            logger.warning("[XLRHopper] Gui.yml slot " + slot + " 超出 " + rows + " 行，回退 " + fallback);
            return fallback;
        }
        return slot;
    }

    public Material resolveMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material mat = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (mat == null) {
            logger.warning("[XLRHopper] 未知材质: " + name + "，使用 " + fallback);
            return fallback;
        }
        return mat;
    }

    public record GuiButtonDef(Material material, String name, List<String> lore, int slot, boolean showEnchant) {
    }
}
