package xlingran;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;

/** 按样板 ItemStack 匹配（样板有设置的字段才参与比较）。 */
public final class FilterItemMatcher {

    private FilterItemMatcher() {
    }

    public static boolean allows(ItemStack stack, boolean whitelist, List<ItemStack> prototypes) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        // 未配置过滤物品时不做样板限制（与耐久/附魔未设置时一致）
        if (prototypes == null || prototypes.isEmpty()) {
            return true;
        }
        boolean anyMatch = false;
        for (ItemStack prototype : prototypes) {
            if (matches(stack, prototype)) {
                anyMatch = true;
                break;
            }
        }
        return whitelist ? anyMatch : !anyMatch;
    }

    public static boolean matches(ItemStack stack, ItemStack prototype) {
        if (stack == null || prototype == null || stack.getType().isAir() || prototype.getType().isAir()) {
            return false;
        }
        if (stack.getType() != prototype.getType()) {
            return false;
        }
        ItemMeta stackMeta = stack.getItemMeta();
        ItemMeta protoMeta = prototype.getItemMeta();
        if (protoMeta == null) {
            return true;
        }
        if (!matchesPotionMeta(stackMeta, protoMeta)) {
            return false;
        }
        if (stackMeta == null) {
            return !protoMeta.hasDisplayName() && !protoMeta.hasLore() && !protoMeta.hasEnchants()
                    && !protoMeta.hasCustomModelData() && !(protoMeta instanceof PotionMeta);
        }
        if (protoMeta.hasDisplayName()) {
            String stackName = stackMeta.hasDisplayName() ? stackMeta.getDisplayName() : "";
            if (!TextUtil.stripForMatch(stackName).equals(TextUtil.stripForMatch(protoMeta.getDisplayName()))) {
                return false;
            }
        }
        if (protoMeta.hasLore() && protoMeta.getLore() != null) {
            List<String> stackLore = stackMeta.hasLore() && stackMeta.getLore() != null
                    ? stackMeta.getLore() : List.of();
            for (String protoLine : protoMeta.getLore()) {
                String need = TextUtil.stripForMatch(protoLine);
                boolean found = false;
                for (String line : stackLore) {
                    if (TextUtil.stripForMatch(line).contains(need) || need.contains(TextUtil.stripForMatch(line))) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }
        if (protoMeta.hasEnchants()) {
            for (Map.Entry<Enchantment, Integer> entry : protoMeta.getEnchants().entrySet()) {
                if (!stackMeta.hasEnchant(entry.getKey())
                        || stackMeta.getEnchantLevel(entry.getKey()) < entry.getValue()) {
                    return false;
                }
            }
        }
        if (protoMeta.hasCustomModelData()) {
            if (!stackMeta.hasCustomModelData()
                    || stackMeta.getCustomModelData() != protoMeta.getCustomModelData()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 样板为药水时比较基础类型与自定义效果，避免仅按 {@link org.bukkit.Material#POTION} 误伤全部药水。
     */
    private static boolean matchesPotionMeta(ItemMeta stackMeta, ItemMeta protoMeta) {
        if (!(protoMeta instanceof PotionMeta protoPotion)) {
            return true;
        }
        if (!(stackMeta instanceof PotionMeta stackPotion)) {
            return false;
        }
        PotionType protoBase = protoPotion.getBasePotionType();
        if (protoBase != null) {
            if (stackPotion.getBasePotionType() != protoBase) {
                return false;
            }
        }
        if (protoPotion.hasCustomEffects()) {
            return customEffectsMatch(stackPotion, protoPotion);
        }
        return true;
    }

    private static boolean customEffectsMatch(PotionMeta stackPotion, PotionMeta protoPotion) {
        List<PotionEffect> protoEffects = protoPotion.getCustomEffects();
        if (protoEffects == null || protoEffects.isEmpty()) {
            return true;
        }
        List<PotionEffect> stackEffects = stackPotion.getCustomEffects();
        if (stackEffects == null || stackEffects.isEmpty()) {
            return false;
        }
        for (PotionEffect required : protoEffects) {
            PotionEffectType type = required.getType();
            if (type == null) {
                continue;
            }
            boolean found = false;
            for (PotionEffect onStack : stackEffects) {
                if (onStack.getType() == type
                        && onStack.getAmplifier() == required.getAmplifier()
                        && onStack.getDuration() == required.getDuration()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /** 两条规则是否为同一样板（用于 GUI 去重）。 */
    public static boolean sameRule(ItemStack a, ItemStack b) {
        return matches(a, b) && matches(b, a);
    }
}
