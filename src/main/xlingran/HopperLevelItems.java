package xlingran;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import xlingran.gui.HopperLevelDef;
import xlingran.gui.TextPlaceholders;
import xlingran.gui.UpdateConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HopperLevelItems {

    private HopperLevelItems() {
    }

    public static String normalizeLevelId(String levelId) {
        if (levelId == null) {
            return null;
        }
        String normalized = levelId.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public static ItemStack createLevelHopper(UpdateConfig config, HopperKeys keys, String levelId, int amount) {
        String normalizedId = normalizeLevelId(levelId);
        if (normalizedId == null || amount < 1 || config == null || keys == null) {
            return null;
        }
        HopperLevelDef def = config.getLevel(normalizedId);
        if (def == null) {
            return null;
        }
        ItemStack stack = new ItemStack(Material.HOPPER, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        clearItemMeta(meta);
        meta.setDisplayName(TextPlaceholders.color(def.displayName()));
        List<String> lore = new ArrayList<>();
        for (String line : def.lore()) {
            lore.add(TextPlaceholders.color(line));
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(keys.hopperLevelItem, PersistentDataType.STRING, normalizedId);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack toCanonical(ItemStack stack, UpdateConfig config, HopperKeys keys) {
        if (stack == null || stack.getType() != Material.HOPPER) {
            return stack;
        }
        String levelId = readLevelFromItem(stack, keys);
        if (levelId == null) {
            return stack;
        }
        ItemStack canonical = createLevelHopper(config, keys, levelId, stack.getAmount());
        return canonical != null ? canonical : stack;
    }

    public static String readLevelFromItem(ItemStack stack, HopperKeys keys) {
        if (stack == null || stack.getType() != Material.HOPPER || !stack.hasItemMeta() || keys == null) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(keys.hopperLevelItem, PersistentDataType.STRING);
        return normalizeLevelId(raw);
    }

    public static ItemStack createDropFromBlock(Block block, HopperKeys keys, UpdateConfig config) {
        String levelId = normalizeLevelId(HopperLevelResolver.readLevelId(block, keys));
        if (levelId == null) {
            return null;
        }
        return createLevelHopper(config, keys, levelId, 1);
    }

    public static boolean applyLevelToBlock(Block block, HopperKeys keys, String levelId) {
        String normalizedId = normalizeLevelId(levelId);
        if (block == null || block.getType() != Material.HOPPER || normalizedId == null) {
            return false;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return false;
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(keys.hopperLevel, PersistentDataType.STRING, normalizedId);
        tileState.update(true);
        return true;
    }

    private static void clearItemMeta(ItemMeta meta) {
        meta.setDisplayName(null);
        meta.setLore(null);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Set<NamespacedKey> keys = new HashSet<>(pdc.getKeys());
        for (NamespacedKey key : keys) {
            pdc.remove(key);
        }
    }
}
