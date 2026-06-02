package xlingran;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 材质/样板过滤；白黑名单可由红石信号覆盖（在 HopperTemplate 内计算）。 */
public final class FilterItem {

    private FilterItem() {
    }

    public static boolean allows(ItemStack stack, boolean whitelist, List<ItemStack> prototypes) {
        return FilterItemMatcher.allows(stack, whitelist, prototypes);
    }
}
