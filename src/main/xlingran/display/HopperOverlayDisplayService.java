package xlingran.display;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
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
import xlingran.gui.HopperLevelDef;
import xlingran.gui.TextPlaceholders;
import xlingran.gui.UpdateConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 漏斗上方悬浮信息：DecentHolograms DHAPI（非持久化全息）。
 */
public final class HopperOverlayDisplayService {

    private static final float HOLOGRAM_Y_OFFSET = 1.3f;
    private static final long REFRESH_DEBOUNCE_TICKS = 4L;

    private final Shan plugin;
    private final HopperKeys keys;
    private final HopperTemplateManager templateManager;
    private final UpdateConfig updateConfig;
    private final boolean decentHologramsAvailable;
    private final Map<String, String> signaturesByLocation = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> debouncedRefresh = new ConcurrentHashMap<>();

    public HopperOverlayDisplayService(Shan plugin, HopperKeys keys, HopperTemplateManager templateManager,
                                       UpdateConfig updateConfig, boolean decentHologramsAvailable) {
        this.plugin = plugin;
        this.keys = keys;
        this.templateManager = templateManager;
        this.updateConfig = updateConfig;
        this.decentHologramsAvailable = decentHologramsAvailable;
    }

    public boolean isAvailable() {
        return decentHologramsAvailable;
    }

    public void refreshDebounced(Block block) {
        if (!decentHologramsAvailable || block == null || block.getType() != Material.HOPPER) {
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
        }, REFRESH_DEBOUNCE_TICKS);
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
        if (!decentHologramsAvailable || block == null || block.getType() != Material.HOPPER) {
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

        List<ItemStack> items = collectHopperItems(block);
        List<String> textLines = buildOverlayLines(block, template, config);
        String signature = contentSignature(textLines, items);
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
        if (hologram == null) {
            DHAPI.createHologram(name, holoLoc, false, List.of(" "));
            hologram = DHAPI.getHologram(name);
        } else {
            hologram.setLocation(holoLoc);
            if (!hologram.isEnabled()) {
                hologram.showAll();
            }
        }
        if (hologram == null) {
            return;
        }
        replaceLines(hologram, items, textLines);
        signaturesByLocation.put(locKey, signature);
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
        return block != null && block.getType() == Material.HOPPER
                && HopperBlockConfig.read(block, keys).isHoverDisplay();
    }

    private void replaceLines(Hologram hologram, List<ItemStack> items, List<String> textLines) {
        HologramPage page = DHAPI.getHologramPage(hologram, 0);
        if (page != null) {
            while (page.size() > 0) {
                DHAPI.removeHologramLine(hologram, 0);
                page = DHAPI.getHologramPage(hologram, 0);
                if (page == null) {
                    break;
                }
            }
        }
        for (ItemStack stack : items) {
            DHAPI.addHologramLine(hologram, singleDisplayStack(stack));
        }
        for (String line : textLines) {
            if (line != null && !line.isEmpty()) {
                DHAPI.addHologramLine(hologram, line);
            }
        }
        hologram.realignLines();
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

    private static Location hologramLocation(Block block) {
        return block.getLocation().add(0.5, HOLOGRAM_Y_OFFSET, 0.5);
    }

    private static String hologramName(Location loc) {
        return "xlrhopper_" + loc.getWorld().getUID() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_"
                + loc.getBlockZ();
    }

    private static String contentSignature(List<String> lines, List<ItemStack> items) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir()) {
                sb.append("air;");
            } else {
                sb.append(stack.getType().name()).append(':').append(stack.getAmount()).append(';');
            }
        }
        return sb.toString();
    }

    private List<ItemStack> collectHopperItems(Block block) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = HopperContainerUtil.getContainerInventory(block);
        if (inv == null) {
            return out;
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && !stack.getType().isAir()) {
                out.add(stack);
            }
        }
        return out;
    }

    private static ItemStack singleDisplayStack(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one;
    }

    private List<String> buildOverlayLines(Block block, HopperTemplate template, HopperBlockConfig config) {
        List<String> lines = new ArrayList<>(5);
        HopperLevelDef levelDef = HopperLevelResolver.resolveForBlock(block, keys, updateConfig);
        String levelName = levelDef != null ? levelDef.displayName() : "&7默认漏斗";
        lines.add(TextPlaceholders.apply("&a漏斗等级: %name%", Map.of("name", levelName)));

        String templateName = readTemplateName(block);
        if (templateName == null) {
            templateName = "?";
        }
        lines.add(TextPlaceholders.color("&a当前使用模板: &e" + templateName));

        boolean whitelist = HopperBlockConfig.getEffectiveWhitelist(block, keys, template);
        lines.add(TextPlaceholders.color("&7模式：" + (whitelist ? "白名单" : "黑名单")));

        int enchantCount = template.getEnchantMinLevels().size();
        lines.add(TextPlaceholders.color("&7过滤: " + enchantCount + " 种附魔"));

        Integer dur = template.getDurabilityThreshold();
        if (dur != null) {
            lines.add(TextPlaceholders.color("&7最低耐久度: &a" + dur));
        } else {
            lines.add(TextPlaceholders.color("&7最低耐久度: &7未设置"));
        }
        return lines;
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
