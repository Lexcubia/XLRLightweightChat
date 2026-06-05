package xlingran.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
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
    private YamlConfiguration jarDefaults = new YamlConfiguration();
    private Map<String, String> enchantNames = Collections.emptyMap();

    private String diskToggleOn;
    private String diskToggleOff;
    private String diskFilterModeOn;
    private String diskFilterModeOff;

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "Gui.yml");
        if (!file.exists()) {
            plugin.saveResource("Gui.yml", false);
        }
        jarDefaults = loadJarDefaults();
        YamlConfiguration loaded = loadUserFile(file);
        captureDiskTokens(loaded, file);
        applyDefaults(loaded, jarDefaults);
        config = loaded;
        enchantNames = loadEnchantNames();
        logLoadedTokens(file);
    }

    public void reload() {
        load();
    }

    private YamlConfiguration loadUserFile(File file) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            logger.warning("[XLRHopper] Gui.yml UTF-8 读取失败，回退系统编码: " + e.getMessage());
            return YamlConfiguration.loadConfiguration(file);
        }
    }

    private YamlConfiguration loadJarDefaults() {
        try (InputStream in = plugin.getResource("Gui.yml")) {
            if (in != null) {
                return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.warning("[XLRHopper] 无法读取 jar 内 Gui.yml: " + e.getMessage());
        }
        return new YamlConfiguration();
    }

    private void applyDefaults(YamlConfiguration target, YamlConfiguration defaults) {
        if (defaults.getKeys(true).isEmpty()) {
            logger.warning("[XLRHopper] jar 内 Gui.yml 为空，toggle/filtermode 仅能来自磁盘文件");
            return;
        }
        target.setDefaults(defaults);
        target.options().copyDefaults(true);
    }

    private void captureDiskTokens(YamlConfiguration loaded, File file) {
        diskToggleOn = readTokenFromSection(loaded, "toggle", "on");
        diskToggleOff = readTokenFromSection(loaded, "toggle", "off");
        diskFilterModeOn = readTokenFromSection(loaded, "filtermode", "on");
        diskFilterModeOff = readTokenFromSection(loaded, "filtermode", "off");

        if (diskToggleOn == null) {
            diskToggleOn = findMisnestedToken(loaded, "toggle", "on");
        }
        if (diskToggleOff == null) {
            diskToggleOff = findMisnestedToken(loaded, "toggle", "off");
        }
        if (diskFilterModeOn == null) {
            diskFilterModeOn = findMisnestedToken(loaded, "filtermode", "on");
        }
        if (diskFilterModeOff == null) {
            diskFilterModeOff = findMisnestedToken(loaded, "filtermode", "off");
        }

        diagnoseSection(loaded, file, "toggle", diskToggleOn);
        diagnoseSection(loaded, file, "filtermode", diskFilterModeOn);
    }

    private void diagnoseSection(YamlConfiguration loaded, File file, String sectionName, String diskValue) {
        Object node = loaded.get(sectionName);
        if (node == null && diskValue == null) {
            logger.warning("[XLRHopper] Gui.yml 缺少根节点 " + sectionName + "（须与 TemplateSet 同级顶格）: "
                    + file.getAbsolutePath());
            return;
        }
        if (node instanceof String) {
            logger.warning("[XLRHopper] Gui.yml 节点 " + sectionName + " 被解析为字符串「" + node
                    + "」而非节；请改为:\n" + sectionName + ":\n    \"on\": \"&a启\"");
            return;
        }
        if (diskValue == null) {
            ConfigurationSection section = loaded.getConfigurationSection(sectionName);
            if (section != null) {
                logger.warning("[XLRHopper] Gui.yml 存在 " + sectionName + " 节但读不到 on/off，子键="
                        + section.getKeys(false) + "；建议对 on/off 加引号: \"on\": \"&a启\"");
            }
        }
    }

    private String findMisnestedToken(YamlConfiguration loaded, String sectionName, String childKey) {
        String suffix = sectionName + "." + childKey;
        for (String key : loaded.getKeys(true)) {
            if (!key.endsWith(suffix) || key.equals(suffix)) {
                continue;
            }
            String raw = readTokenFromSection(loaded, key.substring(0, key.length() - childKey.length() - 1),
                    childKey);
            if (raw == null) {
                raw = loaded.getString(key);
            }
            if (isPresent(raw)) {
                logger.warning("[XLRHopper] Gui.yml 发现 " + suffix + " 位于非根路径 " + key
                        + "，请移到根节点与 TemplateSet 同级");
                return raw;
            }
        }
        return null;
    }

    private static String readTokenFromSection(YamlConfiguration cfg, String sectionName, String childKey) {
        ConfigurationSection section = cfg.getConfigurationSection(sectionName);
        if (section == null) {
            return null;
        }
        String raw = section.getString(childKey);
        if (isPresent(raw)) {
            return raw;
        }
        for (String key : section.getKeys(false)) {
            if (!normalizeKey(key).equalsIgnoreCase(childKey)) {
                continue;
            }
            raw = section.getString(key);
            if (isPresent(raw)) {
                return raw;
            }
            Object val = section.get(key);
            if (val != null && !(val instanceof ConfigurationSection)) {
                String text = val.toString();
                if (isPresent(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.replace("\"", "").trim();
    }

    private static boolean isPresent(String raw) {
        return raw != null && !raw.isEmpty();
    }

    private void logLoadedTokens(File file) {
        String jarOn = readTokenFromSection(jarDefaults, "toggle", "on");
        logger.info("[XLRHopper] Gui.yml: " + file.getAbsolutePath()
                + " | disk: toggle.on=" + diskToggleOn
                + " | merged: toggle.on=" + readTokenFromSection(config, "toggle", "on")
                + " | jar: toggle.on=" + jarOn
                + " | filtermode.on=" + resolveToken("filtermode", "on", diskFilterModeOn, "&a1名单模式"));
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
        String child = on ? "on" : "off";
        String disk = on ? diskToggleOn : diskToggleOff;
        String builtin = on ? "&a启" : "&c关";
        return color(resolveToken("toggle", child, disk, builtin));
    }

    public String filterMode(boolean whitelist) {
        String child = whitelist ? "on" : "off";
        String disk = whitelist ? diskFilterModeOn : diskFilterModeOff;
        String builtin = whitelist ? "&a1名单模式" : "&c2名单模式";
        return color(resolveToken("filtermode", child, disk, builtin));
    }

    private String resolveToken(String sectionName, String childKey, String diskValue, String builtinFallback) {
        if (isPresent(diskValue)) {
            return diskValue;
        }
        String raw = readTokenFromSection(config, sectionName, childKey);
        if (isPresent(raw)) {
            return raw;
        }
        raw = readTokenFromSection(jarDefaults, sectionName, childKey);
        if (isPresent(raw)) {
            return raw;
        }
        return builtinFallback;
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
