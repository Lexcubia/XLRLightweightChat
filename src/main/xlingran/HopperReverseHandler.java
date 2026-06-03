package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

/**
 * 反向吸取：从下方向上传输；取消原版从上吸入与向下输出，并尝试定向搬运。
 */
public class HopperReverseHandler implements Listener {

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;

    public HopperReverseHandler(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block destHopper = getHopperBlock(event.getDestination());
        Block srcHopper = getHopperBlock(event.getSource());
        boolean cancelled = false;

        if (destHopper != null && HopperBlockConfig.isReverse(destHopper, keys)) {
            if (isInventoryAboveHopper(destHopper, event.getSource())) {
                event.setCancelled(true);
                cancelled = true;
            }
        }
        if (srcHopper != null && HopperBlockConfig.isReverse(srcHopper, keys)) {
            if (isInventoryBelowHopper(srcHopper, event.getDestination())) {
                event.setCancelled(true);
                cancelled = true;
            }
        }

        Block reverseBlock = destHopper != null && HopperBlockConfig.isReverse(destHopper, keys) ? destHopper
                : (srcHopper != null && HopperBlockConfig.isReverse(srcHopper, keys) ? srcHopper : null);
        if (cancelled && reverseBlock != null) {
            Bukkit.getScheduler().runTask(plugin, () -> attemptReverseTransfer(reverseBlock));
        }
    }

    private void attemptReverseTransfer(Block hopperBlock) {
        if (hopperBlock.getType() != Material.HOPPER) {
            return;
        }
        if (!HopperBlockConfig.isReverse(hopperBlock, keys)) {
            return;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(hopperBlock, keys, templateManager);
        if (template == null) {
            return;
        }
        BlockState state = hopperBlock.getState();
        if (!(state instanceof Container hopperContainer)) {
            return;
        }
        Inventory hopperInv = hopperContainer.getInventory();

        Inventory belowInv = getNeighborInventory(hopperBlock.getRelative(BlockFace.DOWN));
        if (belowInv != null) {
            pullOne(belowInv, hopperInv, hopperBlock, template);
        }

        Inventory aboveInv = getNeighborInventory(hopperBlock.getRelative(BlockFace.UP));
        if (aboveInv != null) {
            pushOne(hopperInv, aboveInv, hopperBlock, template);
        }
    }

    private void pullOne(Inventory from, Inventory hopperInv, Block hopperBlock, HopperTemplate template) {
        for (int i = 0; i < from.getSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!template.allows(stack, hopperBlock, keys)) {
                continue;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            HashMap<Integer, ItemStack> leftover = hopperInv.addItem(one);
            if (leftover.isEmpty()) {
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    from.setItem(i, null);
                }
                return;
            }
        }
    }

    private void pushOne(Inventory hopperInv, Inventory to, Block hopperBlock, HopperTemplate template) {
        for (int i = 0; i < hopperInv.getSize(); i++) {
            ItemStack stack = hopperInv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!template.allows(stack, hopperBlock, keys)) {
                continue;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            HashMap<Integer, ItemStack> leftover = to.addItem(one);
            if (leftover.isEmpty()) {
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    hopperInv.setItem(i, null);
                }
                return;
            }
        }
    }

    private static Inventory getNeighborInventory(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
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
