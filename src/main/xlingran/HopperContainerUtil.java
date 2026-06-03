package xlingran;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public final class HopperContainerUtil {

    private HopperContainerUtil() {
    }

    public static Inventory getContainerInventory(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    public static void syncContainer(Block block) {
        if (block == null) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof Container) {
            state.update(true, false);
        }
    }

    public static void refund(Block block, Inventory inventory, ItemStack stack) {
        if (block == null || inventory == null || stack == null || stack.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> left = inventory.addItem(stack.clone());
        for (ItemStack drop : left.values()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }
    }

    static int countSimilar(Inventory inventory, ItemStack prototype) {
        if (inventory == null || prototype == null || prototype.getType().isAir()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot != null && !slot.getType().isAir() && slot.isSimilar(prototype)) {
                total += slot.getAmount();
            }
        }
        return total;
    }
}
