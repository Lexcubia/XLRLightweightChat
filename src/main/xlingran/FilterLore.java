package xlingran;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Lore 过滤：固定黑名单。 */
public final class FilterLore {

    private FilterLore() {
    }

    public static boolean allows(ItemStack stack, List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return true;
        }
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return true;
        }
        for (String line : lore) {
            String strippedLine = TextUtil.stripForMatch(line);
            if (strippedLine.isEmpty()) {
                continue;
            }
            for (String rule : rules) {
                if (rule == null || rule.isEmpty()) {
                    continue;
                }
                String strippedRule = TextUtil.stripForMatch(rule);
                if (strippedLine.contains(strippedRule)) {
                    return false;
                }
            }
        }
        return true;
    }
}
