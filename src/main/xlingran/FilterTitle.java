package xlingran;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** 名称过滤：固定黑名单。 */
public final class FilterTitle {

    private FilterTitle() {
    }

    public static boolean allows(ItemStack stack, List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        if (stack == null || !stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return true;
        }
        String display = TextUtil.stripForMatch(meta.getDisplayName());
        for (String rule : rules) {
            if (rule == null || rule.isEmpty()) {
                continue;
            }
            if (display.contains(TextUtil.stripForMatch(rule))) {
                return false;
            }
        }
        return true;
    }
}
