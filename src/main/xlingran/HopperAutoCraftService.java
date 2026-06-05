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

    private static final int MAX_CRAFTS_PER_CALL = 4;

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
        if (!(hopperBlock.getState() instanceof Container container)) {
            return false;
        }
        Inventory inv = container.getInventory();
        CraftPlan plan = peekBestPlan(inv, hopperBlock, template, keys);
        if (plan == null || !isMovingRecipeIngredient(hopperBlock, template, keys, moving)) {
            return false;
        }
        if (plan.canCraftNow()) {
            return true;
        }
        if (plan.needed() == null || plan.needed().isEmpty()) {
            return false;
        }
        return hasInsufficientMaterials(inv, hopperBlock, template, keys, plan.needed());
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

        for (int attempt = 0; attempt < MAX_CRAFTS_PER_CALL; attempt++) {
            Set<Integer> result = tryCraftOnce(inv, hopperBlock, template, keys);
            if (result == null) {
                return reserved;
            }
            if (!result.isEmpty()) {
                return result;
            }
            reserved = Set.of();
        }
        return reserved;
    }

    private Set<Integer> tryCraftOnce(Inventory inv, Block hopperBlock, HopperTemplate template, HopperKeys keys) {
        for (ItemStack target : template.getAutoCraftTargets()) {
            for (ShapelessRecipe recipe : HopperRecipeUtil.findShapelessRecipes(target)) {
                Set<Integer> r = applyPlan(inv, hopperBlock,
                        planShapeless(inv, hopperBlock, template, keys, recipe), recipe.getResult());
                if (r != null) {
                    return r;
                }
            }
            for (ShapedRecipe recipe : HopperRecipeUtil.findShapedRecipes(target)) {
                Set<Integer> r = applyPlan(inv, hopperBlock,
                        planShaped(inv, hopperBlock, template, keys, recipe), recipe.getResult());
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static CraftPlan peekBestPlan(Inventory inv, Block hopperBlock, HopperTemplate template, HopperKeys keys) {
        if (inv == null || template == null) {
            return null;
        }
        for (ItemStack target : template.getAutoCraftTargets()) {
            for (ShapelessRecipe recipe : HopperRecipeUtil.findShapelessRecipes(target)) {
                CraftPlan plan = planShapeless(inv, hopperBlock, template, keys, recipe);
                if (plan != null) {
                    return plan;
                }
            }
            for (ShapedRecipe recipe : HopperRecipeUtil.findShapedRecipes(target)) {
                CraftPlan plan = planShaped(inv, hopperBlock, template, keys, recipe);
                if (plan != null) {
                    return plan;
                }
            }
        }
        return null;
    }

    private static boolean isMovingRecipeIngredient(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                    ItemStack moving) {
        if (!template.allows(moving, hopperBlock, keys)) {
            return false;
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
                    if (HopperRecipeUtil.matchesChoice(choice, moving)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasInsufficientMaterials(Inventory inv, Block hopperBlock, HopperTemplate template,
                                                      HopperKeys keys, Map<RecipeChoice, Integer> needed) {
        for (Map.Entry<RecipeChoice, Integer> entry : needed.entrySet()) {
            if (countMatchingInInventory(inv, hopperBlock, template, keys, entry.getKey()) < entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    private static int countMatchingInInventory(Inventory inv, Block hopperBlock, HopperTemplate template,
                                                HopperKeys keys, RecipeChoice choice) {
        int total = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (HopperRecipeUtil.matchesChoice(choice, stack) && template.allows(stack, hopperBlock, keys)) {
                total += stack.getAmount();
            }
        }
        return total;
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
        Map<Integer, Integer> slotUsage = new HashMap<>();
        for (Map.Entry<RecipeChoice, Integer> entry : needed.entrySet()) {
            RecipeChoice choice = entry.getKey();
            int count = entry.getValue();
            for (int n = 0; n < count; n++) {
                Integer slot = findSlotForChoice(inv, hopperBlock, template, keys, choice, slotUsage);
                if (slot == null) {
                    Set<Integer> partial = collectPartial(inv, hopperBlock, template, keys, needed);
                    return partial.isEmpty() ? null : new CraftPlan(List.of(), partial, false, needed);
                }
                slotUsage.merge(slot, 1, Integer::sum);
                consume.add(slot);
            }
        }
        return new CraftPlan(consume, Set.of(), true, needed);
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
                                             HopperKeys keys, RecipeChoice choice, Map<Integer, Integer> slotUsage) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            int used = slotUsage.getOrDefault(i, 0);
            if (used >= slot.getAmount()) {
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

    private record CraftPlan(List<Integer> slotsToConsume, Set<Integer> reservedSlots, boolean canCraftNow,
                             Map<RecipeChoice, Integer> needed) {
    }
}
