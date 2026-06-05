package xlingran.display;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.api.holograms.HologramLine;
import eu.decentsoftware.holograms.api.holograms.HologramPage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import xlingran.HopperBlockConfig;
import xlingran.HopperContainerUtil;
import xlingran.HopperKeys;
import xlingran.HopperLevelResolver;
import xlingran.HopperTemplate;
import xlingran.HopperTemplateManager;
import xlingran.HopperTemplateResolver;
import xlingran.Shan;
import xlingran.XLRHopperConfig;
import xlingran.gui.HopperLevelDef;
import xlingran.gui.TextPlaceholders;
import xlingran.gui.UpdateConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 漏斗上方悬浮信息：DecentHolograms DHAPI（非持久化全息）。
 */
public final class HopperOverlayDisplayService {

    private static final double ITEM_ROW_SPACING = 0.38;
    private static final double ITEM_ROW_HEIGHT = 0.28;
    private static final double ITEM_TEXT_GAP = 0.18;
    private static final Pattern ITEM_SLOT_PATTERN = Pattern.compile("%item([1-5])%");

    private final Shan plugin;
    private final HopperKeys keys;
    private final HopperTemplateManager templateManager;
    private final UpdateConfig updateConfig;
    private final XLRHopperConfig pluginConfig;
    private final boolean decentHologramsAvailable;
    private final Map<String, String> signaturesByLocation = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> debouncedRefresh = new ConcurrentHashMap<>();

    public HopperOverlayDisplayService(Shan plugin, HopperKeys keys, HopperTemplateManager templateManager,
                                       UpdateConfig updateConfig, XLRHopperConfig pluginConfig,
                                       boolean decentHologramsAvailable) {
        this.plugin = plugin;
        this.keys = keys;
        this.templateManager = templateManager;
        this.updateConfig = updateConfig;
        this.pluginConfig = pluginConfig;
        this.decentHologramsAvailable = decentHologramsAvailable;
    }

    public boolean isAvailable() {
        return decentHologramsAvailable;
    }

