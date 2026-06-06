package xlingran;

import org.bukkit.Location;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动合成：每漏斗单 job，craft-tick 后产出 1 个并下入方箱子；每 8 tick 推进 8。
 */
public final class HopperAutoCraftService {

    public static final int TICK_STEP = 8;

    private final XLRHopperConfig pluginConfig;
    private final Map<String, CraftJob> jobs = new ConcurrentHashMap<>();

    public HopperAutoCraftService(XLRHopperConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    private int craftDurationTicks() {
        return pluginConfig != null ? pluginConfig.getCraftTick() : 20;
    }

    public Set<Integer> tick(Block hopperBlock, HopperTemplate template, HopperKeys keys) {
        return processCraft(hopperBlock, template, keys, true);
    }

    public Set<Integer> tryStartCraft(Block hopperBlock, HopperTemplate template, HopperKeys keys) {
        return processCraft(hopperBlock, template, keys, false);
    }

    public Set<Integer> getActiveReservedSlots(Location loc) {
        Set<Integer> reserved = new HashSet<>();
        if (loc == null || loc.getWorld() == null) {
            return reserved;
        }
        CraftJob job = jobs.get(laneKey(loc));
        if (job != null) {
            reserved.addAll(job.reservedSlots);
        }
        return reserved;
    }

    public boolean shouldHoldOutbound(Block hopperBlock, HopperTemplate template, HopperKeys keys, ItemStack moving) {
        if (hopperBlock == null || template == null || moving == null || moving.getType().isAir()) {
            return false;
        }
        if (!template.isAutoCraftEnabled() || template.getAutoCraftTargets().isEmpty()) {
            return false;
        }
        if (!(hopperBlock.getState() instanceof Container container)) {
            return false;
        }
        Location loc = hopperBlock.getLocation();
        Inventory inv = container.getInventory();
        if (!isCraftPipelineActive(inv, hopperBlock, template, keys, loc)) {
            return false;
        }
        if (matchesAnyCraftTargetPrototype(moving, template)) {
            return true;
        }
        return isMovingRecipeIngredient(hopperBlock, template, keys, moving);
    }

    private static boolean matchesAnyCraftTargetPrototype(ItemStack moving, HopperTemplate template) {
        for (ItemStack target : template.getAutoCraftTargets()) {
            if (HopperRecipeUtil.matchesPrototype(moving, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCraftPipelineActive(Inventory inv, Block hopperBlock, HopperTemplate template,
                                          HopperKeys keys, Location loc) {
        if (hasJob(loc)) {
            return true;
        }
        if (containsAnyRecipeIngredient(inv, hopperBlock, template, keys)) {
            return true;
        }
        for (ItemStack target : template.getAutoCraftTargets()) {
            for (ShapelessRecipe recipe : HopperRecipeUtil.findShapelessRecipes(target)) {
                if (isActivePlan(inv, hopperBlock, template, keys,
                        planShapeless(inv, hopperBlock, template, keys, recipe))) {
                    return true;
                }
            }
            for (ShapedRecipe recipe : HopperRecipeUtil.findShapedRecipes(target)) {
                if (isActivePlan(inv, hopperBlock, template, keys,
                        planShaped(inv, hopperBlock, template, keys, recipe))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isActivePlan(Inventory inv, Block hopperBlock, HopperTemplate template,
                                        HopperKeys keys, CraftPlan plan) {
        if (plan == null) {
            return false;
        }
        if (plan.canCraftNow()) {
            return true;
        }
        if (!plan.reservedSlots().isEmpty()) {
            return true;
        }
        Map<RecipeChoice, Integer> needed = plan.needed();
        return needed != null && !needed.isEmpty()
                && hasInsufficientMaterials(inv, hopperBlock, template, keys, needed);
    }

    public void clear(Location loc) {
        jobs.remove(laneKey(loc));
    }

    public boolean hasJob(Location loc) {
        return loc != null && loc.getWorld() != null && jobs.containsKey(laneKey(loc));
    }

    private Set<Integer> processCraft(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                      boolean advanceTimer) {
        Set<Integer> reserved = new HashSet<>();
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER || template == null) {
            return reserved;
        }
        if (pluginConfig != null && !pluginConfig.isAutoCraftEnabled()) {
            jobs.remove(laneKey(hopperBlock.getLocation()));
            return reserved;
        }
        if (!template.isAutoCraftEnabled() || template.getAutoCraftTargets().isEmpty()) {
            jobs.remove(laneKey(hopperBlock.getLocation()));
            return reserved;
        }
        if (!(hopperBlock.getState() instanceof Container container)) {
            return reserved;
        }
        Location loc = hopperBlock.getLocation();
        String key = laneKey(loc);
        Inventory inv = container.getInventory();

        CraftJob job = jobs.get(key);
        if (job != null) {
            if (advanceTimer) {
                job.ticksRemaining -= TICK_STEP;
                if (job.ticksRemaining <= 0) {
                    ItemStack output = job.outputStack.clone();
                    output.setAmount(1);
                    HopperContainerUtil.deliverAutomationOutput(hopperBlock, keys, output);
                    jobs.remove(key);
                    HopperContainerUtil.syncContainer(hopperBlock);
                    return reserved;
                }
            }
            reserved.addAll(job.reservedSlots);
            return reserved;
        }

        CraftMatch match = findCraftMatch(inv, hopperBlock, template, keys);
        if (match != null) {
            if (consumeSlots(inv, match.plan.slotsToConsume())) {
                ItemStack result = match.result.clone();
                result.setAmount(1);
                Set<Integer> slots = uniqueSlots(match.plan.slotsToConsume());
                jobs.put(key, new CraftJob(result, craftDurationTicks(), slots));
                reserved.addAll(slots);
                HopperContainerUtil.syncContainer(hopperBlock);
            }
            return reserved;
        }

        CraftPlan partial = peekBestPlan(inv, hopperBlock, template, keys);
        if (partial != null && !partial.canCraftNow() && !partial.reservedSlots().isEmpty()) {
            return new HashSet<>(partial.reservedSlots());
        }
        return reserved;
    }

    private static Set<Integer> uniqueSlots(List<Integer> slots) {
        return new HashSet<>(slots);
    }

    private static CraftMatch findCraftMatch(Inventory inv, Block hopperBlock, HopperTemplate template,
                                             HopperKeys keys) {
        for (ItemStack target : template.getAutoCraftTargets()) {
            for (ShapelessRecipe recipe : HopperRecipeUtil.findShapelessRecipes(target)) {
                CraftPlan plan = planShapeless(inv, hopperBlock, template, keys, recipe);
                if (plan != null && plan.canCraftNow()) {
                    return new CraftMatch(plan, recipe.getResult());
                }
            }
            for (ShapedRecipe recipe : HopperRecipeUtil.findShapedRecipes(target)) {
                CraftPlan plan = planShaped(inv, hopperBlock, template, keys, recipe);
                if (plan != null && plan.canCraftNow()) {
                    return new CraftMatch(plan, recipe.getResult());
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

    private static boolean containsAnyRecipeIngredient(Inventory inv, Block hopperBlock, HopperTemplate template,
                                                     HopperKeys keys) {
        if (inv == null) {
            return false;
        }
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (isMovingRecipeIngredient(hopperBlock, template, keys, stack)) {
                return true;
            }
        }
        return false;
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

    private static String laneKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private record CraftPlan(List<Integer> slotsToConsume, Set<Integer> reservedSlots, boolean canCraftNow,
                             Map<RecipeChoice, Integer> needed) {
    }

    private record CraftMatch(CraftPlan plan, ItemStack result) {
    }

    private static final class CraftJob {
        private final ItemStack outputStack;
        private final Set<Integer> reservedSlots;
        private int ticksRemaining;

        private CraftJob(ItemStack outputStack, int ticksRemaining, Set<Integer> reservedSlots) {
            this.outputStack = outputStack;
            this.ticksRemaining = ticksRemaining;
            this.reservedSlots = reservedSlots;
        }
    }
}
