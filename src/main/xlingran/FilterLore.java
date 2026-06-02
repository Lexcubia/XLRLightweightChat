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
        if (stack == null || !stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return true;
        }
        List<String> lore = meta.getLore();
        if (lore == null) {
            return true;
        }
        for (String line : lore) {
            String strippedLine = TextUtil.stripForMatch(line);
            for (String rule : rules) {
                if (rule == null || rule.isEmpty()) {
                    continue;
                }
                if (strippedLine.contains(TextUtil.stripForMatch(rule))) {
                    return false;
                }
            }
        }
        return true;
    }
}