    public void refreshDebounced(Block block) {
        if (!canShowHologram(block)) {
            return;
        }
        String locKey = locationKey(block.getLocation());
        BukkitTask pending = debouncedRefresh.remove(locKey);
        if (pending != null) {
            pending.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            debouncedRefresh.remove(locKey);
            refresh(block);
        }, pluginConfig.getHologramRefreshDebounceTicks());
        debouncedRefresh.put(locKey, task);
    }

    public void cancelPendingRefresh(Block block) {
        if (block == null) {
            return;
        }
        BukkitTask pending = debouncedRefresh.remove(locationKey(block.getLocation()));
        if (pending != null) {
            pending.cancel();
        }
    }

    public void show(Block block) {
        refresh(block);
    }

    public void hide(Block block) {
        if (block == null) {
            return;
        }
        cancelPendingRefresh(block);
        String locKey = locationKey(block.getLocation());
        signaturesByLocation.remove(locKey);
        destroyHologram(hologramName(block.getLocation()));
    }

    public void refresh(Block block) {
        if (!canShowHologram(block)) {
            return;
        }
        HopperBlockConfig config = HopperBlockConfig.read(block, keys);
        if (!config.isHoverDisplay()) {
            hide(block);
            return;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(block, keys, templateManager);
        if (template == null) {
            hide(block);
            return;
        }

        List<HologramSegment> layout = parseHologramLayout(block, template, config);
        String signature = contentSignature(block, layout);
        String locKey = locationKey(block.getLocation());
        if (signature.equals(signaturesByLocation.get(locKey))) {
            Hologram existing = DHAPI.getHologram(hologramName(block.getLocation()));
            if (existing != null && existing.isEnabled()) {
                return;
            }
        }

        Location holoLoc = hologramLocation(block);
        String name = hologramName(block.getLocation());
        Hologram hologram = DHAPI.getHologram(name);
        boolean created = false;
        if (hologram == null) {
            DHAPI.createHologram(name, holoLoc, false, List.of(" "));
            hologram = DHAPI.getHologram(name);
            created = true;
        }
        if (hologram == null) {
            return;
        }
        hologram.setAlwaysFacePlayer(true);
        hologram.setDisplayRange((int) pluginConfig.getHologramDisplayRange());
        hologram.setUpdateRange((int) pluginConfig.getHologramUpdateRange());
        if (!created && locationChanged(hologram, holoLoc)) {
            hologram.setLocation(holoLoc);
        }
        if (!hologram.isEnabled()) {
            hologram.showAll();
        }

        syncLines(hologram, block, layout);
        signaturesByLocation.put(locKey, signature);
    }

    public void restoreAllAfterReload() {
        if (!decentHologramsAvailable) {
            return;
        }
        for (String locKey : new ArrayList<>(signaturesByLocation.keySet())) {
            Location loc = parseLocationKey(locKey);
            if (loc == null) {
                continue;
            }
            Block block = loc.getBlock();
            if (block.getType() == Material.HOPPER && isHoverEnabled(block)) {
                signaturesByLocation.remove(locKey);
                refresh(block);
            } else {
                signaturesByLocation.remove(locKey);
            }
        }
    }

    public void hideAllInChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        for (String locKey : new ArrayList<>(signaturesByLocation.keySet())) {
            Location loc = parseLocationKey(locKey);
            if (loc == null || loc.getWorld() == null) {
                continue;
            }
            if (loc.getWorld().equals(chunk.getWorld())
                    && (loc.getBlockX() >> 4) == chunk.getX()
                    && (loc.getBlockZ() >> 4) == chunk.getZ()) {
                Block block = loc.getBlock();
                if (block.getType() == Material.HOPPER) {
                    hide(block);
                } else {
                    signaturesByLocation.remove(locKey);
                    destroyHologram(hologramName(loc));
                }
            }
        }
    }

    public void hideAll() {
        for (BukkitTask task : debouncedRefresh.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        debouncedRefresh.clear();
        for (String locKey : new ArrayList<>(signaturesByLocation.keySet())) {
            Location loc = parseLocationKey(locKey);
            if (loc != null) {
                destroyHologram(hologramName(loc));
            }
        }
        signaturesByLocation.clear();
    }

    public boolean isHoverEnabled(Block block) {
        return canShowHologram(block) && HopperBlockConfig.read(block, keys).isHoverDisplay();
    }

    private boolean canShowHologram(Block block) {
        return decentHologramsAvailable && pluginConfig.isHologramEnabled()
                && pluginConfig.isPluginWorld(block)
                && block != null && block.getType() == Material.HOPPER;
    }

    private void syncLines(Hologram hologram, Block block, List<HologramSegment> layout) {
        HologramPage page = DHAPI.getHologramPage(hologram, 0);
        if (page == null) {
            return;
        }
        Inventory inv = HopperContainerUtil.getContainerInventory(block);
        double lineHeight = pluginConfig.getHologramLineHeight();
        int lineIndex = 0;
        boolean firstItemRow = true;

        for (HologramSegment segment : layout) {
            if (segment instanceof ItemSlotRow itemRow) {
                List<Integer> slots = itemRow.slotIndices();
                for (int i = 0; i < slots.size(); i++) {
                    int slotIndex = slots.get(i);
                    boolean leadSlot = firstItemRow && i == 0;
                    ItemStack stack = inv != null ? inv.getItem(slotIndex) : null;
                    if (stack == null || stack.getType().isAir()) {
                        setHologramLineAt(hologram, lineIndex, " ");
                    } else {
                        setHologramLineAt(hologram, lineIndex, singleDisplayStack(stack));
                    }
                    page = DHAPI.getHologramPage(hologram, 0);
                    if (page != null && lineIndex < page.size()) {
                        applyItemSlotLayout(DHAPI.getHologramLine(page, lineIndex), slotIndex, leadSlot);
                    }
                    lineIndex++;
                }
                firstItemRow = false;
            } else if (segment instanceof TextLine textLine) {
                String line = textLine.text();
                if (line == null || line.isEmpty()) {
                    continue;
                }
                setHologramLineAt(hologram, lineIndex, line);
                page = DHAPI.getHologramPage(hologram, 0);
                if (page != null && lineIndex < page.size()) {
                    applyTextLineLayout(DHAPI.getHologramLine(page, lineIndex), lineHeight);
                }
                lineIndex++;
            }
        }

        page = DHAPI.getHologramPage(hologram, 0);
        if (page == null) {
            return;
        }
        while (page.size() > lineIndex) {
            DHAPI.removeHologramLine(hologram, page.size() - 1);
            page = DHAPI.getHologramPage(hologram, 0);
            if (page == null) {
                return;
            }
        }

        hologram.realignLines();
    }

    private static void setHologramLineAt(Hologram hologram, int lineIndex, String content) {
        HologramPage page = DHAPI.getHologramPage(hologram, 0);
        if (page == null) {
            return;
        }
        if (lineIndex < page.size()) {
            DHAPI.setHologramLine(hologram, lineIndex, content);
        } else {
            DHAPI.addHologramLine(hologram, content);
        }
    }

    private static void setHologramLineAt(Hologram hologram, int lineIndex, ItemStack content) {
        HologramPage page = DHAPI.getHologramPage(hologram, 0);
        if (page == null) {
            return;
        }
        if (lineIndex < page.size()) {
            DHAPI.setHologramLine(hologram, lineIndex, content);
        } else {
            DHAPI.addHologramLine(hologram, content);
        }
    }

    private static void applyItemSlotLayout(HologramLine line, int slotIndex, boolean leadSlot) {
        if (line == null) {
            return;
        }
        if (leadSlot) {
            line.setHeight(ITEM_ROW_HEIGHT + ITEM_TEXT_GAP);
        } else {
            line.setHeight(0);
        }
        line.setOffsetX((slotIndex - 2) * ITEM_ROW_SPACING);
        line.setOffsetY(0);
        line.setOffsetZ(0);
        line.update();
    }

    private static void applyTextLineLayout(HologramLine line, double lineHeight) {
        if (line == null) {
            return;
        }
        line.setHeight(lineHeight);
        line.setOffsetX(0);
        line.setOffsetY(0);
        line.setOffsetZ(0);
        line.update();
    }

    private static boolean locationChanged(Hologram hologram, Location target) {
        Location current = hologram.getLocation();
        if (current == null || target == null || current.getWorld() == null || target.getWorld() == null) {
            return true;
        }
        return !current.getWorld().equals(target.getWorld())
                || current.getBlockX() != target.getBlockX()
                || current.getBlockY() != target.getBlockY()
                || current.getBlockZ() != target.getBlockZ()
                || Math.abs(current.getY() - target.getY()) > 0.01
                || Math.abs(current.getX() - (target.getX())) > 0.01
                || Math.abs(current.getZ() - (target.getZ())) > 0.01;
    }

    private void destroyHologram(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        Hologram hologram = DHAPI.getHologram(name);
        if (hologram != null) {
            hologram.destroy();
        }
    }

    private Location hologramLocation(Block block) {
        return block.getLocation().add(0.5, pluginConfig.getHologramHeight(), 0.5);
    }

    private static String hologramName(Location loc) {
        return "xlrhopper_" + loc.getWorld().getUID() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_"
                + loc.getBlockZ();
    }

    private static String contentSignature(Block block, List<HologramSegment> layout) {
        StringBuilder sb = new StringBuilder();
        Inventory inv = HopperContainerUtil.getContainerInventory(block);
        for (HologramSegment segment : layout) {
            if (segment instanceof ItemSlotRow itemRow) {
                for (int slotIndex : itemRow.slotIndices()) {
                    ItemStack stack = inv != null ? inv.getItem(slotIndex) : null;
                    if (stack == null || stack.getType().isAir()) {
                        sb.append("air;");
                    } else {
                        sb.append(stack.getType().name()).append(';');
                    }
                }
            } else if (segment instanceof TextLine textLine) {
                sb.append(textLine.text()).append('\n');
            }
        }
        return sb.toString();
    }

    private static ItemStack singleDisplayStack(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one;
    }

    private List<HologramSegment> parseHologramLayout(Block block, HopperTemplate template, HopperBlockConfig config) {
        HopperLevelDef levelDef = HopperLevelResolver.resolveForBlock(block, keys, updateConfig);
        String hopperName = levelDef != null ? levelDef.displayName() : "&7默认漏斗";
        String templateName = readTemplateName(block);
        if (templateName == null) {
            templateName = "?";
        }
        boolean whitelist = HopperBlockConfig.getEffectiveWhitelist(block, keys, template);
        String mode = whitelist ? "白名单" : "黑名单";
        int enchantCount = template.getEnchantMinLevels().size();
        Integer dur = template.getDurabilityThreshold();
        String durability = dur != null ? String.valueOf(dur) : "未设置";

        Map<String, String> vars = Map.of(
                "hoppername", hopperName,
                "template", templateName,
                "mode", mode,
                "enchan", String.valueOf(enchantCount),
                "durability", durability);

        List<HologramSegment> layout = new ArrayList<>();
        for (String raw : pluginConfig.getHologramLines()) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            if (ITEM_SLOT_PATTERN.matcher(raw).find()) {
                List<Integer> slots = parseItemSlotIndices(raw);
                if (!slots.isEmpty()) {
                    layout.add(new ItemSlotRow(slots));
                }
            } else {
                layout.add(new TextLine(TextPlaceholders.apply(raw, vars)));
            }
        }
        return layout;
    }

    private static List<Integer> parseItemSlotIndices(String raw) {
        List<Integer> slots = new ArrayList<>();
        Matcher matcher = ITEM_SLOT_PATTERN.matcher(raw);
        while (matcher.find()) {
            slots.add(Integer.parseInt(matcher.group(1)) - 1);
        }
        return slots;
    }

    private sealed interface HologramSegment permits ItemSlotRow, TextLine {
    }

    private record ItemSlotRow(List<Integer> slotIndices) implements HologramSegment {
    }

    private record TextLine(String text) implements HologramSegment {
    }

    private String readTemplateName(Block block) {
        if (!(block.getState() instanceof org.bukkit.block.TileState tileState)) {
            return null;
        }
        return tileState.getPersistentDataContainer().get(keys.template, PersistentDataType.STRING);
    }

    private static String locationKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static Location parseLocationKey(String key) {
        int colon = key.indexOf(':');
        if (colon < 0) {
            return null;
        }
        try {
            UUID worldId = UUID.fromString(key.substring(0, colon));
            String[] parts = key.substring(colon + 1).split(",");
            if (parts.length != 3) {
                return null;
            }
            org.bukkit.World world = Bukkit.getWorld(worldId);
            if (world == null) {
                return null;
            }
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}
