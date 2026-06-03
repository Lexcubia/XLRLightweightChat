package xlingran;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** 计算容器可接纳的物品数量（不修改库存）。 */
public final class InventoryCapacity {

    private InventoryCapacity() {
    }

    public static int maxFit(Inventory inventory, ItemStack stack) {
        return maxFit(inventory, stack, stack == null ? 0 : stack.getAmount());
    }

    public static int maxFit(Inventory inventory, ItemStack stack, int maxCount) {
        if (inventory == null || stack == null || stack.getType().isAir() || maxCount <= 0) {
            return 0;
        }
        int need = maxCount;
        int fit = 0;
        int maxStack = stack.getMaxStackSize();
        for (int i = 0; i < inventory.getSize(); i++) {
            fit += fitInSlot(inventory.getItem(i), stack, maxStack, need - fit);
            if (fit >= need) {
                return need;
            }
        }
        return fit;
    }

    public static int maxFit(ItemStack[] slots, ItemStack stack) {
        return maxFit(slots, stack, stack == null ? 0 : stack.getAmount());
    }

    public static int maxFit(ItemStack[] slots, ItemStack stack, int maxCount) {
        if (slots == null || stack == null || stack.getType().isAir() || maxCount <= 0) {
            return 0;
        }
        int need = maxCount;
        int fit = 0;
        int maxStack = stack.getMaxStackSize();
        for (ItemStack slot : slots) {
            fit += fitInSlot(slot, stack, maxStack, need - fit);
            if (fit >= need) {
                return need;
            }
        }
        return fit;
    }

    private static int fitInSlot(ItemStack slot, ItemStack stack, int maxStack, int stillNeed) {
        if (stillNeed <= 0) {
            return 0;
        }
        if (slot == null || slot.getType().isAir()) {
            return Math.min(stillNeed, maxStack);
        }
        if (slot.isSimilar(stack)) {
            int space = maxStack - slot.getAmount();
            if (space > 0) {
                return Math.min(stillNeed, space);
            }
        }
        return 0;
    }
}
