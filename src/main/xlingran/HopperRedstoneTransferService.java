package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xlingran.gui.HopperLevelDef;
import xlingran.gui.UpdateConfig;

import java.util.Collection;
import java.util.HashMap;

/**
 * 红石名单漏斗被原版充能锁定时，由插件辅助完成过滤允许的吸取（地上物品 + 上方容器）。
 */
public final class HopperRedstoneTransferService {

    private HopperRedstoneTransferService() {
    }

    /**
     * @return 本步是否移动了至少 1 件物品
     */
    public static boolean transferStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                       XLRHopperConfig pluginConfig, int maxItem) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER || template == null
                || pluginConfig == null || !pluginConfig.isRedstoneToggleEnabled()) {
            return false;
        }
        HopperBlockConfig config = HopperBlockConfig.read(hopperBlock, keys);
        if (!config.isRedstoneListToggle() || !hopperBlock.isBlockPowered()) {
            return false;
        }
        if (!(hopperBlock.getState() instanceof Container hopperContainer)) {
            return false;
        }
        Inventory hopperInv = hopperContainer.getInventory();
        int limit = Math.max(1, maxItem);
        int moved = 0;
        if (tryPickupGround(hopperBlock, hopperInv, template, keys, limit - moved)) {
            moved++;
        }
        for (int i = moved; i < limit; i++) {
            if (!tryPullFromAbove(hopperBlock, hopperInv, template, keys)) {
                break;
            }
            moved++;
        }
        if (moved > 0) {
            HopperContainerUtil.syncContainer(hopperBlock);
        }
        return moved > 0;
    }

    private static boolean tryPickupGround(Block hopperBlock, Inventory hopperInv, HopperTemplate template,
                                           HopperKeys keys, int maxPickup) {
        if (maxPickup <= 0 || !hasHopperSpace(hopperInv)) {
            return false;
        }
        Collection<Entity> nearby = hopperBlock.getWorld().getNearbyEntities(
                hopperBlock.getLocation().add(0.5, 0.5, 0.5), 0.6, 0.6, 0.6,
                entity -> entity instanceof Item);
        for (Entity entity : nearby) {
            if (!(entity instanceof Item)) {
                continue;
            }
            Item itemEntity = (Item) entity;
            ItemStack stack = itemEntity.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!template.allows(stack, hopperBlock, keys)) {
                continue;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            HashMap<Integer, ItemStack> leftover = hopperInv.addItem(one);
            if (!leftover.isEmpty()) {
                continue;
            }
            int remaining = stack.getAmount() - 1;
            if (remaining <= 0) {
                itemEntity.remove();
            } else {
                stack.setAmount(remaining);
                itemEntity.setItemStack(stack);
            }
            return true;
        }
        return false;
    }

    private static boolean tryPullFromAbove(Block hopperBlock, Inventory hopperInv, HopperTemplate template,
                                            HopperKeys keys) {
        if (!hasHopperSpace(hopperInv)) {
            return false;
        }
        Block above = hopperBlock.getRelative(BlockFace.UP);
        Inventory aboveInv = HopperContainerUtil.getContainerInventory(above);
        if (aboveInv == null) {
            return false;
        }
        for (int i = 0; i < aboveInv.getSize(); i++) {
            ItemStack slot = aboveInv.getItem(i);
            if (slot == null || slot.getType().isAir() || !template.allows(slot, hopperBlock, keys)) {
                continue;
            }
            ItemStack one = slot.clone();
            one.setAmount(1);
            int amountBefore = slot.getAmount();
            HashMap<Integer, ItemStack> leftover = hopperInv.addItem(one);
            if (!leftover.isEmpty()) {
                continue;
            }
            int expectedAfter = amountBefore - 1;
            if (expectedAfter <= 0) {
                aboveInv.setItem(i, null);
            } else {
                ItemStack updated = slot.clone();
                updated.setAmount(expectedAfter);
                aboveInv.setItem(i, updated);
            }
            ItemStack verify = aboveInv.getItem(i);
            int actualAfter = verify == null || verify.getType().isAir() ? 0 : verify.getAmount();
            if (actualAfter != expectedAfter) {
                hopperInv.removeItem(one);
                aboveInv.setItem(i, slot);
                HopperContainerUtil.syncContainer(above);
                HopperContainerUtil.syncContainer(hopperBlock);
                continue;
            }
            HopperContainerUtil.syncContainer(above);
            return true;
        }
        return false;
    }

    private static boolean hasHopperSpace(Inventory hopperInv) {
        for (int i = 0; i < hopperInv.getSize(); i++) {
            ItemStack slot = hopperInv.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                return true;
            }
            if (slot.getAmount() < slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public static int resolveMaxItem(Block hopperBlock, HopperKeys keys, UpdateConfig updateConfig) {
        HopperLevelDef def = HopperLevelResolver.resolveForBlock(hopperBlock, keys, updateConfig);
        return def != null ? Math.max(1, def.maxItem()) : 1;
    }
}
