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

    /**
     * @return 本 tick 是否完成了一次物品位移
     */
    public static boolean transferStep(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                       HopperReservation reservation) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER
                || !HopperBlockConfig.isReverse(hopperBlock, keys) || template == null) {
            return false;
        }
        if (!(hopperBlock.getState() instanceof Container hopperContainer)) {
            return false;
        }
        Inventory hopperInv = hopperContainer.getInventory();
        Set<Integer> reserved = reservation.getReserved(hopperBlock.getLocation());

        Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);
        Inventory aboveInv = HopperContainerUtil.getContainerInventory(aboveBlock);
        if (aboveInv != null && tryPushOne(hopperInv, hopperBlock, aboveInv, aboveBlock, template, keys, reserved)) {
            HopperContainerUtil.syncContainer(hopperBlock);
            return true;
        }

        Block belowBlock = hopperBlock.getRelative(BlockFace.DOWN);
        Inventory belowInv = HopperContainerUtil.getContainerInventory(belowBlock);
        if (belowInv != null && tryPullOne(belowInv, belowBlock, hopperInv, hopperBlock, template, keys, reserved)) {
            HopperContainerUtil.syncContainer(hopperBlock);
            return true;
        }

        return false;
    }

    private static boolean tryPushOne(Inventory hopperInv, Block hopperBlock, Inventory to, Block toBlock,
                                      HopperTemplate template, HopperKeys keys, Set<Integer> reserved) {
        for (int i = 0; i < hopperInv.getSize(); i++) {
            if (reserved.contains(i)) {
                continue;
            }
            ItemStack slot = hopperInv.getItem(i);
            if (slot == null || slot.getType().isAir() || !template.allows(slot, hopperBlock, keys)) {
                continue;
            }
            if (moveOne(hopperInv, i, to, toBlock, hopperBlock)) {
                HopperContainerUtil.syncContainer(hopperBlock);
                HopperContainerUtil.syncContainer(toBlock);
                return true;
            }
        }
        return false;
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

    private static boolean moveOne(Inventory fromInv, int fromSlot, Inventory toInv, Block toBlock, Block fromBlock) {
        ItemStack slot = fromInv.getItem(fromSlot);
        if (slot == null || slot.getType().isAir()) {
            return false;
        }
        ItemStack one = slot.clone();
        one.setAmount(1);
        HashMap<Integer, ItemStack> leftover = toInv.addItem(one);
        if (!leftover.isEmpty()) {
            return false;
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
            return true;
        }
        if (verify != null && verify.isSimilar(one) && amountAfter == expectedAfter) {
            return true;
        }
        HopperContainerUtil.refund(toBlock, toInv, one);
        if (amountBefore <= 1) {
            fromInv.setItem(fromSlot, slot.clone());
        } else {
            fromInv.setItem(fromSlot, slot.clone());
        }
        HopperContainerUtil.syncContainer(fromBlock);
        HopperContainerUtil.syncContainer(toBlock);
        return false;
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
