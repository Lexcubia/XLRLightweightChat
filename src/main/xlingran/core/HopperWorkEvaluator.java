package xlingran.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xlingran.HopperAutoSmeltService;
import xlingran.HopperContainerUtil;
import xlingran.HopperKeys;

/**
 * 事件侧入队判定（禁止在 8 tick 定时器内调用）。
 */
public final class HopperWorkEvaluator {

    private HopperWorkEvaluator() {
    }

    public static void evaluateAndQueue(Block block, HopperLaneRegistry registry, HopperKeys keys,
                                      HopperAutoSmeltService smeltService) {
        if (block == null || block.getType() != Material.HOPPER) {
            return;
        }
        HopperLane lane = registry.getLane(block.getLocation());
        if (lane == null || !lane.hasSnapshot() || !lane.hasAutomation()) {
            return;
        }
        String key = HopperLane.laneKey(block.getLocation());
        if (!pendingWork(block, lane, keys, smeltService)) {
            registry.removeFromWorkQueue(key);
            return;
        }
        if (!lane.isTargetHasSpace() && lane.isReverse()) {
            registry.removeFromWorkQueue(key);
            return;
        }
        registry.offerWork(key);
    }

    public static void markPending(Block block, HopperLaneRegistry registry, HopperKeys keys,
                                   HopperAutoSmeltService smeltService) {
        registry.invalidateTargetSpace(block.getLocation());
        evaluateAndQueue(block, registry, keys, smeltService);
    }

    public static boolean shouldRemainInQueue(Block block, HopperLane lane, HopperKeys keys,
                                              HopperAutoSmeltService smeltService) {
        return pendingWork(block, lane, keys, smeltService);
    }

    private static boolean pendingWork(Block block, HopperLane lane, HopperKeys keys,
                                       HopperAutoSmeltService smeltService) {
        if (registryHasSmelt(smeltService, block.getLocation())) {
            return true;
        }
        if (!(block.getState() instanceof Container container)) {
            return false;
        }
        Inventory inv = container.getInventory();
        var template = lane.template();
        if (template == null) {
            return false;
        }
        if (lane.isAutoCraft() || lane.isReverse()) {
            for (ItemStack stack : inv.getContents()) {
                if (stack != null && !stack.getType().isAir() && template.allows(stack, block, keys)) {
                    return true;
                }
            }
        }
        if (lane.isReverse()) {
            Block below = block.getRelative(BlockFace.DOWN);
            Inventory belowInv = HopperContainerUtil.getContainerInventory(below);
            if (belowInv != null) {
                for (ItemStack stack : belowInv.getContents()) {
                    if (stack != null && !stack.getType().isAir() && template.allows(stack, block, keys)) {
                        return true;
                    }
                }
            }
        }
        if (lane.isAutoSmelt() && template.isAutoSmeltEnabled()) {
            for (ItemStack stack : inv.getContents()) {
                if (stack != null && !stack.getType().isAir() && template.allows(stack, block, keys)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean registryHasSmelt(HopperAutoSmeltService smeltService, Location loc) {
        return smeltService != null && smeltService.hasJob(loc);
    }
}
