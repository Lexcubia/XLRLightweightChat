package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HopperRecipeUtil {

    private HopperRecipeUtil() {
    }

    static SmeltMapping findSmeltMapping(ItemStack outputPrototype) {
        List<SmeltMapping> mappings = findAllSmeltMappings(outputPrototype);
        return mappings.isEmpty() ? null : mappings.get(0);
    }

    static List<SmeltMapping> findAllSmeltMappings(ItemStack outputPrototype) {
        List<SmeltMapping> list = new ArrayList<>();
        if (outputPrototype == null || outputPrototype.getType().isAir()) {
            return list;
        }
        ItemStack probe = outputPrototype.clone();
        probe.setAmount(1);
        Set<String> seenInputs = new LinkedHashSet<>();
        for (Recipe recipe : Bukkit.getRecipesFor(probe)) {
            if (!(recipe instanceof CookingRecipe<?> cooking)) {
                continue;
            }
            ItemStack result = cooking.getResult();
            if (result == null || result.getType().isAir()) {
                continue;
            }
            if (!matchesPrototype(result, outputPrototype)) {
                continue;
            }
            RecipeChoice inputChoice = cooking.getInputChoice();
            if (inputChoice == null) {
                continue;
            }
            String key = inputChoiceKey(inputChoice);
            if (!seenInputs.add(key)) {
                continue;
            }
            list.add(new SmeltMapping(inputChoice, result.clone()));
        }
        return list;
    }

    private static String inputChoiceKey(RecipeChoice choice) {
        return choice.toString();
    }

    static boolean matchesPrototype(ItemStack stack, ItemStack prototype) {
        if (stack == null || prototype == null) {
            return false;
        }
        if (stack.getType() != prototype.getType()) {
            return false;
        }
        return FilterItemMatcher.matches(stack, prototype) && FilterItemMatcher.matches(prototype, stack);
    }

    static boolean matchesChoice(RecipeChoice choice, ItemStack stack) {
        if (choice == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        return choice.test(stack);
    }

    static List<ShapedRecipe> findShapedRecipes(ItemStack resultPrototype) {
        List<ShapedRecipe> list = new ArrayList<>();
        if (resultPrototype == null || resultPrototype.getType().isAir()) {
            return list;
        }
        ItemStack probe = resultPrototype.clone();
        probe.setAmount(1);
        for (Recipe recipe : Bukkit.getRecipesFor(probe)) {
            if (recipe instanceof ShapedRecipe shaped) {
                ItemStack result = shaped.getResult();
                if (result != null && matchesPrototype(result, resultPrototype)) {
                    list.add(shaped);
                }
            }
        }
        return list;
    }

    static Map<RecipeChoice, Integer> shapedIngredientCounts(ShapedRecipe shaped) {
        Map<RecipeChoice, Integer> counts = new HashMap<>();
        String[] shape = shaped.getShape();
        Map<Character, RecipeChoice> choiceMap = shaped.getChoiceMap();
        if (shape == null || choiceMap == null) {
            return counts;
        }
        for (String row : shape) {
            if (row == null) {
                continue;
            }
            for (char c : row.toCharArray()) {
                if (c == ' ') {
                    continue;
                }
                RecipeChoice choice = choiceMap.get(c);
                if (choice != null) {
                    counts.merge(choice, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    static List<ShapelessRecipe> findShapelessRecipes(ItemStack resultPrototype) {
        List<ShapelessRecipe> list = new ArrayList<>();
        if (resultPrototype == null || resultPrototype.getType().isAir()) {
            return list;
        }
        ItemStack probe = resultPrototype.clone();
        probe.setAmount(1);
        for (Recipe recipe : Bukkit.getRecipesFor(probe)) {
            if (recipe instanceof ShapelessRecipe shapeless) {
                ItemStack result = shapeless.getResult();
                if (result != null && matchesPrototype(result, resultPrototype)) {
                    list.add(shapeless);
                }
            }
        }
        return list;
    }

    record SmeltMapping(RecipeChoice inputChoice, ItemStack resultStack) {
    }
}
