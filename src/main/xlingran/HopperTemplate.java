package xlingran;

import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HopperTemplate {

    private boolean whitelist = false;
    private boolean autoDestroy;
    private boolean autoCraftEnabled;
    private boolean autoSmeltEnabled;
    private final List<ItemStack> autoCraftTargets = new ArrayList<>();
    private final List<ItemStack> autoSmeltOutputs = new ArrayList<>();
    private final List<ItemStack> filterPrototypes = new ArrayList<>();
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

    public boolean isAutoDestroy() {
        return autoDestroy;
    }

    public void setAutoDestroy(boolean autoDestroy) {
        this.autoDestroy = autoDestroy;
    }

    public void toggleAutoDestroy() {
        this.autoDestroy = !this.autoDestroy;
    }

    public boolean isAutoCraftEnabled() {
        return autoCraftEnabled;
    }

    public void setAutoCraftEnabled(boolean autoCraftEnabled) {
        this.autoCraftEnabled = autoCraftEnabled;
    }

    public void toggleAutoCraftEnabled() {
        this.autoCraftEnabled = !this.autoCraftEnabled;
    }

    public List<ItemStack> getAutoCraftTargets() {
        return autoCraftTargets;
    }

    public void setAutoCraftTargets(List<ItemStack> targets) {
        autoCraftTargets.clear();
        if (targets != null) {
            for (ItemStack stack : targets) {
                ItemStack proto = ItemStackUtil.clonePrototype(stack);
                if (proto != null) {
                    autoCraftTargets.add(proto);
                }
            }
        }
    }

    public boolean isAutoSmeltEnabled() {
        return autoSmeltEnabled;
    }

    public void setAutoSmeltEnabled(boolean autoSmeltEnabled) {
        this.autoSmeltEnabled = autoSmeltEnabled;
    }

    public void toggleAutoSmeltEnabled() {
        this.autoSmeltEnabled = !this.autoSmeltEnabled;
    }

    public List<ItemStack> getAutoSmeltOutputs() {
        return autoSmeltOutputs;
    }

    public void setAutoSmeltOutputs(List<ItemStack> outputs) {
        autoSmeltOutputs.clear();
        if (outputs != null) {
            for (ItemStack stack : outputs) {
                ItemStack proto = ItemStackUtil.clonePrototype(stack);
                if (proto != null) {
                    autoSmeltOutputs.add(proto);
                }
            }
        }
    }

    public List<ItemStack> getFilterPrototypes() {
        return filterPrototypes;
    }

    public void setFilterPrototypes(List<ItemStack> prototypes) {
        filterPrototypes.clear();
        if (prototypes != null) {
            for (ItemStack stack : prototypes) {
                ItemStack proto = ItemStackUtil.clonePrototype(stack);
                if (proto != null) {
                    filterPrototypes.add(proto);
                }
            }
        }
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

    public void setEnchantMinLevel(Enchantment enchant, int minLevel) {
        if (enchant == null || minLevel <= 0) {
            return;
        }
        enchantMinLevels.put(enchant, minLevel);
    }

    public void removeEnchantMinLevel(Enchantment enchant) {
        if (enchant != null) {
            enchantMinLevels.remove(enchant);
        }
    }

    public boolean allows(ItemStack stack, Block hopperBlock, HopperKeys keys) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        boolean effectiveWhitelist = HopperBlockConfig.getEffectiveWhitelist(hopperBlock, keys, this);
        boolean passItem = FilterItem.allows(stack, effectiveWhitelist, filterPrototypes);
        XLRHopperConfig cfg = Shan.getInstance() != null ? Shan.getInstance().getPluginConfig() : null;
        boolean passDurability = cfg == null || cfg.isFilterDurabilityEnabled()
                ? FilterDurability.allows(stack, durabilityThreshold)
                : true;
        boolean passEnchant = cfg == null || cfg.isFilterEnchanEnabled()
                ? FilterEnchant.allows(stack, enchantMinLevels)
                : true;
        return passItem && passDurability && passEnchant;
    }
}
