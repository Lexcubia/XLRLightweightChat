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

    public static ReverseTransferResult transferStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                                     HopperReservation reservation, int maxItem) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER
                || !HopperBlockConfig.isReverse(hopperBlock, keys) || template == null) {
            return new ReverseTransferResult(0, false);
        }
        if (!(hopperBlock.getState() instanceof Container hopperContainer)) {
            return new ReverseTransferResult(0, false);
        }
        Inventory hopperInv = hopperContainer.getInventory();
        Set<Integer> reserved = reservation.getReserved(hopperBlock.getLocation());

        int limit = Math.max(1, maxItem);
        int moved = 0;
        PushAttempt lastPushAttempt = PushAttempt.NO_WORK;
        for (int i = 0; i < limit; i++) {
            Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);
            Inventory aboveInv = HopperContainerUtil.getContainerInventory(aboveBlock);
            if (aboveInv == null) {
                lastPushAttempt = PushAttempt.NO_WORK;
                break;
            }
            PushAttempt attempt = tryPushOne(hopperInv, hopperBlock, aboveInv, aboveBlock, template, keys, reserved);
            lastPushAttempt = attempt;
            if (attempt != PushAttempt.MOVED) {
                break;
            }
            moved++;
        }
        if (moved > 0) {
            HopperContainerUtil.syncContainer(hopperBlock);
            return new ReverseTransferResult(moved, false);
        }
        if (lastPushAttempt == PushAttempt.TARGET_FULL) {
            return new ReverseTransferResult(0, true);
        }

        for (int i = 0; i < limit; i++) {
            Block belowBlock = hopperBlock.getRelative(BlockFace.DOWN);
            Inventory belowInv = HopperContainerUtil.getContainerInventory(belowBlock);
            if (belowInv == null || !tryPullOne(belowInv, belowBlock, hopperInv, hopperBlock, template, keys, reserved)) {
                break;
            }
            moved++;
        }
        if (moved > 0) {
            HopperContainerUtil.syncContainer(hopperBlock);
        }
        return new ReverseTransferResult(moved, false);
    }

    private static PushAttempt tryPushOne(Inventory hopperInv, Block hopperBlock, Inventory to, Block toBlock,
                                          HopperTemplate template, HopperKeys keys, Set<Integer> reserved) {
        boolean hasPushable = false;
        for (int i = 0; i < hopperInv.getSize(); i++) {
            if (reserved.contains(i)) {
                continue;
            }
            ItemStack slot = hopperInv.getItem(i);
            if (slot == null || slot.getType().isAir() || !template.allows(slot, hopperBlock, keys)) {
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

    private static boolean tryPullOne(Inventory from, Block fromBlock, Inventory hopperInv, Block hopperBlock,
                                      HopperTemplate template, HopperKeys keys, Set<Integer> reserved) {
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
            int countBefore = HopperContainerUtil.countSimilar(hopperInv, one);
            HashMap<Integer, ItemStack> leftover = hopperInv.addItem(one);
            if (!leftover.isEmpty()) {
                continue;
            }
            int countAfter = HopperContainerUtil.countSimilar(hopperInv, one);
            if (countAfter - countBefore < 1) {
                continue;
            }
            int newAmount = slot.getAmount() - 1;
            if (newAmount <= 0) {
                from.setItem(i, null);
            } else {
                ItemStack updated = slot.clone();
                updated.setAmount(newAmount);
                from.setItem(i, updated);
            }
            int fromBefore = slot.getAmount() + 1;
            int fromAfter = newAmount <= 0 ? 0 : newAmount;
            if (fromBefore - fromAfter != 1) {
                hopperInv.removeItem(one);
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
        if (amountBefore <= 1) {
            fromInv.setItem(fromSlot, slot.clone());
        } else {
            fromInv.setItem(fromSlot, slot.clone());
        }
        HopperContainerUtil.syncContainer(fromBlock);
        HopperContainerUtil.syncContainer(toBlock);
        return MoveResult.FAILED;
    }

    private static boolean hasHopperSpace(Inventory hopperInv, Set<Integer> reserved) {
        for (int i = 0; i < hopperInv.getSize(); i++) {
            if (reserved.contains(i)) {
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
