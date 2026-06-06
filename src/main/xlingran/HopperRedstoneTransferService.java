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
import java.util.Set;

/**
 * 红石名单漏斗被原版充能锁定时，由插件辅助完成过滤允许的吸取（地上物品 + 上方容器）。
 */
public final class HopperRedstoneTransferService {

    private HopperRedstoneTransferService() {
    }

    public record RedstoneTransferContext(HopperAutoCraftService craftService, HopperAutoSmeltService smeltService,
                                          XLRHopperConfig pluginConfig, Set<Integer> reserved) {
    }

    /**
     * 吸取阶段：地上物品 + 上方容器（每 8 tick 执行，不受 transfer-tick 门控）。
     */
    public static int absorbStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                 XLRHopperConfig pluginConfig, int maxItem) {
        return absorbStep(hopperBlock, template, keys, pluginConfig, maxItem, null);
    }

    public static int absorbStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                 XLRHopperConfig pluginConfig, int maxItem, HopperAutoSmeltService smeltService) {
        if (!canRun(hopperBlock, template, keys, pluginConfig)) {
            return 0;
        }
        Container hopperContainer = (Container) hopperBlock.getState();
        Inventory hopperInv = hopperContainer.getInventory();
        int limit = Math.max(1, maxItem);
        int moved = 0;
        if (tryPickupGround(hopperBlock, hopperInv, template, keys, smeltService, pluginConfig)) {
            moved++;
        }
        for (int i = moved; i < limit; i++) {
            if (!tryPullFromAbove(hopperBlock, hopperInv, template, keys, smeltService, pluginConfig)) {
                break;
            }
            moved++;
        }
        if (moved > 0) {
            HopperContainerUtil.syncContainer(hopperBlock);
        }
        return moved;
    }

    /**
     * 下推阶段：过滤允许且非合成/熔炼 hold 的物品（充能锁时替代原版 push）。
     */
    public static int pushStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                               XLRHopperConfig pluginConfig, int maxItem, RedstoneTransferContext context) {
        if (!canRun(hopperBlock, template, keys, pluginConfig)) {
            return 0;
        }
        Container hopperContainer = (Container) hopperBlock.getState();
        Inventory hopperInv = hopperContainer.getInventory();
        int limit = Math.max(1, maxItem);
        int moved = 0;
        for (int i = 0; i < limit; i++) {
            if (!tryPushBelow(hopperBlock, hopperInv, template, keys, context)) {
                break;
            }
            moved++;
        }
        if (moved > 0) {
            HopperContainerUtil.syncContainer(hopperBlock);
        }
        return moved;
    }

    private static boolean canRun(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                XLRHopperConfig pluginConfig) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER || template == null
                || keys == null || pluginConfig == null || !pluginConfig.isRedstoneToggleEnabled()) {
            return false;
        }
        HopperBlockConfig config = HopperBlockConfig.read(hopperBlock, keys);
        return config.isRedstoneListToggle() && hopperBlock.isBlockPowered()
                && hopperBlock.getState() instanceof Container;
    }

    public static boolean isRedstonePoweredTransferActive(Block hopperBlock, HopperKeys keys,
                                                          XLRHopperConfig pluginConfig) {
        if (hopperBlock == null || pluginConfig == null || !pluginConfig.isRedstoneToggleEnabled()) {
            return false;
        }
        HopperBlockConfig config = HopperBlockConfig.read(hopperBlock, keys);
        return config.isRedstoneListToggle() && hopperBlock.isBlockPowered();
    }

    private static boolean tryPushBelow(Block hopperBlock, Inventory hopperInv, HopperTemplate template,
                                        HopperKeys keys, RedstoneTransferContext context) {
        Block below = hopperBlock.getRelative(BlockFace.DOWN);
        Inventory belowInv = HopperContainerUtil.getContainerInventory(below);
        if (belowInv == null) {
            return false;
        }
        Set<Integer> reserved = context != null && context.reserved() != null ? context.reserved() : Set.of();
        for (int i = 0; i < hopperInv.getSize(); i++) {
            if (reserved.contains(i)) {
                continue;
            }
            ItemStack slot = hopperInv.getItem(i);
            if (slot == null || slot.getType().isAir() || !template.allows(slot, hopperBlock, keys)) {
                continue;
            }
            if (context != null && shouldHoldForAutomation(hopperBlock, template, keys, slot, context)) {
                continue;
            }
            ItemStack one = slot.clone();
            one.setAmount(1);
            int amountBefore = slot.getAmount();
            HashMap<Integer, ItemStack> leftover = belowInv.addItem(one);
            if (!leftover.isEmpty()) {
                continue;
            }
            int expectedAfter = amountBefore - 1;
            if (expectedAfter <= 0) {
                hopperInv.setItem(i, null);
            } else {
                ItemStack updated = slot.clone();
                updated.setAmount(expectedAfter);
                hopperInv.setItem(i, updated);
            }
            ItemStack verify = hopperInv.getItem(i);
            int actualAfter = verify == null || verify.getType().isAir() ? 0 : verify.getAmount();
            if (actualAfter != expectedAfter) {
                hopperInv.setItem(i, slot);
                belowInv.removeItem(one);
                HopperContainerUtil.syncContainer(below);
                HopperContainerUtil.syncContainer(hopperBlock);
                continue;
            }
            HopperContainerUtil.syncContainer(below);
            return true;
        }
        return false;
    }

    private static boolean tryPickupGround(Block hopperBlock, Inventory hopperInv, HopperTemplate template,
                                           HopperKeys keys, HopperAutoSmeltService smeltService,
                                           XLRHopperConfig pluginConfig) {
        if (!hasHopperSpace(hopperInv)) {
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
            if (shouldThrottleSmeltInbound(hopperBlock, template, keys, one, smeltService, pluginConfig)) {
                continue;
            }
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
                                            HopperKeys keys, HopperAutoSmeltService smeltService,
                                            XLRHopperConfig pluginConfig) {
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
            if (shouldThrottleSmeltInbound(hopperBlock, template, keys, one, smeltService, pluginConfig)) {
                continue;
            }
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

    private static boolean shouldThrottleSmeltInbound(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                      ItemStack stack, HopperAutoSmeltService smeltService,
                                                      XLRHopperConfig pluginConfig) {
        if (smeltService == null || pluginConfig == null) {
            return false;
        }
        return template.isAutoSmeltEnabled() && pluginConfig.isAutoSmeltEnabled()
                && smeltService.shouldThrottleInboundSmeltItem(hopperBlock, template, keys, stack);
    }

    private static boolean shouldHoldForAutomation(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                   ItemStack stack, RedstoneTransferContext context) {
        if (context.pluginConfig() == null) {
            return false;
        }
        if (template.isAutoCraftEnabled() && context.pluginConfig().isAutoCraftEnabled()
                && context.craftService() != null
                && context.craftService().shouldHoldOutbound(hopperBlock, template, keys, stack)) {
            return true;
        }
        return template.isAutoSmeltEnabled() && context.pluginConfig().isAutoSmeltEnabled()
                && context.smeltService() != null
                && context.smeltService().shouldHoldOutbound(hopperBlock, template, keys, stack);
    }

    public static int resolveMaxItem(Block hopperBlock, HopperKeys keys, UpdateConfig updateConfig) {
        HopperLevelDef def = HopperLevelResolver.resolveForBlock(hopperBlock, keys, updateConfig);
        return def != null ? Math.max(1, def.maxItem()) : 1;
    }
}
