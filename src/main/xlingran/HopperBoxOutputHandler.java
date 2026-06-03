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
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

/**
 * 链接仓库输出：漏斗向下输出时优先填满下方容器，仅溢出部分写入链接仓库（不复制物品）。
 */
public class HopperBoxOutputHandler implements Listener {

    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final PlayerBoxManager boxManager;

    public HopperBoxOutputHandler(HopperTemplateManager templateManager, HopperKeys keys, PlayerBoxManager boxManager) {
        this.templateManager = templateManager;
        this.keys = keys;
        this.boxManager = boxManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (event.getSource().getType() != InventoryType.HOPPER) {
            return;
        }
        Block hopperBlock = getHopperBlock(event.getSource());
        if (hopperBlock == null) {
            return;
        }
        if (!isInventoryBelowHopper(hopperBlock, event.getDestination())) {
            return;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(hopperBlock, keys, templateManager);
        if (template == null) {
            return;
        }
        String boxName = template.getLinkedBoxName();
        if (boxName == null || boxName.isEmpty()) {
            return;
        }
        UUID owner = resolveOwner(hopperBlock);
        if (owner == null || !boxManager.hasBox(owner, boxName)) {
            return;
        }
        ItemStack moving = event.getItem();
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        if (!template.allows(moving, hopperBlock, keys)) {
            return;
        }

        Inventory dest = event.getDestination();
        int total = moving.getAmount();
        int fitBelow = maxFit(dest, moving);
        if (fitBelow >= total) {
            return;
        }

        event.setCancelled(true);
        Inventory hopperInv = event.getSource();

        int toBelow = fitBelow;
        int toBox = total - fitBelow;

        if (toBelow > 0) {
            ItemStack forBelow = moving.clone();
            forBelow.setAmount(toBelow);
            HashMap<Integer, ItemStack> belowLeft = dest.addItem(forBelow);
            for (ItemStack left : belowLeft.values()) {
                toBox += left.getAmount();
            }
        }

        if (toBox > 0) {
            ItemStack forBox = moving.clone();
            forBox.setAmount(toBox);
            if (!template.allows(forBox, hopperBlock, keys)) {
                returnItemsToHopper(hopperInv, forBox);
                removeFromHopper(hopperInv, moving, total);
                return;
            }
            HashMap<Integer, ItemStack> boxLeft = boxManager.addItem(owner, boxName, forBox);
            for (ItemStack left : boxLeft.values()) {
                returnItemsToHopper(hopperInv, left);
            }
        }

        removeFromHopper(hopperInv, moving, total);
    }

    /** 下方容器最多能接纳的数量（不修改库存）。 */
    static int maxFit(Inventory inventory, ItemStack stack) {
        if (inventory == null || stack == null || stack.getType().isAir()) {
            return 0;
        }
        int need = stack.getAmount();
        int fit = 0;
        int maxStack = stack.getMaxStackSize();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                int add = Math.min(need - fit, maxStack);
                fit += add;
            } else if (slot.isSimilar(stack)) {
                int space = maxStack - slot.getAmount();
                if (space > 0) {
                    fit += Math.min(need - fit, space);
                }
            }
            if (fit >= need) {
                return need;
            }
        }
        return fit;
    }

    private static void removeFromHopper(Inventory hopperInv, ItemStack prototype, int amount) {
        int left = amount;
        for (int i = 0; i < hopperInv.getSize() && left > 0; i++) {
            ItemStack slot = hopperInv.getItem(i);
            if (slot == null || slot.getType().isAir() || !slot.isSimilar(prototype)) {
                continue;
            }
            int take = Math.min(left, slot.getAmount());
            slot.setAmount(slot.getAmount() - take);
            if (slot.getAmount() <= 0) {
                hopperInv.setItem(i, null);
            }
            left -= take;
        }
    }

    private static void returnItemsToHopper(Inventory hopperInv, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> left = hopperInv.addItem(stack);
        for (ItemStack drop : left.values()) {
            if (hopperInv.getHolder() instanceof BlockState blockState) {
                blockState.getWorld().dropItemNaturally(blockState.getLocation().add(0.5, 0.5, 0.5), drop);
            }
        }
    }

    private UUID resolveOwner(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (!(state instanceof org.bukkit.block.TileState tileState)) {
            return null;
        }
        String ownerStr = tileState.getPersistentDataContainer().get(keys.owner,
                org.bukkit.persistence.PersistentDataType.STRING);
        if (ownerStr == null) {
            return null;
        }
        try {
            return UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isInventoryBelowHopper(Block hopper, Inventory other) {
        Block otherBlock = getInventoryBlock(other);
        return otherBlock != null && otherBlock.equals(hopper.getRelative(BlockFace.DOWN));
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
