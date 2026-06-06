package xlingran;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import xlingran.core.HopperLaneListener;

/**
 * 取消插件管理漏斗的原版上下方容器传输，改由 tick 驱动（正向/反向/红石）。
 */
public final class HopperManagedTransferHandler implements Listener {

    private final HopperKeys keys;
    private final HopperTickService tickService;
    private final HopperLaneListener laneListener;
    private final XLRHopperConfig pluginConfig;

    public HopperManagedTransferHandler(HopperKeys keys, HopperTickService tickService,
                                        HopperLaneListener laneListener, XLRHopperConfig pluginConfig) {
        this.keys = keys;
        this.tickService = tickService;
        this.laneListener = laneListener;
        this.pluginConfig = pluginConfig;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block destHopper = getHopperBlock(event.getDestination());
        Block srcHopper = getHopperBlock(event.getSource());

        if (destHopper != null && shouldCancelManaged(destHopper, event.getSource())) {
            event.setCancelled(true);
            wakeHopper(destHopper);
        }
        if (srcHopper != null && shouldCancelManaged(srcHopper, event.getDestination())) {
            event.setCancelled(true);
            wakeHopper(srcHopper);
        }
    }

    private boolean shouldCancelManaged(Block hopper, Inventory other) {
        if (!pluginConfig.isPluginWorld(hopper)) {
            return false;
        }
        if (HopperTemplateResolver.resolve(hopper, keys, tickService.getTemplateManager()) == null) {
            return false;
        }
        if (!isVerticalContainerNeighbor(hopper, other)) {
            return false;
        }
        if (HopperRedstoneTransferService.isRedstonePoweredTransferActive(hopper, keys, pluginConfig)) {
            return true;
        }
        return true;
    }

    private void wakeHopper(Block hopper) {
        laneListener.scheduleEvaluateImmediate(hopper);
        tickService.getLaneRegistry().invalidateTargetSpace(hopper.getLocation());
    }

    private static boolean isVerticalContainerNeighbor(Block hopper, Inventory other) {
        Block otherBlock = getInventoryBlock(other);
        if (otherBlock == null) {
            return false;
        }
        return otherBlock.equals(hopper.getRelative(BlockFace.UP))
                || otherBlock.equals(hopper.getRelative(BlockFace.DOWN));
    }

    private static Block getHopperBlock(Inventory inventory) {
        return HopperBlockUtil.resolveHopperBlock(inventory);
    }

    private static Block getInventoryBlock(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        if (inventory.getHolder() instanceof BlockState) {
            return ((BlockState) inventory.getHolder()).getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }
}
