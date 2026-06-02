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
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        String display = resolveMatchText(stack);
        if (display.isEmpty()) {
            return true;
        }
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

    private static String resolveMatchText(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return TextUtil.stripForMatch(meta.getDisplayName());
        }
        return TextUtil.stripForMatch(stack.getType().name().replace('_', ' '));
    }
}
