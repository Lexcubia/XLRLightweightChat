package xlingran;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** 材质过滤；唯一使用白/黑名单切换的维度。 */
public final class FilterItem {

    private FilterItem() {
    }

    public static boolean allows(ItemStack stack, boolean whitelist, Set<Material> materials) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Material type = stack.getType();
        if (materials == null || materials.isEmpty()) {
            return !whitelist;
        }
        boolean inList = materials.contains(type);
        return whitelist ? inList : !inList;
    }
}
