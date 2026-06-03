package xlingran;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * 反向吸取：仅取消与反向冲突的原版四向传输；搬运由 {@link HopperTickService} 执行。
 */
public class HopperReverseHandler implements Listener {

    private final HopperKeys keys;
    private final HopperTemplateManager templateManager;
    private final HopperAutomationRegistry registry;

    public HopperReverseHandler(HopperKeys keys, HopperTemplateManager templateManager,
                                HopperAutomationRegistry registry) {
        this.keys = keys;
        this.templateManager = templateManager;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block destHopper = getHopperBlock(event.getDestination());
        Block srcHopper = getHopperBlock(event.getSource());

        if (destHopper != null && HopperBlockConfig.isReverse(destHopper, keys)) {
            registry.syncHopper(destHopper, keys, templateManager);
            if (isInventoryAboveHopper(destHopper, event.getSource())
                    || isInventoryBelowHopper(destHopper, event.getSource())) {
                event.setCancelled(true);
            }
        }
        if (srcHopper != null && HopperBlockConfig.isReverse(srcHopper, keys)) {
            registry.syncHopper(srcHopper, keys, templateManager);
            if (isInventoryBelowHopper(srcHopper, event.getDestination())
                    || isInventoryAboveHopper(srcHopper, event.getDestination())) {
                event.setCancelled(true);
            }
        }
    }

    private static Block getHopperBlock(Inventory inventory) {
        if (inventory == null || inventory.getType() != InventoryType.HOPPER) {
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
