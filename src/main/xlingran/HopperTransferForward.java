package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Set;

/**
 * 正向传输：先 pull 上方，再 push 下方；由 tick 驱动，遵循等级 transfer-tick / max-item。
 */
public final class HopperTransferForward {

    private HopperTransferForward() {
    }

    public record ForwardTransferContext(HopperAutoCraftService craftService, HopperAutoSmeltService smeltService,
                                       XLRHopperConfig pluginConfig) {
    }

    public record ForwardTransferResult(int pulled, int pushed) {
    }

    public static int pullStep(Block hopperBlock, HopperTemplate template, HopperKeys keys, int maxItem) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER || template == null) {
            return 0;
        }
        if (HopperBlockConfig.isReverse(hopperBlock, keys)) {
            return 0;
        }
        if (!(hopperBlock.getState() instanceof Container hopperContainer)) {
            return 0;
        }
        Inventory hopperInv = hopperContainer.getInventory();
        Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);
        Inventory aboveInv = HopperContainerUtil.getContainerInventory(aboveBlock);
        if (aboveInv == null) {
            return 0;
        }
        int limit = Math.max(1, maxItem);
        int moved = 0;
        for (int i = 0; i < limit; i++) {
            if (!tryPullOne(aboveInv, aboveBlock, hopperInv, hopperBlock, template, keys)) {
                break;
            }
            moved++;
        }
        if (moved > 0) {
            HopperContainerUtil.syncContainer(hopperBlock);
            HopperContainerUtil.syncContainer(aboveBlock);
        }
        return moved;
    }

    public static int pushStep(Block hopperBlock, HopperTemplate template, HopperKeys keys, Set<Integer> reserved,
                               int maxItem, ForwardTransferContext context) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER || template == null) {
            return 0;
        }
        if (HopperBlockConfig.isReverse(hopperBlock, keys)) {
            return 0;
        }
        if (!(hopperBlock.getState() instanceof Container hopperContainer)) {
            return 0;
        }
        Inventory hopperInv = hopperContainer.getInventory();
        Block belowBlock = hopperBlock.getRelative(BlockFace.DOWN);
        Inventory belowInv = HopperContainerUtil.getContainerInventory(belowBlock);
        if (belowInv == null) {
            return 0;
        }
        int limit = Math.max(1, maxItem);
        int moved = 0;
        for (int i = 0; i < limit; i++) {
            if (!tryPushOne(hopperInv, hopperBlock, belowInv, belowBlock, template, keys, reserved, context)) {
                break;
            }
            moved++;
        }
        if (moved > 0) {
            HopperContainerUtil.syncContainer(hopperBlock);
            HopperContainerUtil.syncContainer(belowBlock);
        }
        return moved;
    }

    private static boolean tryPullOne(Inventory from, Block fromBlock, Inventory hopperInv, Block hopperBlock,
                                      HopperTemplate template, HopperKeys keys) {
        if (!hasHopperSpace(hopperInv, Set.of())) {
            return false;
        }
        for (int i = 0; i < from.getSize(); i++) {
            ItemStack slot = from.getItem(i);
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
                from.setItem(i, null);
            } else {
                ItemStack updated = slot.clone();
                updated.setAmount(expectedAfter);
                from.setItem(i, updated);
            }
            ItemStack verify = from.getItem(i);
            int actualAfter = verify == null || verify.getType().isAir() ? 0 : verify.getAmount();
            if (actualAfter != expectedAfter) {
                hopperInv.removeItem(one);
                from.setItem(i, slot);
                HopperContainerUtil.syncContainer(fromBlock);
                HopperContainerUtil.syncContainer(hopperBlock);
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean tryPushOne(Inventory hopperInv, Block hopperBlock, Inventory to, Block toBlock,
                                      HopperTemplate template, HopperKeys keys, Set<Integer> reserved,
                                      ForwardTransferContext context) {
        for (int i = 0; i < hopperInv.getSize(); i++) {
            if (reserved.contains(i)) {
                continue;
            }
            ItemStack slot = hopperInv.getItem(i);
            if (slot == null || slot.getType().isAir() || !template.allows(slot, hopperBlock, keys)) {
                continue;
            }
            if (shouldHoldForAutomation(hopperBlock, template, keys, slot, context)) {
                continue;
            }
            MoveResult result = moveOne(hopperInv, i, to, toBlock, hopperBlock);
            if (result == MoveResult.SUCCESS) {
                return true;
            }
            if (result == MoveResult.TARGET_FULL) {
                return false;
            }
        }
        return false;
    }

    private static boolean shouldHoldForAutomation(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                   ItemStack stack, ForwardTransferContext context) {
        if (context == null || context.pluginConfig() == null) {
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

    private enum MoveResult {
        SUCCESS,
        TARGET_FULL,
        FAILED
    }

    private static MoveResult moveOne(Inventory fromInv, int fromSlot, Inventory toInv, Block toBlock, Block fromBlock) {
        ItemStack slot = fromInv.getItem(fromSlot);
        if (slot == null || slot.getType().isAir()) {
            return MoveResult.FAILED;
        }
        ItemStack one = slot.clone();
        one.setAmount(1);
        HashMap<Integer, ItemStack> leftover = toInv.addItem(one);
        if (!leftover.isEmpty()) {
            return MoveResult.TARGET_FULL;
        }
        int amountBefore = slot.getAmount();
        if (amountBefore <= 1) {
            fromInv.setItem(fromSlot, null);
        } else {
            ItemStack updated = slot.clone();
            updated.setAmount(amountBefore - 1);
            fromInv.setItem(fromSlot, updated);
        }
        ItemStack verify = fromInv.getItem(fromSlot);
        int amountAfter = verify == null || verify.getType().isAir() ? 0 : verify.getAmount();
        int expectedAfter = amountBefore - 1;
        if ((verify == null || verify.getType().isAir()) && expectedAfter == 0) {
            return MoveResult.SUCCESS;
        }
        if (verify != null && verify.isSimilar(one) && amountAfter == expectedAfter) {
            return MoveResult.SUCCESS;
        }
        HopperContainerUtil.refund(toBlock, toInv, one);
        fromInv.setItem(fromSlot, slot);
        HopperContainerUtil.syncContainer(fromBlock);
        HopperContainerUtil.syncContainer(toBlock);
        return MoveResult.FAILED;
    }

    private static boolean hasHopperSpace(Inventory hopperInv, Set<Integer> reserved) {
        for (int i = 0; i < hopperInv.getSize(); i++) {
            if (reserved.contains(i)) {
                ItemStack slot = hopperInv.getItem(i);
                if (slot == null || slot.getType().isAir()) {
                    return true;
                }
                if (slot.getAmount() < slot.getMaxStackSize()) {
                    return true;
                }
                continue;
            }
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
}
