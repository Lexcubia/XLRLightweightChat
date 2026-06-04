package xlingran;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 从漏斗库存解析对应方块（兼容不同 Spigot 实现的 InventoryHolder）。
 */
public final class HopperBlockUtil {

    private HopperBlockUtil() {
    }

    public static Block resolveHopperBlock(Inventory inventory) {
        if (inventory == null || inventory.getType() != InventoryType.HOPPER) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Hopper hopper) {
            return hopper.getBlock();
        }
        if (holder instanceof BlockInventoryHolder blockHolder) {
            Block block = blockHolder.getBlock();
            if (block != null && block.getType() == Material.HOPPER) {
                return block;
            }
        }
        if (holder instanceof BlockState blockState) {
            Block block = blockState.getBlock();
            if (block.getType() == Material.HOPPER) {
                return block;
            }
        }
        Location location = inventory.getLocation();
        if (location != null) {
            Block block = location.getBlock();
            if (block.getType() == Material.HOPPER) {
                return block;
            }
        }
        return null;
    }
}
