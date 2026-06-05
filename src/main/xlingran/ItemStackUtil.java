package xlingran;

import org.bukkit.inventory.ItemStack;

public final class ItemStackUtil {

    private ItemStackUtil() {
    }

    public static ItemStack clonePrototype(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }
}
