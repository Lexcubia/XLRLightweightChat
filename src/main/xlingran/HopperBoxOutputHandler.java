package xlingran;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

/**
 * 双路按比例拆分：漏斗转出 N 个物品时，按下方/仓库剩余空间比例分配，总量仍为 N（不复制）。
 */
public class HopperBoxOutputHandler implements Listener {

    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final PlayerBoxManager boxManager;
    private final Runnable persistBoxes;

    public HopperBoxOutputHandler(HopperTemplateManager templateManager, HopperKeys keys,
                                  PlayerBoxManager boxManager, Runnable persistBoxes) {
        this.templateManager = templateManager;
        this.keys = keys;
        this.boxManager = boxManager;
        this.persistBoxes = persistBoxes;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (event.getSource().getType() != InventoryType.HOPPER) {
            return;
        }
        Inventory hopperInv = event.getSource();
        Block hopperBlock = getHopperBlock(hopperInv);
        if (hopperBlock == null) {
            return;
        }
        if (!isInventoryBelowHopper(hopperBlock, event.getDestination())) {
            return;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(hopperBlock, keys, templateManager);
        if (template == null) {
            return;
        }
        String boxName = template.getLinkedBoxName();
        if (boxName == null || boxName.isEmpty()) {
            return;
        }
        UUID owner = resolveOwner(hopperBlock);
        if (owner == null || !boxManager.hasBox(owner, boxName)) {
            return;
        }
        ItemStack moving = event.getItem();
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        if (!template.allows(moving, hopperBlock, keys)) {
            return;
        }

        Inventory dest = event.getDestination();
        int total = moving.getAmount();
        int belowCap = InventoryCapacity.maxFit(dest, moving);
        int boxCap = boxManager.maxFit(owner, boxName, moving);

        if (boxCap <= 0) {
            return;
        }
        if (belowCap >= total && boxCap <= 0) {
            return;
        }

        int sumCap = belowCap + boxCap;
        if (sumCap <= 0) {
            return;
        }

        event.setCancelled(true);
        int toMove = Math.min(total, sumCap);
        int toBelow = (int) ((long) toMove * belowCap / sumCap);
        int toBox = toMove - toBelow;
        if (toBelow > belowCap) {
            toBox += toBelow - belowCap;
            toBelow = belowCap;
        }
        if (toBox > boxCap) {
            int overflow = toBox - boxCap;
            toBox = boxCap;
            toBelow = Math.min(belowCap, toBelow + overflow);
        }

        removeFromHopper(hopperInv, moving, toMove);
        boolean boxChanged = false;

        if (toBelow > 0) {
            ItemStack forBelow = moving.clone();
            forBelow.setAmount(toBelow);
            HashMap<Integer, ItemStack> belowLeft = dest.addItem(forBelow);
            for (ItemStack left : belowLeft.values()) {
                returnToHopper(hopperInv, left);
            }
        }
        if (toBox > 0) {
            ItemStack forBox = moving.clone();
            forBox.setAmount(toBox);
            HashMap<Integer, ItemStack> boxLeft = boxManager.addItem(owner, boxName, forBox);
            boxChanged = true;
            for (ItemStack left : boxLeft.values()) {
                returnToHopper(hopperInv, left);
            }
        }

        if (boxChanged && persistBoxes != null) {
            persistBoxes.run();
        }
    }

    private static void removeFromHopper(Inventory hopperInv, ItemStack prototype, int amount) {
        int left = amount;
        for (int i = 0; i < hopperInv.getSize() && left > 0; i++) {
            ItemStack slot = hopperInv.getItem(i);
            if (slot == null || slot.getType().isAir() || !slot.isSimilar(prototype)) {
                continue;
            }
            int take = Math.min(left, slot.getAmount());
            slot.setAmount(slot.getAmount() - take);
            if (slot.getAmount() <= 0) {
                hopperInv.setItem(i, null);
            }
            left -= take;
        }
    }

    private static void returnToHopper(Inventory hopperInv, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> left = hopperInv.addItem(stack);
        if (!left.isEmpty() && hopperInv.getHolder() instanceof BlockState blockState) {
            for (ItemStack drop : left.values()) {
                blockState.getWorld().dropItemNaturally(blockState.getLocation().add(0.5, 0.5, 0.5), drop);
            }
        }
    }

    private UUID resolveOwner(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (!(state instanceof org.bukkit.block.TileState tileState)) {
            return null;
        }
        String ownerStr = tileState.getPersistentDataContainer().get(keys.owner,
                org.bukkit.persistence.PersistentDataType.STRING);
        if (ownerStr == null) {
            return null;
        }
        try {
            return UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isInventoryBelowHopper(Block hopper, Inventory other) {
        Block otherBlock = getInventoryBlock(other);
        return otherBlock != null && otherBlock.equals(hopper.getRelative(BlockFace.DOWN));
    }

    private static Block getHopperBlock(Inventory inventory) {
        if (inventory == null || inventory.getType() != InventoryType.HOPPER) {
            return null;
        }
        if (inventory.getHolder() instanceof BlockState blockState) {
            return blockState.getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }

    private static Block getInventoryBlock(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        if (inventory.getHolder() instanceof BlockState blockState) {
            return blockState.getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }
}
