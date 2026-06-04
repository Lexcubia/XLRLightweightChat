package xlingran.display;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
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
 * 漏斗上方悬浮信息：TextDisplay + ItemDisplay（文案与布局硬编码，不读 Message.yml）。
 */
public final class HopperOverlayDisplayService {

    private static final float ITEM_ROW_Y = 1.55f;
    private static final float LINE_START_Y = 1.28f;
    private static final float LINE_SPACING = 0.22f;
    private static final float ITEM_SPACING = 0.32f;
    private static final float TEXT_DISPLAY_SCALE = 0.45f;
    private static final float ITEM_DISPLAY_SCALE = 0.8f;
    private static final float VIEW_RANGE = 32f;
    private static final long REFRESH_DEBOUNCE_TICKS = 4L;

    private final Shan plugin;
    private final HopperKeys keys;
    private final HopperTemplateManager templateManager;
    private final UpdateConfig updateConfig;
    private final Map<String, ActiveOverlay> activeByLocation = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> debouncedRefresh = new ConcurrentHashMap<>();

    public HopperOverlayDisplayService(Shan plugin, HopperKeys keys, HopperTemplateManager templateManager,
                                       UpdateConfig updateConfig) {
        this.plugin = plugin;
        this.keys = keys;
        this.templateManager = templateManager;
        this.updateConfig = updateConfig;
    }

