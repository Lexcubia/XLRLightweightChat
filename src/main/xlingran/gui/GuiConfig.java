package xlingran.gui;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import xlingran.HopperBlockConfig;
import xlingran.HopperKeys;
import xlingran.HopperTemplate;
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

    private static final String KEY_TOGGLE_ON = "toggleon";
    private static final String KEY_TOGGLE_OFF = "toggleoff";
    private static final String KEY_FILTERMODE_ON = "filtermodeon";
    private static final String KEY_FILTERMODE_OFF = "filtermodeoff";
    private static final String KEY_STONEMODE_ON = "stonemodeon";
    private static final String KEY_STONEMODE_OFF = "stonemodeoff";
    private static final String KEY_STONEMODE_DISABLED = "stonemodeodisabled";

    private final JavaPlugin plugin;
    private final Logger logger;
    private YamlConfiguration config;
    private YamlConfiguration jarDefaults = new YamlConfiguration();
    private Map<String, String> enchantNames = Collections.emptyMap();

    private String diskToggleOn;
    private String diskToggleOff;
    private String diskFilterModeOn;
    private String diskFilterModeOff;
    private String diskStoneModeOn;
    private String diskStoneModeOff;
    private String diskStoneModeDisabled;

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
        diskToggleOn = readFlatToken(loaded, KEY_TOGGLE_ON);
        diskToggleOff = readFlatToken(loaded, KEY_TOGGLE_OFF);
        diskFilterModeOn = readFlatToken(loaded, KEY_FILTERMODE_ON);
        diskFilterModeOff = readFlatToken(loaded, KEY_FILTERMODE_OFF);
        diskStoneModeOn = readFlatToken(loaded, KEY_STONEMODE_ON);
        diskStoneModeOff = readFlatToken(loaded, KEY_STONEMODE_OFF);
        diskStoneModeDisabled = readFlatToken(loaded, KEY_STONEMODE_DISABLED);

        if (!isPresent(diskToggleOn) && !isPresent(diskToggleOff)
                && !isPresent(diskFilterModeOn) && !isPresent(diskFilterModeOff)) {
            logger.warning("[XLRHopper] Gui.yml 缺少 toggleon/toggleoff/filtermodeon/filtermodeoff（根节点顶格）: "
                    + file.getAbsolutePath());
        }
    }

    private static String readFlatToken(YamlConfiguration cfg, String key) {
        if (cfg == null) {
            return null;
        }
        String raw = cfg.getString(key);
        return isPresent(raw) ? raw : null;
    }

    private static boolean isPresent(String raw) {
        return raw != null && !raw.isEmpty();
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
        String key = on ? KEY_TOGGLE_ON : KEY_TOGGLE_OFF;
        String disk = on ? diskToggleOn : diskToggleOff;
        String builtin = on ? "&a开启" : "&c关闭";
        return color(resolveFlatToken(key, disk, builtin));
    }

    public String filterMode(boolean whitelist) {
        String key = whitelist ? KEY_FILTERMODE_ON : KEY_FILTERMODE_OFF;
        String disk = whitelist ? diskFilterModeOn : diskFilterModeOff;
        String builtin = whitelist ? "&a3名单模式" : "&c4名单模式";
        return color(resolveFlatToken(key, disk, builtin));
    }

    public String stoneMode(boolean whitelist) {
        String key = whitelist ? KEY_STONEMODE_ON : KEY_STONEMODE_OFF;
        String disk = whitelist ? diskStoneModeOn : diskStoneModeOff;
        String builtin = whitelist ? "&a红石白名单模式" : "&c红石黑名单模式";
        return color(resolveFlatToken(key, disk, builtin));
    }

    public String stoneModeDisabled() {
        return color(resolveFlatToken(KEY_STONEMODE_DISABLED, diskStoneModeDisabled, "&6功能已关闭"));
    }

    public String displayMode(Block hopper, HopperTemplate template, HopperKeys keys) {
        if (hopper == null || template == null || keys == null) {
            return filterMode(true);
        }
        boolean effectiveWhitelist = HopperBlockConfig.getEffectiveWhitelist(hopper, keys, template);
        if (HopperBlockConfig.read(hopper, keys).isRedstoneListToggle()) {
            return stoneMode(effectiveWhitelist);
        }
        return filterMode(effectiveWhitelist);
    }

    private String resolveFlatToken(String flatKey, String diskValue, String builtinFallback) {
        if (isPresent(diskValue)) {
            return diskValue;
        }
        String raw = readFlatToken(config, flatKey);
        if (isPresent(raw)) {
            return raw;
        }
        raw = readFlatToken(jarDefaults, flatKey);
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
