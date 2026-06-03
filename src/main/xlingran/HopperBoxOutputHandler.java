package xlingran;

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

import java.util.HashMap;
import java.util.UUID;

/**
 * 双路按比例拆分：漏斗转出 N 个物品时，按下方/仓库剩余空间比例分配，总量仍为 N（不复制）。
 */
public class HopperBoxOutputHandler implements Listener {

    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final PlayerBoxManager boxManager;
    private final Runnable persistBoxes;

    public HopperBoxOutputHandler(HopperTemplateManager templateManager, HopperKeys keys,
                                  PlayerBoxManager boxManager, Runnable persistBoxes) {
        this.templateManager = templateManager;
        this.keys = keys;
        this.boxManager = boxManager;
        this.persistBoxes = persistBoxes;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (event.getSource().getType() != InventoryType.HOPPER) {
            return;
        }
        Inventory hopperInv = event.getSource();
        Block hopperBlock = getHopperBlock(hopperInv);
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
        int belowCap = InventoryCapacity.maxFit(dest, moving);
        int boxCap = boxManager.maxFit(owner, boxName, moving);

        if (boxCap <= 0) {
            return;
        }

        int sumCap = belowCap + boxCap;
        if (sumCap <= 0) {
            return;
        }

        event.setCancelled(true);

        int planMove = Math.min(total, sumCap);
        int removed = removeFromHopper(hopperInv, moving, planMove);
        if (removed <= 0) {
            return;
        }

        int toBelow = splitAmount(removed, belowCap, boxCap);
        int toBox = removed - toBelow;
        if (toBox > boxCap) {
            toBelow += toBox - boxCap;
            toBox = boxCap;
            toBelow = Math.min(toBelow, belowCap);
        }
        if (toBelow > belowCap) {
            toBox += toBelow - belowCap;
            toBelow = belowCap;
            toBox = Math.min(toBox, boxCap);
        }

        boolean boxChanged = false;
        int distributed = 0;

        if (toBelow > 0) {
            ItemStack forBelow = moving.clone();
            forBelow.setAmount(toBelow);
            HashMap<Integer, ItemStack> belowLeft = dest.addItem(forBelow);
            int addedBelow = toBelow;
            for (ItemStack left : belowLeft.values()) {
                addedBelow -= left.getAmount();
                returnToHopper(hopperInv, left);
            }
            distributed += addedBelow;
        }
        if (toBox > 0) {
            ItemStack forBox = moving.clone();
            forBox.setAmount(toBox);
            HashMap<Integer, ItemStack> boxLeft = boxManager.addItem(owner, boxName, forBox);
            boxChanged = true;
            int addedBox = toBox;
            for (ItemStack left : boxLeft.values()) {
                addedBox -= left.getAmount();
                returnToHopper(hopperInv, left);
            }
            distributed += addedBox;
        }

        int notPlaced = removed - distributed;
        if (notPlaced > 0) {
            ItemStack refund = moving.clone();
            refund.setAmount(notPlaced);
            returnToHopper(hopperInv, refund);
        }

        syncHopper(hopperInv);
        if (boxChanged && persistBoxes != null) {
            persistBoxes.run();
        }
    }

    /** 按两路剩余空间比例分配实际已从漏斗取出的数量。 */
    private static int splitAmount(int removed, int belowCap, int boxCap) {
        if (removed <= 0) {
            return 0;
        }
        if (boxCap <= 0) {
            return Math.min(removed, belowCap);
        }
        if (belowCap <= 0) {
            return 0;
        }
        int sumCap = belowCap + boxCap;
        return (int) ((long) removed * belowCap / sumCap);
    }

    /**
     * 从漏斗扣除物品，返回实际扣除数量。
     */
    private static int removeFromHopper(Inventory hopperInv, ItemStack moving, int amount) {
        int left = amount;
        int removed = 0;
        for (int i = 0; i < hopperInv.getSize() && left > 0; i++) {
            ItemStack slot = hopperInv.getItem(i);
            if (!slotMatchesForRemoval(slot, moving)) {
                continue;
            }
            int take = Math.min(left, slot.getAmount());
            slot.setAmount(slot.getAmount() - take);
            if (slot.getAmount() <= 0) {
                hopperInv.setItem(i, null);
            }
            left -= take;
            removed += take;
        }
        return removed;
    }

    private static boolean slotMatchesForRemoval(ItemStack slot, ItemStack moving) {
        if (slot == null || slot.getType().isAir() || moving == null || moving.getType().isAir()) {
            return false;
        }
        if (slot.isSimilar(moving)) {
            return true;
        }
        return slot.getType() == moving.getType()
                && FilterItemMatcher.matches(slot, moving)
                && FilterItemMatcher.matches(moving, slot);
    }

    private static void syncHopper(Inventory hopperInv) {
        if (hopperInv.getHolder() instanceof Container container) {
            container.getBlock().getState().update(true, false);
        }
    }

    private static void returnToHopper(Inventory hopperInv, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> left = hopperInv.addItem(stack);
        if (!left.isEmpty() && hopperInv.getHolder() instanceof BlockState blockState) {
            for (ItemStack drop : left.values()) {
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
