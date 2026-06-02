package xlingran;

import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HopperTemplate {

    private boolean whitelist = true;
    private boolean reverseSuction;
    private boolean redstoneListToggle;
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

    public boolean isReverseSuction() {
        return reverseSuction;
    }

    public void setReverseSuction(boolean reverseSuction) {
        this.reverseSuction = reverseSuction;
    }

    public void toggleReverseSuction() {
        this.reverseSuction = !this.reverseSuction;
    }

    public boolean isRedstoneListToggle() {
        return redstoneListToggle;
    }

    public void setRedstoneListToggle(boolean redstoneListToggle) {
        this.redstoneListToggle = redstoneListToggle;
    }

    public void toggleRedstoneListToggle() {
        this.redstoneListToggle = !this.redstoneListToggle;
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

    public boolean getEffectiveWhitelist(Block hopperBlock) {
        if (redstoneListToggle && hopperBlock != null) {
            return hopperBlock.isBlockPowered();
        }
        return whitelist;
    }

    public boolean allows(ItemStack stack, Block hopperBlock) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        boolean effectiveWhitelist = getEffectiveWhitelist(hopperBlock);
        boolean passItem = FilterItem.allows(stack, effectiveWhitelist, filterPrototypes);
        boolean passDurability = FilterDurability.allows(stack, durabilityThreshold);
        boolean passEnchant = FilterEnchant.allows(stack, enchantMinLevels);
        return passItem && passDurability && passEnchant;
    }
}
