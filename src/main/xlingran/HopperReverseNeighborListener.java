package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import xlingran.core.HopperLaneListener;

/**
 * 相邻容器关闭界面后唤醒反向吸取漏斗（覆盖手动往箱内放货路径）。
 */
public final class HopperReverseNeighborListener implements Listener {

    private final HopperKeys keys;
    private final HopperLaneListener laneListener;
    private final XLRHopperConfig pluginConfig;

    public HopperReverseNeighborListener(HopperKeys keys, HopperLaneListener laneListener,
                                         XLRHopperConfig pluginConfig) {
        this.keys = keys;
        this.laneListener = laneListener;
        this.pluginConfig = pluginConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!pluginConfig.isReverseHopperEnabled()) {
            return;
        }
        Block containerBlock = resolveContainerBlock(event.getInventory());
        if (containerBlock == null) {
            return;
        }
        wakeAdjacentReverseHoppers(containerBlock.getRelative(BlockFace.UP));
        wakeAdjacentReverseHoppers(containerBlock.getRelative(BlockFace.DOWN));
    }

    private void wakeAdjacentReverseHoppers(Block hopper) {
        if (hopper == null || hopper.getType() != Material.HOPPER || !pluginConfig.isPluginWorld(hopper)) {
            return;
        }
        if (!HopperBlockConfig.isReverse(hopper, keys)) {
            return;
        }
        laneListener.scheduleEvaluateImmediate(hopper);
    }

    private static Block resolveContainerBlock(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState blockState) {
            return blockState.getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }
}
