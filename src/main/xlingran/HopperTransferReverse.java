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
 * 反向传输：每 8 tick 单步；先 push 上方，再 pull 下方；扣减校验。
 */
public final class HopperTransferReverse {

    private HopperTransferReverse() {
    }

    public record ReverseTransferResult(int moved, boolean pushTargetFull) {
    }

    public record ReverseTransferContext(HopperAutoCraftService craftService, HopperAutoSmeltService smeltService,
                                         XLRHopperConfig pluginConfig) {
    }

    private enum MoveResult {
        SUCCESS,
        TARGET_FULL,
        FAILED
    }

    private enum PushAttempt {
        MOVED,
        TARGET_FULL,
        NO_WORK
    }

    public static int pullStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                               HopperReservation reservation, int maxItem) {
        return pullStep(hopperBlock, template, keys, reservation, maxItem, null);
    }

    public static int pullStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                               HopperReservation reservation, int maxItem, ReverseTransferContext context) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER
                || !HopperBlockConfig.isReverse(hopperBlock, keys) || template == null) {
            return 0;
        }
        if (!(hopperBlock.getState() instanceof Container hopperContainer)) {
            return 0;
        }
        Inventory hopperInv = hopperContainer.getInventory();
        Set<Integer> reserved = reservation.getReserved(hopperBlock.getLocation());
        Block belowBlock = hopperBlock.getRelative(BlockFace.DOWN);
        Inventory belowInv = HopperContainerUtil.getContainerInventory(belowBlock);
        if (belowInv == null) {
            return 0;
        }
        int limit = Math.max(1, maxItem);
        int moved = 0;
        for (int i = 0; i < limit; i++) {
            if (!tryPullOne(belowInv, belowBlock, hopperInv, hopperBlock, template, keys, reserved, context)) {
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

    public static ReverseTransferResult pushStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                 HopperReservation reservation, int maxItem,
                                                 ReverseTransferContext context) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER
                || !HopperBlockConfig.isReverse(hopperBlock, keys) || template == null) {
            return new ReverseTransferResult(0, false);
        }
        if (!(hopperBlock.getState() instanceof Container hopperContainer)) {
            return new ReverseTransferResult(0, false);
        }
        Inventory hopperInv = hopperContainer.getInventory();
        Set<Integer> reserved = reservation.getReserved(hopperBlock.getLocation());
        Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);
        Inventory aboveInv = HopperContainerUtil.getContainerInventory(aboveBlock);
        if (aboveInv == null) {
            return new ReverseTransferResult(0, false);
        }
        int limit = Math.max(1, maxItem);
        int moved = 0;
        PushAttempt lastPushAttempt = PushAttempt.NO_WORK;
        for (int i = 0; i < limit; i++) {
            PushAttempt attempt = tryPushOne(hopperInv, hopperBlock, aboveInv, aboveBlock, template, keys, reserved,
                    context);
            lastPushAttempt = attempt;
            if (attempt != PushAttempt.MOVED) {
                break;
            }
            moved++;
        }
        if (moved > 0) {
            HopperContainerUtil.syncContainer(hopperBlock);
            HopperContainerUtil.syncContainer(aboveBlock);
            return new ReverseTransferResult(moved, false);
        }
        return new ReverseTransferResult(0, lastPushAttempt == PushAttempt.TARGET_FULL);
    }

    private static PushAttempt tryPushOne(Inventory hopperInv, Block hopperBlock, Inventory to, Block toBlock,
                                          HopperTemplate template, HopperKeys keys, Set<Integer> reserved,
                                          ReverseTransferContext context) {
        boolean hasPushable = false;
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
            hasPushable = true;
            MoveResult result = moveOne(hopperInv, i, to, toBlock, hopperBlock);
            if (result == MoveResult.SUCCESS) {
                HopperContainerUtil.syncContainer(hopperBlock);
                HopperContainerUtil.syncContainer(toBlock);
                return PushAttempt.MOVED;
            }
            if (result == MoveResult.TARGET_FULL) {
                return PushAttempt.TARGET_FULL;
            }
        }
        return hasPushable ? PushAttempt.NO_WORK : PushAttempt.NO_WORK;
    }

    private static boolean shouldThrottleSmeltInbound(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                      ItemStack stack, ReverseTransferContext context) {
        if (context == null || context.smeltService() == null || context.pluginConfig() == null) {
            return false;
        }
        return template.isAutoSmeltEnabled() && context.pluginConfig().isAutoSmeltEnabled()
                && context.smeltService().shouldThrottleInboundSmeltItem(hopperBlock, template, keys, stack);
    }

    private static boolean shouldHoldForAutomation(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                   ItemStack stack, ReverseTransferContext context) {
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

    private static boolean tryPullOne(Inventory from, Block fromBlock, Inventory hopperInv, Block hopperBlock,
                                      HopperTemplate template, HopperKeys keys, Set<Integer> reserved,
                                      ReverseTransferContext context) {
        if (!hasHopperSpace(hopperInv, reserved)) {
            return false;
        }
        for (int i = 0; i < from.getSize(); i++) {
            ItemStack slot = from.getItem(i);
            if (slot == null || slot.getType().isAir() || !template.allows(slot, hopperBlock, keys)) {
                continue;
            }
            ItemStack one = slot.clone();
            one.setAmount(1);
            if (shouldThrottleSmeltInbound(hopperBlock, template, keys, one, context)) {
                continue;
            }
            int amountBefore = slot.getAmount();
            int countBefore = HopperContainerUtil.countSimilar(hopperInv, one);
            HashMap<Integer, ItemStack> leftover = hopperInv.addItem(one);
            if (!leftover.isEmpty()) {
                continue;
            }
            int countAfter = HopperContainerUtil.countSimilar(hopperInv, one);
            if (countAfter - countBefore < 1) {
                hopperInv.removeItem(one);
                HopperContainerUtil.syncContainer(hopperBlock);
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
            HopperContainerUtil.syncContainer(fromBlock);
            HopperContainerUtil.syncContainer(hopperBlock);
            return true;
        }
        return false;
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
            ItemStack slot = hopperInv.getItem(i);
            if (reserved.contains(i)) {
                if (slot == null || slot.getType().isAir()) {
                    return true;
                }
                if (slot.getAmount() < slot.getMaxStackSize()) {
                    return true;
                }
                continue;
            }
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
