package xlingran;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/** 物品若含某附魔且等级低于设定最低等级则拒绝。 */
public final class FilterEnchant {

    private FilterEnchant() {
    }

    public static boolean allows(ItemStack stack, Map<Enchantment, Integer> minLevels) {
        if (minLevels == null || minLevels.isEmpty()) {
            return true;
        }
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return true;
        }
        for (Map.Entry<Enchantment, Integer> rule : minLevels.entrySet()) {
            Enchantment enchant = rule.getKey();
            int minLevel = rule.getValue();
            if (enchant == null || minLevel <= 0) {
                continue;
            }
            int level = getEnchantLevel(meta, enchant);
            if (level > 0 && level < minLevel) {
                return false;
            }
        }
        return true;
    }

    private static int getEnchantLevel(ItemMeta meta, Enchantment enchant) {
        if (meta.hasEnchant(enchant)) {
            return meta.getEnchantLevel(enchant);
        }
        if (meta instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchant(enchant)) {
            return storage.getStoredEnchantLevel(enchant);
        }
        return 0;
    }
}
