package xlingran;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 读取 plugins/XLRHopper/config.yml；reload 时由 Shan 调用 {@link #reload()}。
 */
public final class XLRHopperConfig {

    private final JavaPlugin plugin;

    private String configVersion = "1.3.0";
    private boolean checkUpdateEnabled = true;
    private Set<String> enabledWorlds = Collections.emptySet();

    private int maxQueueLimit = 5000;
    private int perTickMaxProcess = 256;
    private boolean sleepEmptyHopper = true;
    private boolean sleepFullContainer = true;
    private int sleepTick = 16;
    private int deepSleepTick = 80;
    private boolean clearOnChunkUnload = true;
    private int maxQueueSize = 8192;
    private boolean queueOverflowRetry = true;

    private boolean hologramEnabled = true;
    private int hologramRefreshTimeSeconds = 1;
    private double hologramLineHeight = 0.3;
    private double hologramHeight = 1.0;
    private double hologramUpdateRange = 48.0;
    private double hologramDisplayRange = 48.0;
    private List<String> hologramLines = defaultHologramLines();

    private boolean autoCraftEnabled = true;
    private boolean autoSmeltEnabled = true;
    private int craftTick = 20;
    private int smeltTick = 100;
    private boolean destroyUnmatchedEnabled = true;
    private boolean filterEnchanEnabled = true;
    private boolean filterDurabilityEnabled = true;
    private boolean batchSetEnabled = true;
    private boolean redstoneToggleEnabled = true;
    private boolean reverseHopperEnabled = true;

    private int maxTemplateCount = 27;
    private int guiClickCooldownMs = 200;
    private boolean explosionHopperProtection = true;

    private List<String> helpLines = Collections.emptyList();
    private List<String> reloadLines = defaultReloadLines();

    public XLRHopperConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        configVersion = cfg.getString("Version", "1.3.0");
        checkUpdateEnabled = cfg.getBoolean("check-upddate", true);

        List<String> worlds = cfg.getStringList("World");
        if (worlds == null || worlds.isEmpty()) {
            enabledWorlds = Collections.emptySet();
        } else {
            enabledWorlds = new HashSet<>(worlds);
        }

        maxQueueLimit = cfg.getInt("XLRHopper.max-queue-limit", 5000);
        perTickMaxProcess = cfg.getInt("XLRHopper.per-tick-max-process", 256);
        sleepEmptyHopper = cfg.getBoolean("XLRHopper.sleep-empty-hopper", true);
        sleepFullContainer = cfg.getBoolean("XLRHopper.sleep-full-container", true);
        sleepTick = cfg.getInt("XLRHopper.sleep-tick", 16);
        deepSleepTick = cfg.getInt("XLRHopper.deep-sleep-tick", 80);
        clearOnChunkUnload = cfg.getBoolean("XLRHopper.clear-on-chunk-unload", true);
        maxQueueSize = cfg.getInt("XLRHopper.max-queue-size", 8192);
        queueOverflowRetry = cfg.getBoolean("XLRHopper.queue-overflow-policy", true);

        hologramEnabled = cfg.getBoolean("Hologram.enable", true);
        hologramRefreshTimeSeconds = cfg.getInt("Hologram.refresh-time", 1);
        hologramLineHeight = cfg.getDouble("Hologram.line-height", 0.3);
        hologramHeight = cfg.getDouble("Hologram.height", 1.0);
        hologramUpdateRange = cfg.getDouble("Hologram.update-range", 48.0);
        hologramDisplayRange = cfg.getDouble("Hologram.display-range", 48.0);
        List<String> lines = cfg.getStringList("Hologram.hologram-lines");
        hologramLines = lines == null || lines.isEmpty() ? defaultHologramLines() : new ArrayList<>(lines);

        autoCraftEnabled = cfg.getBoolean("Gui.auto-craft-enable", true);
        autoSmeltEnabled = cfg.getBoolean("Gui.auto-smelt-enable", true);
        craftTick = cfg.getInt("Gui.craft-tick", 20);
        smeltTick = cfg.getInt("Gui.smelt-tick", 100);
        destroyUnmatchedEnabled = cfg.getBoolean("Gui.destroy-unmatched-items", true);
        filterEnchanEnabled = cfg.getBoolean("Gui.filter-enchan", true);
        filterDurabilityEnabled = cfg.getBoolean("Gui.filter-durability", true);
        batchSetEnabled = cfg.getBoolean("Gui.batch-set", true);
        redstoneToggleEnabled = cfg.getBoolean("Gui.redstone-toggle", true);
        reverseHopperEnabled = cfg.getBoolean("Gui.reverse-hopper", true);

        maxTemplateCount = Math.min(27, Math.max(1, cfg.getInt("Setting.max-template-count", 27)));
        guiClickCooldownMs = Math.max(0, cfg.getInt("Setting.gui-click-cooldown", 200));
        explosionHopperProtection = cfg.getBoolean("Setting.explosion-hopper", true);

        List<String> help = cfg.getStringList("Command.help");
        helpLines = help == null ? Collections.emptyList() : new ArrayList<>(help);
        List<String> reload = cfg.getStringList("Command.reload");
        reloadLines = reload == null || reload.isEmpty() ? defaultReloadLines() : new ArrayList<>(reload);
    }

    public boolean isPluginWorld(World world) {
        if (world == null || enabledWorlds.isEmpty()) {
            return false;
        }
        return enabledWorlds.contains(world.getName());
    }

    public boolean isPluginWorld(Block block) {
        return block != null && isPluginWorld(block.getWorld());
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public boolean isCheckUpdateEnabled() {
        return checkUpdateEnabled;
    }

    public int getMaxQueueLimit() {
        return maxQueueLimit;
    }

    public int getPerTickMaxProcess() {
        return perTickMaxProcess;
    }

    public boolean isSleepEmptyHopper() {
        return sleepEmptyHopper;
    }

    public boolean isSleepFullContainer() {
        return sleepFullContainer;
    }

    public int getSleepTick() {
        return sleepTick;
    }

    public int getDeepSleepTick() {
        return deepSleepTick;
    }

    public boolean isClearOnChunkUnload() {
        return clearOnChunkUnload;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public boolean isQueueOverflowRetry() {
        return queueOverflowRetry;
    }

    public boolean isHologramEnabled() {
        return hologramEnabled;
    }

    public int getHologramRefreshTimeSeconds() {
        return hologramRefreshTimeSeconds;
    }

    public long getHologramRefreshDebounceTicks() {
        return Math.max(1L, hologramRefreshTimeSeconds * 20L);
    }

    public double getHologramLineHeight() {
        return hologramLineHeight;
    }

    public double getHologramHeight() {
        return hologramHeight;
    }

    public double getHologramUpdateRange() {
        return hologramUpdateRange;
    }

    public double getHologramDisplayRange() {
        return hologramDisplayRange;
    }

    public List<String> getHologramLines() {
        return Collections.unmodifiableList(hologramLines);
    }

    public boolean isAutoCraftEnabled() {
        return autoCraftEnabled;
    }

    public boolean isAutoSmeltEnabled() {
        return autoSmeltEnabled;
    }

    public int getCraftTick() {
        return craftTick;
    }

    public int getSmeltTick() {
        return smeltTick;
    }

    public boolean isDestroyUnmatchedEnabled() {
        return destroyUnmatchedEnabled;
    }

    public boolean isFilterEnchanEnabled() {
        return filterEnchanEnabled;
    }

    public boolean isFilterDurabilityEnabled() {
        return filterDurabilityEnabled;
    }

    public boolean isBatchSetEnabled() {
        return batchSetEnabled;
    }

    public boolean isRedstoneToggleEnabled() {
        return redstoneToggleEnabled;
    }

    public boolean isReverseHopperEnabled() {
        return reverseHopperEnabled;
    }

    public int getMaxTemplateCount() {
        return maxTemplateCount;
    }

    public int getGuiClickCooldownMs() {
        return guiClickCooldownMs;
    }

    public boolean isExplosionHopperProtection() {
        return explosionHopperProtection;
    }

    public List<String> getHelpLines() {
        return Collections.unmodifiableList(helpLines);
    }

    public List<String> getReloadLines() {
        return Collections.unmodifiableList(reloadLines);
    }

    private static List<String> defaultHologramLines() {
        List<String> defaults = new ArrayList<>();
        defaults.add("%item1%%item2%%item3%%item4%%item5%");
        defaults.add("&a漏斗等级: %hoppername%");
        defaults.add("&a当前使用模板: %template%");
        defaults.add("&7模式: %mode%");
        defaults.add("&7过滤: %enchan% 种附魔");
        defaults.add("&7最低耐久度: %durability%");
        return defaults;
    }

    private static List<String> defaultReloadLines() {
        List<String> defaults = new ArrayList<>();
        defaults.add("&a配置已重载");
        defaults.add("&a加载了 %status% 种漏斗");
        return defaults;
    }
}
