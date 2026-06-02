package xlingran;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HopperTemplate {

    private boolean whitelist = true;
    private final Set<Material> materials = new HashSet<>();
    private final List<String> titleRules = new ArrayList<>();
    private final List<String> loreRules = new ArrayList<>();
    private Integer durabilityThreshold;
    private final Map<Enchantment, Integer> enchantMinLevels = new LinkedHashMap<>();

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public void toggleWhitelist() {
        this.whitelist = !this.whitelist;
    }

    public Set<Material> getMaterials() {
        return materials;
    }

    public void setMaterials(Set<Material> materials) {
        this.materials.clear();
        if (materials != null) {
            this.materials.addAll(materials);
        }
    }

    public List<String> getTitleRules() {
        return titleRules;
    }

    public List<String> getLoreRules() {
        return loreRules;
    }

    public Integer getDurabilityThreshold() {
        return durabilityThreshold;
    }

    public void setDurabilityThreshold(Integer durabilityThreshold) {
        this.durabilityThreshold = durabilityThreshold;
    }

    public Map<Enchantment, Integer> getEnchantMinLevels() {
        return enchantMinLevels;
    }

    public void addTitleRule(String rule) {
        if (rule != null && !rule.isBlank()) {
            titleRules.add(rule.trim());
        }
    }

    public void addLoreRule(String rule) {
        if (rule != null && !rule.isBlank()) {
            loreRules.add(rule.trim());
        }
    }

    public void setEnchantMinLevel(Enchantment enchant, int minLevel) {
        if (enchant == null || minLevel <= 0) {
            return;
        }
        enchantMinLevels.put(enchant, minLevel);
    }

    public boolean allows(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        boolean passItem = FilterItem.allows(stack, whitelist, materials);
        boolean passTitle = FilterTitle.allows(stack, titleRules);
        boolean passLore = FilterLore.allows(stack, loreRules);
        boolean passDurability = FilterDurability.allows(stack, durabilityThreshold);
        boolean passEnchant = FilterEnchant.allows(stack, enchantMinLevels);
        return passItem && passTitle && passLore && passDurability && passEnchant;
    }
}
