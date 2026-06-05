package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自动合成：按模板配置的结果物品匹配无序配方，在漏斗内扣料并生成 1 个产物。
 */
public final class HopperAutoCraftService {

    public boolean shouldHoldOutbound(Block hopperBlock, HopperTemplate template, HopperKeys keys, ItemStack moving) {
        if (hopperBlock == null || template == null || moving == null || moving.getType().isAir()) {
            return false;
        }
        if (!template.isAutoCraftEnabled() || template.getAutoCraftTargets().isEmpty()) {
            return false;
        }
        for (ItemStack target : template.getAutoCraftTargets()) {
            if (HopperRecipeUtil.matchesPrototype(moving, target)) {
                return false;
            }
        }
        for (ItemStack target : template.getAutoCraftTargets()) {
            for (ShapelessRecipe recipe : HopperRecipeUtil.findShapelessRecipes(target)) {
                if (matchesRecipeIngredient(hopperBlock, template, keys, moving, recipe.getChoiceList())) {
                    return true;
                }
            }
            for (ShapedRecipe recipe : HopperRecipeUtil.findShapedRecipes(target)) {
                Map<RecipeChoice, Integer> needed = HopperRecipeUtil.shapedIngredientCounts(recipe);
                for (RecipeChoice choice : needed.keySet()) {
                    if (HopperRecipeUtil.matchesChoice(choice, moving)
                            && template.allows(moving, hopperBlock, keys)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesRecipeIngredient(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                   ItemStack moving, List<RecipeChoice> choices) {
        if (choices == null) {
            return false;
        }
        for (RecipeChoice choice : choices) {
            if (HopperRecipeUtil.matchesChoice(choice, moving)
                    && template.allows(moving, hopperBlock, keys)) {
                return true;
            }
        }
        return false;
    }

    public Set<Integer> tryCraft(Block hopperBlock, HopperTemplate template, HopperKeys keys) {
        Set<Integer> reserved = new HashSet<>();
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER || template == null) {
            return reserved;
        }
        if (!template.isAutoCraftEnabled() || template.getAutoCraftTargets().isEmpty()) {
            return reserved;
        }
        if (!(hopperBlock.getState() instanceof Container container)) {
            return reserved;
        }
        Inventory inv = container.getInventory();

        for (ItemStack target : template.getAutoCraftTargets()) {
            for (ShapelessRecipe recipe : HopperRecipeUtil.findShapelessRecipes(target)) {
                Set<Integer> r = applyPlan(inv, hopperBlock, planShapeless(inv, hopperBlock, template, keys, recipe),
                        recipe.getResult());
                if (r != null) {
                    return r;
                }
            }
            for (ShapedRecipe recipe : HopperRecipeUtil.findShapedRecipes(target)) {
                Set<Integer> r = applyPlan(inv, hopperBlock, planShaped(inv, hopperBlock, template, keys, recipe),
                        recipe.getResult());
                if (r != null) {
                    return r;
                }
            }
        }
        return reserved;
    }

    private Set<Integer> applyPlan(Inventory inv, Block hopperBlock, CraftPlan plan, ItemStack result) {
        if (plan == null) {
            return null;
        }
        if (plan.canCraftNow()) {
            if (consumeSlots(inv, plan.slotsToConsume())) {
                if (result != null) {
                    ItemStack one = result.clone();
                    one.setAmount(1);
                    HopperContainerUtil.refund(hopperBlock, inv, one);
                }
                HopperContainerUtil.syncContainer(hopperBlock);
                return Set.of();
            }
            return null;
        }
        if (!plan.reservedSlots().isEmpty()) {
            return new HashSet<>(plan.reservedSlots());
        }
        return null;
    }

    private static CraftPlan planShaped(Inventory inv, Block hopperBlock, HopperTemplate template,
                                        HopperKeys keys, ShapedRecipe recipe) {
        Map<RecipeChoice, Integer> needed = HopperRecipeUtil.shapedIngredientCounts(recipe);
        if (needed.isEmpty()) {
            return null;
        }
        return planChoiceCounts(inv, hopperBlock, template, keys, needed);
    }

    private static CraftPlan planChoiceCounts(Inventory inv, Block hopperBlock, HopperTemplate template,
                                              HopperKeys keys, Map<RecipeChoice, Integer> needed) {
        List<Integer> consume = new ArrayList<>();
        List<Integer> used = new ArrayList<>();
        for (Map.Entry<RecipeChoice, Integer> entry : needed.entrySet()) {
            RecipeChoice choice = entry.getKey();
            int count = entry.getValue();
            for (int n = 0; n < count; n++) {
                Integer slot = findSlotForChoice(inv, hopperBlock, template, keys, choice, used);
                if (slot == null) {
                    Set<Integer> partial = collectPartial(inv, hopperBlock, template, keys, needed);
                    return partial.isEmpty() ? null : new CraftPlan(List.of(), partial, false);
                }
                used.add(slot);
                consume.add(slot);
            }
        }
        return new CraftPlan(consume, Set.of(), true);
    }

    private static Set<Integer> collectPartial(Inventory inv, Block hopperBlock, HopperTemplate template,
                                                 HopperKeys keys, Map<RecipeChoice, Integer> needed) {
        Set<Integer> partial = new HashSet<>();
        for (RecipeChoice choice : needed.keySet()) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                if (HopperRecipeUtil.matchesChoice(choice, stack) && template.allows(stack, hopperBlock, keys)) {
                    partial.add(i);
                }
            }
        }
        return partial;
    }

    private static CraftPlan planShapeless(Inventory inv, Block hopperBlock, HopperTemplate template,
                                           HopperKeys keys, ShapelessRecipe recipe) {
        List<RecipeChoice> choices = recipe.getChoiceList();
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Map<RecipeChoice, Integer> needed = new HashMap<>();
        for (RecipeChoice choice : choices) {
            needed.merge(choice, 1, Integer::sum);
        }
        return planChoiceCounts(inv, hopperBlock, template, keys, needed);
    }

    private static Integer findSlotForChoice(Inventory inv, Block hopperBlock, HopperTemplate template,
                                             HopperKeys keys, RecipeChoice choice, List<Integer> used) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (used.contains(i)) {
                continue;
            }
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            if (!HopperRecipeUtil.matchesChoice(choice, slot)) {
                continue;
            }
            if (!template.allows(slot, hopperBlock, keys)) {
                continue;
            }
            return i;
        }
        return null;
    }

    private static boolean consumeSlots(Inventory inv, List<Integer> slots) {
        for (int slot : slots) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null) {
                return false;
            }
            int amount = stack.getAmount() - 1;
            if (amount <= 0) {
                inv.setItem(slot, null);
            } else {
                ItemStack updated = stack.clone();
                updated.setAmount(amount);
                inv.setItem(slot, updated);
            }
        }
        return true;
    }

    private record CraftPlan(List<Integer> slotsToConsume, Set<Integer> reservedSlots, boolean canCraftNow) {
    }
}
