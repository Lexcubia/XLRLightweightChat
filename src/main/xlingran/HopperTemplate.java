package xlingran;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HopperTemplate {

    private boolean whitelist = true;
    private final Set<Material> materials = new HashSet<>();
    private final List<String> titleRules = new ArrayList<>();
    private final List<String> loreRules = new ArrayList<>();

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

    public boolean allows(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        boolean passItem = FilterItem.allows(stack, whitelist, materials);
        boolean passTitle = FilterTitle.allows(stack, titleRules);
        boolean passLore = FilterLore.allows(stack, loreRules);
        return passItem && passTitle && passLore;
    }
}