    public void refreshDebounced(Block block) {
        if (block == null || block.getType() != Material.HOPPER) {
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
        ActiveOverlay active = activeByLocation.remove(locKey);
        if (active == null) {
            return;
        }
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        removeOverlayEntities(active, world);
    }

    public void refresh(Block block) {
        if (block == null || block.getType() != Material.HOPPER) {
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
        List<String> lines = buildOverlayLines(block, template, config);
        String signature = contentSignature(lines, items);
        String locKey = locationKey(block.getLocation());
        ActiveOverlay active = activeByLocation.get(locKey);

        if (active != null && signature.equals(active.signature) && entitiesAlive(active, block.getWorld())) {
            return;
        }
        if (active != null && entitiesAlive(active, block.getWorld())) {
            if (updateInPlace(block, active, lines, items)) {
                active.signature = signature;
                return;
            }
        }
        hide(block);
        spawnOverlay(block, template, config, lines, items, signature);
    }

    public void hideAllInChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        for (String key : new ArrayList<>(activeByLocation.keySet())) {
            Location loc = parseLocationKey(key);
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
                    ActiveOverlay active = activeByLocation.remove(key);
                    if (active != null) {
                        removeOverlayEntities(active, loc.getWorld());
                    }
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
        for (String key : new ArrayList<>(activeByLocation.keySet())) {
            Location loc = parseLocationKey(key);
            if (loc != null && loc.getWorld() != null) {
                ActiveOverlay active = activeByLocation.remove(key);
                if (active != null) {
                    removeOverlayEntities(active, loc.getWorld());
                }
            } else {
                activeByLocation.remove(key);
            }
        }
    }

    public boolean isHoverEnabled(Block block) {
        return block != null && block.getType() == Material.HOPPER
                && HopperBlockConfig.read(block, keys).isHoverDisplay();
    }

    private boolean updateInPlace(Block block, ActiveOverlay active, List<String> lines, List<ItemStack> items) {
        World world = block.getWorld();
        if (world == null) {
            return false;
        }
        Location center = block.getLocation().add(0.5, 0.0, 0.5);
        String marker = locKey(block);

        while (active.textDisplays.size() > lines.size()) {
            UUID id = active.textDisplays.remove(active.textDisplays.size() - 1);
            removeEntity(world, id);
        }
        while (active.textDisplays.size() < lines.size()) {
            int i = active.textDisplays.size();
            String line = lines.get(i);
            if (line == null || line.isEmpty()) {
                break;
            }
            float y = LINE_START_Y - i * LINE_SPACING;
            TextDisplay display = spawnText(world, center.clone().add(0, y, 0), marker, line);
            active.textDisplays.add(display.getUniqueId());
        }
        for (int i = 0; i < active.textDisplays.size() && i < lines.size(); i++) {
            Entity entity = plugin.getServer().getEntity(active.textDisplays.get(i));
            if (entity instanceof TextDisplay textDisplay) {
                textDisplay.setText(lines.get(i));
            }
        }

        int itemCount = items.size();
        while (active.itemDisplays.size() > itemCount) {
            UUID id = active.itemDisplays.remove(active.itemDisplays.size() - 1);
            removeEntity(world, id);
        }
        float itemStartX = itemCount <= 1 ? 0f : -((itemCount - 1) * ITEM_SPACING) / 2f;
        while (active.itemDisplays.size() < itemCount) {
            int i = active.itemDisplays.size();
            Location at = center.clone().add(itemStartX + i * ITEM_SPACING, ITEM_ROW_Y, 0);
            ItemDisplay display = spawnItem(world, at, marker, items.get(i));
            active.itemDisplays.add(display.getUniqueId());
        }
        for (int i = 0; i < active.itemDisplays.size() && i < itemCount; i++) {
            Entity entity = plugin.getServer().getEntity(active.itemDisplays.get(i));
            if (entity instanceof ItemDisplay itemDisplay) {
                itemDisplay.setItemStack(singleDisplayStack(items.get(i)));
            }
        }
        return true;
    }

    private void spawnOverlay(Block block, HopperTemplate template, HopperBlockConfig config,
                              List<String> lines, List<ItemStack> items, String signature) {
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        Location center = block.getLocation().add(0.5, 0.0, 0.5);
        String marker = locKey(block);
        ActiveOverlay active = new ActiveOverlay();
        active.signature = signature;

        int itemCount = items.size();
        float itemStartX = itemCount <= 1 ? 0f : -((itemCount - 1) * ITEM_SPACING) / 2f;
        for (int i = 0; i < itemCount; i++) {
            Location at = center.clone().add(itemStartX + i * ITEM_SPACING, ITEM_ROW_Y, 0);
            ItemDisplay display = spawnItem(world, at, marker, items.get(i));
            active.itemDisplays.add(display.getUniqueId());
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) {
                continue;
            }
            float y = LINE_START_Y - i * LINE_SPACING;
            TextDisplay display = spawnText(world, center.clone().add(0, y, 0), marker, line);
            active.textDisplays.add(display.getUniqueId());
        }

        if (!active.itemDisplays.isEmpty() || !active.textDisplays.isEmpty()) {
            activeByLocation.put(locationKey(block.getLocation()), active);
        }
    }

    private ItemDisplay spawnItem(World world, Location at, String marker, ItemStack stack) {
        return world.spawn(at, ItemDisplay.class, entity -> {
            configureItemDisplayEntity(entity, marker);
            entity.setItemStack(singleDisplayStack(stack));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
        });
    }

    private TextDisplay spawnText(World world, Location at, String marker, String line) {
        return world.spawn(at, TextDisplay.class, entity -> {
            configureTextDisplayEntity(entity, marker);
            entity.setText(line);
            entity.setDefaultBackground(false);
            entity.setSeeThrough(true);
        });
    }

    private void configureItemDisplayEntity(ItemDisplay entity, String locKey) {
        applyDisplayBase(entity, locKey, ITEM_DISPLAY_SCALE);
    }

    private void configureTextDisplayEntity(TextDisplay entity, String locKey) {
        applyDisplayBase(entity, locKey, TEXT_DISPLAY_SCALE);
    }

    private void applyDisplayBase(Display entity, String locKey, float scale) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGravity(false);
        entity.setBillboard(Display.Billboard.CENTER);
        entity.setViewRange(VIEW_RANGE);
        entity.setBrightness(new Display.Brightness(15, 15));
        entity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)));
        entity.getPersistentDataContainer().set(keys.overlayMarker, PersistentDataType.STRING, locKey);
    }

    private static boolean entitiesAlive(ActiveOverlay active, World world) {
        if (active == null || world == null) {
            return false;
        }
        for (UUID id : active.itemDisplays) {
            if (!isAlive(world, id)) {
                return false;
            }
        }
        for (UUID id : active.textDisplays) {
            if (!isAlive(world, id)) {
                return false;
            }
        }
        return !active.itemDisplays.isEmpty() || !active.textDisplays.isEmpty();
    }

    private static boolean isAlive(World world, UUID id) {
        Entity entity = Bukkit.getServer().getEntity(id);
        return entity != null && entity.isValid() && entity.getWorld().equals(world);
    }

    private static void removeEntity(World world, UUID id) {
        Entity entity = Bukkit.getServer().getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    private static void removeOverlayEntities(ActiveOverlay active, World world) {
        if (active == null || world == null) {
            return;
        }
        for (UUID id : active.itemDisplays) {
            removeEntity(world, id);
        }
        for (UUID id : active.textDisplays) {
            removeEntity(world, id);
        }
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

    private static String locKey(Block block) {
        return locationKey(block.getLocation());
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
            World world = Bukkit.getWorld(worldId);
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

    private static final class ActiveOverlay {
        final List<UUID> itemDisplays = new ArrayList<>();
        final List<UUID> textDisplays = new ArrayList<>();
        String signature = "";
    }
}
