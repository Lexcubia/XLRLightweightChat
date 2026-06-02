package xlingran;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/** 剩余耐久低于阈值则拒绝。 */
public final class FilterDurability {

    private FilterDurability() {
    }

    public static boolean allows(ItemStack stack, Integer threshold) {
        if (threshold == null) {
            return true;
        }
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        if (!(stack.getItemMeta() instanceof Damageable damageable)) {
            return true;
        }
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : stack.getType().getMaxDurability();
        if (max <= 0) {
            return true;
        }
        int damage = damageable.getDamage();
        int remaining = max - damage;
        return remaining >= threshold;
    }
}
