package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xlingran.core.HopperLaneListener;

import java.util.HashMap;

public final class HopperContainerUtil {

    private HopperContainerUtil() {
    }

    public static Inventory getContainerInventory(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    public static void syncContainer(Block block) {
        if (block == null) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof Container) {
            state.update(true, false);
        }
    }

    public static void refund(Block block, Inventory inventory, ItemStack stack) {
        if (block == null || inventory == null || stack == null || stack.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> left = inventory.addItem(stack.clone());
        for (ItemStack drop : left.values()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }
    }

    /**
     * 自动合成/熔炼产物交付：反向吸取时产物先进入漏斗，由原版向上推送；正向则下入方容器。
     */
    public static boolean deliverAutomationOutput(Block hopperBlock, HopperKeys keys, ItemStack stack) {
        if (hopperBlock == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        if (keys != null && HopperBlockConfig.isReverse(hopperBlock, keys)) {
            return depositInHopper(hopperBlock, stack);
        }
        return deliverDownstream(hopperBlock, stack);
    }

    /**
     * 将物品填入漏斗库存；空间不足时掉落至漏斗位置。
     */
    public static boolean depositInHopper(Block hopperBlock, ItemStack stack) {
        if (hopperBlock == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        Inventory hopperInv = getContainerInventory(hopperBlock);
        if (hopperInv == null) {
            return false;
        }
        HashMap<Integer, ItemStack> left = hopperInv.addItem(stack.clone());
        syncContainer(hopperBlock);
        if (left.isEmpty()) {
            return true;
        }
        for (ItemStack drop : left.values()) {
            hopperBlock.getWorld().dropItemNaturally(hopperBlock.getLocation().add(0.5, 0.5, 0.5), drop);
        }
        return false;
    }

    /**
     * 优先将物品放入漏斗正上方容器；无容器或已满时回退填入漏斗。
     */
    public static boolean deliverUpstream(Block hopperBlock, ItemStack stack) {
        if (hopperBlock == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemStack remaining = stack.clone();
        Block above = hopperBlock.getRelative(BlockFace.UP);
        Inventory aboveInv = getContainerInventory(above);
        if (aboveInv != null) {
            HashMap<Integer, ItemStack> left = aboveInv.addItem(remaining);
            syncContainer(above);
            if (left.isEmpty()) {
                return true;
            }
            remaining = left.values().iterator().next();
        }
        Inventory hopperInv = getContainerInventory(hopperBlock);
        if (hopperInv != null) {
            refund(hopperBlock, hopperInv, remaining);
            syncContainer(hopperBlock);
        }
        return false;
    }

    /**
     * 优先将物品放入漏斗正下方容器；无容器或已满时回退填入漏斗。
     *
     * @return 是否全部交付到下方容器
     */
    public static boolean deliverDownstream(Block hopperBlock, ItemStack stack) {
        if (hopperBlock == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemStack remaining = stack.clone();
        Block below = hopperBlock.getRelative(BlockFace.DOWN);
        Inventory belowInv = getContainerInventory(below);
        if (belowInv != null) {
            HashMap<Integer, ItemStack> left = belowInv.addItem(remaining);
            syncContainer(below);
            if (left.isEmpty()) {
                wakeAdjacentReverseHopper(below, hopperBlock);
                return true;
            }
            remaining = left.values().iterator().next();
        }
        Inventory hopperInv = getContainerInventory(hopperBlock);
        if (hopperInv != null) {
            refund(hopperBlock, hopperInv, remaining);
            syncContainer(hopperBlock);
        }
        return false;
    }

    private static void wakeAdjacentReverseHopper(Block below, Block hopperBlock) {
        Shan plugin = Shan.getInstance();
        if (plugin == null) {
            return;
        }
        HopperKeys keys = plugin.getHopperKeys();
        HopperLaneListener laneListener = plugin.getHopperLaneListener();
        if (keys == null || laneListener == null) {
            return;
        }
        if (below.getType() == Material.HOPPER && HopperBlockConfig.isReverse(below, keys)) {
            laneListener.scheduleEvaluateImmediate(below);
        }
        Block aboveBelow = below.getRelative(BlockFace.UP);
        if (aboveBelow.equals(hopperBlock) && hopperBlock.getType() == Material.HOPPER
                && HopperBlockConfig.isReverse(hopperBlock, keys)) {
            laneListener.scheduleEvaluateImmediate(hopperBlock);
        }
    }

    static int countSimilar(Inventory inventory, ItemStack prototype) {
        if (inventory == null || prototype == null || prototype.getType().isAir()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot != null && !slot.getType().isAir() && slot.isSimilar(prototype)) {
                total += slot.getAmount();
            }
        }
        return total;
    }
}
