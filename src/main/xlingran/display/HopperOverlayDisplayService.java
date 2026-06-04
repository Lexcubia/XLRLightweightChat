package xlingran.display;

import org.bukkit.Bukkit;
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
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import xlingran.HopperBlockConfig;
import xlingran.HopperContainerUtil;
import xlingran.HopperKeys;
import xlingran.HopperTemplate;
import xlingran.HopperTemplateManager;
import xlingran.HopperTemplateResolver;
import xlingran.gui.TextPlaceholders;
import xlingran.Shan;

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

    private final Shan plugin;
    private final HopperKeys keys;
    private final HopperTemplateManager templateManager;
    private final Map<String, List<UUID>> activeByLocation = new ConcurrentHashMap<>();

    public HopperOverlayDisplayService(Shan plugin, HopperKeys keys, HopperTemplateManager templateManager) {
        this.plugin = plugin;
        this.keys = keys;
        this.templateManager = templateManager;
    }

    public void show(Block block) {
        refresh(block);
    }

    public void hide(Block block) {
        if (block == null) {
            return;
        }
        String locKey = locationKey(block.getLocation());
        List<UUID> ids = activeByLocation.remove(locKey);
        if (ids == null) {
            return;
        }
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        for (UUID id : ids) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
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
        hide(block);
        spawnOverlay(block, template, config);
    }

    public void hideAll() {
        for (String key : new ArrayList<>(activeByLocation.keySet())) {
            Location loc = parseLocationKey(key);
            if (loc != null) {
                Block block = loc.getBlock();
                if (block.getType() == Material.HOPPER) {
                    hide(block);
                } else {
                    removeEntitiesByIds(activeByLocation.remove(key), loc.getWorld());
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

    private void spawnOverlay(Block block, HopperTemplate template, HopperBlockConfig config) {
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        Location center = block.getLocation().add(0.5, 0.0, 0.5);
        List<UUID> spawned = new ArrayList<>();

        List<ItemStack> items = collectHopperItems(block);
        int itemCount = items.size();
        float itemStartX = itemCount <= 1 ? 0f : -((itemCount - 1) * ITEM_SPACING) / 2f;
        for (int i = 0; i < itemCount; i++) {
            ItemStack stack = items.get(i);
            Location at = center.clone().add(itemStartX + i * ITEM_SPACING, ITEM_ROW_Y, 0);
            ItemDisplay display = world.spawn(at, ItemDisplay.class, entity -> configureItemDisplayEntity(entity, locKey(block)));
            display.setItemStack(singleDisplayStack(stack));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            spawned.add(display.getUniqueId());
        }

        List<String> lines = buildOverlayLines(block, template, config);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) {
                continue;
            }
            float y = LINE_START_Y - i * LINE_SPACING;
            Location at = center.clone().add(0, y, 0);
            TextDisplay display = world.spawn(at, TextDisplay.class, entity -> configureTextDisplayEntity(entity, locKey(block)));
            display.setText(line);
            display.setDefaultBackground(false);
            display.setSeeThrough(true);
            spawned.add(display.getUniqueId());
        }

        if (!spawned.isEmpty()) {
            activeByLocation.put(locationKey(block.getLocation()), spawned);
        }
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
        List<String> lines = new ArrayList<>(4);
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

    private static void removeEntitiesByIds(List<UUID> ids, World world) {
        if (ids == null || world == null) {
            return;
        }
        for (UUID id : ids) {
            Entity entity = Bukkit.getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }
}
