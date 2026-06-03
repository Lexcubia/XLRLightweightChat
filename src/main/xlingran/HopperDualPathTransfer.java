package xlingran;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

/**
 * 从方块实体库存执行双路按比例拆分；扣减前后核对数量，杜绝未扣漏斗却写入仓库。
 */
public final class HopperDualPathTransfer {

    private HopperDualPathTransfer() {
    }

    public static TransferResult execute(Location hopperLoc, Location belowLoc, UUID owner, String boxName,
                                         ItemStack prototype, int requestedAmount,
                                         HopperTemplateManager templateManager, HopperKeys keys,
                                         PlayerBoxManager boxManager) {
        if (requestedAmount <= 0 || prototype == null || prototype.getType().isAir()) {
            return TransferResult.NONE;
        }
        Block hopperBlock = hopperLoc.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) {
            return TransferResult.NONE;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(hopperBlock, keys, templateManager);
        if (template == null || !template.allows(prototype, hopperBlock, keys)) {
            return TransferResult.NONE;
        }
        if (boxName == null || boxName.isEmpty() || owner == null || !boxManager.hasBox(owner, boxName)) {
            return TransferResult.NONE;
        }

        Block belowBlock = belowLoc.getBlock();
        Inventory hopperInv = getContainerInventory(hopperBlock);
        Inventory belowInv = getContainerInventory(belowBlock);
        if (hopperInv == null || belowInv == null) {
            return TransferResult.NONE;
        }

        int available = countMatching(hopperInv, prototype);
        if (available <= 0) {
            return TransferResult.NONE;
        }

        int planMove = Math.min(requestedAmount, available);
        int belowCap = InventoryCapacity.maxFit(belowInv, prototype, planMove);
        int boxCap = boxManager.maxFit(owner, boxName, prototype, planMove);
        if (boxCap <= 0 && belowCap <= 0) {
            return TransferResult.NONE;
        }
        planMove = Math.min(planMove, belowCap + boxCap);
        if (planMove <= 0) {
            return TransferResult.NONE;
        }

        int countBefore = countMatching(hopperInv, prototype);
        int removed = removeFromHopper(hopperInv, prototype, planMove);
        int countAfter = countMatching(hopperInv, prototype);
        int verifiedRemoved = countBefore - countAfter;
        if (verifiedRemoved <= 0) {
            syncContainer(hopperBlock);
            return TransferResult.NONE;
        }
        if (verifiedRemoved < removed) {
            refundToHopper(hopperInv, prototype, removed - verifiedRemoved, hopperBlock);
            removed = verifiedRemoved;
        }

        belowCap = InventoryCapacity.maxFit(belowInv, prototype, removed);
        boxCap = boxManager.maxFit(owner, boxName, prototype, removed);
        int[] split = allocateWarehouseFirst(removed, belowCap, boxCap);
        int toBox = split[0];
        int toBelow = split[1];

        int distributed = 0;
        boolean boxChanged = false;

        if (toBelow > 0) {
            ItemStack forBelow = prototype.clone();
            forBelow.setAmount(toBelow);
            int addedBelow = addToInventory(belowInv, forBelow);
            distributed += addedBelow;
            if (addedBelow < toBelow) {
                refundToHopper(hopperInv, prototype, toBelow - addedBelow, hopperBlock);
            }
        }
        if (toBox > 0) {
            ItemStack forBox = prototype.clone();
            forBox.setAmount(toBox);
            HashMap<Integer, ItemStack> boxLeft = boxManager.addItem(owner, boxName, forBox);
            boxChanged = true;
            int addedBox = toBox;
            for (ItemStack left : boxLeft.values()) {
                addedBox -= left.getAmount();
                refundToHopper(hopperInv, prototype, left.getAmount(), hopperBlock);
            }
            distributed += addedBox;
        }

        int notPlaced = removed - distributed;
        if (notPlaced > 0) {
            refundToHopper(hopperInv, prototype, notPlaced, hopperBlock);
        }

        syncContainer(hopperBlock);
        syncContainer(belowBlock);
        return boxChanged ? TransferResult.BOX_CHANGED : TransferResult.OK;
    }

    public enum TransferResult {
        NONE,
        OK,
        BOX_CHANGED
    }

    private static int countMatching(Inventory inventory, ItemStack prototype) {
        int total = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slotMatchesForRemoval(slot, prototype)) {
                total += slot.getAmount();
            }
        }
        return total;
    }

    private static void refundToHopper(Inventory hopperInv, ItemStack prototype, int amount, Block hopperBlock) {
        if (amount <= 0) {
            return;
        }
        ItemStack refund = prototype.clone();
        refund.setAmount(amount);
        HashMap<Integer, ItemStack> left = hopperInv.addItem(refund);
        for (ItemStack drop : left.values()) {
            hopperBlock.getWorld().dropItemNaturally(hopperBlock.getLocation().add(0.5, 0.5, 0.5), drop);
        }
    }

    private static int addToInventory(Inventory inventory, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        int wanted = stack.getAmount();
        HashMap<Integer, ItemStack> left = inventory.addItem(stack);
        int remaining = wanted;
        for (ItemStack l : left.values()) {
            remaining -= l.getAmount();
        }
        return remaining;
    }

    /**
     * 链接仓库优先：先填满仓库，仅当仓库放不下时才写入下方容器。
     */
    private static int[] allocateWarehouseFirst(int removed, int belowCap, int boxCap) {
        int toBox = Math.min(removed, Math.max(0, boxCap));
        int toBelow = Math.min(removed - toBox, Math.max(0, belowCap));
        return new int[]{toBox, toBelow};
    }

    private static int removeFromHopper(Inventory hopperInv, ItemStack prototype, int amount) {
        int left = amount;
        int removed = 0;
        for (int i = 0; i < hopperInv.getSize() && left > 0; i++) {
            ItemStack slot = hopperInv.getItem(i);
            if (!slotMatchesForRemoval(slot, prototype)) {
                continue;
            }
            int take = Math.min(left, slot.getAmount());
            int newAmount = slot.getAmount() - take;
            if (newAmount <= 0) {
                hopperInv.setItem(i, null);
            } else {
                ItemStack updated = slot.clone();
                updated.setAmount(newAmount);
                hopperInv.setItem(i, updated);
            }
            left -= take;
            removed += take;
        }
        return removed;
    }

    static boolean slotMatchesForRemoval(ItemStack slot, ItemStack prototype) {
        if (slot == null || slot.getType().isAir() || prototype == null || prototype.getType().isAir()) {
            return false;
        }
        if (slot.isSimilar(prototype)) {
            return true;
        }
        return slot.getType() == prototype.getType()
                && FilterItemMatcher.matches(slot, prototype)
                && FilterItemMatcher.matches(prototype, slot);
    }

    private static Inventory getContainerInventory(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    private static void syncContainer(Block block) {
        if (block == null) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof Container) {
            state.update(true, false);
        }
    }
}
