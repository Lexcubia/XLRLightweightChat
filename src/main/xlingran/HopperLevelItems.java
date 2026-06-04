package xlingran;

import org.bukkit.Material;
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
import java.util.List;
import java.util.Locale;

public final class HopperLevelItems {

    private HopperLevelItems() {
    }

    public static ItemStack createLevelHopper(UpdateConfig config, HopperKeys keys, String levelId, int amount) {
        HopperLevelDef def = config.getLevel(levelId);
        if (def == null || amount < 1) {
            return null;
        }
        ItemStack stack = new ItemStack(Material.HOPPER, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextPlaceholders.color(def.displayName()));
            List<String> lore = new ArrayList<>();
            for (String line : def.lore()) {
                lore.add(TextPlaceholders.color(line));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(keys.hopperLevelItem, PersistentDataType.STRING,
                    def.id().toLowerCase(Locale.ROOT));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static String readLevelFromItem(ItemStack stack, HopperKeys keys) {
        if (stack == null || stack.getType() != Material.HOPPER || !stack.hasItemMeta() || keys == null) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.hopperLevelItem, PersistentDataType.STRING);
    }

    public static boolean applyLevelToBlock(Block block, HopperKeys keys, String levelId) {
        if (block == null || block.getType() != Material.HOPPER || levelId == null || levelId.isEmpty()) {
            return false;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return false;
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(keys.hopperLevel, PersistentDataType.STRING, levelId.toLowerCase(Locale.ROOT));
        tileState.update(true);
        return true;
    }
}
