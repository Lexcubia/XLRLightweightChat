package xlingran;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import xlingran.core.HopperLaneListener;
/**
 * 反向吸取：仅取消四向冲突移动；搬运由 HopperTickService 执行。
 */
public class HopperReverseHandler implements Listener {

    private final HopperKeys keys;
    private final HopperTickService tickService;
    private final HopperLaneListener laneListener;

    public HopperReverseHandler(HopperKeys keys, HopperTickService tickService, HopperLaneListener laneListener) {
        this.keys = keys;
        this.tickService = tickService;
        this.laneListener = laneListener;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block destHopper = getHopperBlock(event.getDestination());
        Block srcHopper = getHopperBlock(event.getSource());

        if (destHopper != null && HopperBlockConfig.isReverse(destHopper, keys)) {
            if (isInventoryAboveHopper(destHopper, event.getSource())
                    || isInventoryBelowHopper(destHopper, event.getSource())) {
                event.setCancelled(true);
            }
            laneListener.scheduleEvaluate(destHopper);
            tickService.getLaneRegistry().invalidateTargetSpace(destHopper.getLocation());
        }
        if (srcHopper != null && HopperBlockConfig.isReverse(srcHopper, keys)) {
            if (isInventoryBelowHopper(srcHopper, event.getDestination())
                    || isInventoryAboveHopper(srcHopper, event.getDestination())) {
                event.setCancelled(true);
            }
            laneListener.scheduleEvaluate(srcHopper);
            tickService.getLaneRegistry().invalidateTargetSpace(srcHopper.getLocation());
        }
    }

    private static Block getHopperBlock(Inventory inventory) {
        return HopperBlockUtil.resolveHopperBlock(inventory);
    }

    private static boolean isInventoryAboveHopper(Block hopper, Inventory other) {
        Block otherBlock = getInventoryBlock(other);
        return otherBlock != null && otherBlock.equals(hopper.getRelative(BlockFace.UP));
    }

    private static boolean isInventoryBelowHopper(Block hopper, Inventory other) {
        Block otherBlock = getInventoryBlock(other);
        return otherBlock != null && otherBlock.equals(hopper.getRelative(BlockFace.DOWN));
    }

    private static Block getInventoryBlock(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        if (inventory.getHolder() instanceof BlockState blockState) {
            return blockState.getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }
}
