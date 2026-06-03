package xlingran;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

/**
 * 双路按比例拆分：在事件下一 tick 从方块实体库存扣减并分配，避免事件内改库存不生效导致刷物品。
 */
public class HopperBoxOutputHandler implements Listener {

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final PlayerBoxManager boxManager;
    private final Runnable persistBoxes;

    public HopperBoxOutputHandler(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys,
                                  PlayerBoxManager boxManager, Runnable persistBoxes) {
        this.plugin = plugin;
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
        Block hopperBlock = getHopperBlock(event.getSource());
        if (hopperBlock == null) {
            return;
        }
        Block belowBlock = hopperBlock.getRelative(BlockFace.DOWN);
        if (!(belowBlock.getState() instanceof Container) || !isBlockInventory(event.getDestination(), belowBlock)) {
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

        int boxCap = boxManager.maxFit(owner, boxName, moving);
        if (boxCap <= 0) {
            return;
        }

        event.setCancelled(true);

        Location hopperLoc = hopperBlock.getLocation().clone();
        Location belowLoc = belowBlock.getLocation().clone();
        ItemStack snapshot = moving.clone();
        int planAmount = snapshot.getAmount();

        plugin.getServer().getScheduler().runTask(plugin, () -> executeSplit(
                hopperLoc, belowLoc, owner, boxName, snapshot, planAmount));
    }

    private void executeSplit(Location hopperLoc, Location belowLoc, UUID owner, String boxName,
                              ItemStack prototype, int planAmount) {
        Block hopperBlock = hopperLoc.getBlock();
        if (hopperBlock.getType() != org.bukkit.Material.HOPPER) {
            return;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(hopperBlock, keys, templateManager);
        if (template == null || !template.allows(prototype, hopperBlock, keys)) {
            return;
        }

        Inventory hopperInv = getContainerInventory(hopperBlock);
        if (hopperInv == null) {
            return;
        }

        Block belowBlock = belowLoc.getBlock();
        Inventory belowInv = getContainerInventory(belowBlock);
        if (belowInv == null) {
            return;
        }

        int belowCap = InventoryCapacity.maxFit(belowInv, prototype);
        int boxCap = boxManager.maxFit(owner, boxName, prototype);
        if (boxCap <= 0) {
            return;
        }
        int sumCap = belowCap + boxCap;
        if (sumCap <= 0) {
            return;
        }

        int planMove = Math.min(planAmount, sumCap);
        int removed = removeFromHopper(hopperInv, prototype, planMove);
        if (removed <= 0) {
            return;
        }

        int toBelow = splitAmount(removed, belowCap, boxCap);
        int toBox = removed - toBelow;
        toBelow = Math.min(toBelow, belowCap);
        toBox = Math.min(toBox, boxCap);
        if (toBelow + toBox > removed) {
            toBox = removed - toBelow;
        }

        boolean boxChanged = false;
        int distributed = 0;

        if (toBelow > 0) {
            ItemStack forBelow = prototype.clone();
            forBelow.setAmount(toBelow);
            int addedBelow = addToInventory(belowInv, forBelow);
            distributed += addedBelow;
            if (addedBelow < toBelow) {
                ItemStack refund = forBelow.clone();
                refund.setAmount(toBelow - addedBelow);
                returnToHopper(hopperInv, refund, hopperBlock);
            }
        }
        if (toBox > 0) {
            ItemStack forBox = prototype.clone();
            forBox.setAmount(toBox);
            HashMap<Integer, ItemStack> boxLeft = boxManager.addItem(owner, boxName, forBox);
            boxChanged = true;
            int addedBox = toBox;
            for (ItemStack left : boxLeft.values()) {
                addedBox -= left.getAmount();
                returnToHopper(hopperInv, left, hopperBlock);
            }
            distributed += addedBox;
        }

        int notPlaced = removed - distributed;
        if (notPlaced > 0) {
            ItemStack refund = prototype.clone();
            refund.setAmount(notPlaced);
            returnToHopper(hopperInv, refund, hopperBlock);
        }

        syncContainer(hopperBlock);
        syncContainer(belowBlock);
        if (boxChanged && persistBoxes != null) {
            persistBoxes.run();
        }
    }

    private static int addToInventory(Inventory inventory, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        int wanted = stack.getAmount();
        HashMap<Integer, ItemStack> left = inventory.addItem(stack);
        int remaining = wanted;
        for (ItemStack l : left.values()) {
            remaining -= l.getAmount();
        }
        return remaining;
    }

    private static int splitAmount(int removed, int belowCap, int boxCap) {
        if (removed <= 0) {
            return 0;
        }
        if (boxCap <= 0) {
            return Math.min(removed, belowCap);
        }
        if (belowCap <= 0) {
            return 0;
        }
        return (int) ((long) removed * belowCap / (belowCap + boxCap));
    }

    private static int removeFromHopper(Inventory hopperInv, ItemStack prototype, int amount) {
        int left = amount;
        int removed = 0;
        for (int i = 0; i < hopperInv.getSize() && left > 0; i++) {
            ItemStack slot = hopperInv.getItem(i);
            if (!slotMatchesForRemoval(slot, prototype)) {
                continue;
            }
            int take = Math.min(left, slot.getAmount());
            int newAmount = slot.getAmount() - take;
            if (newAmount <= 0) {
                hopperInv.setItem(i, null);
            } else {
                ItemStack updated = slot.clone();
                updated.setAmount(newAmount);
                hopperInv.setItem(i, updated);
            }
            left -= take;
            removed += take;
        }
        return removed;
    }

    private static boolean slotMatchesForRemoval(ItemStack slot, ItemStack prototype) {
        if (slot == null || slot.getType().isAir() || prototype == null || prototype.getType().isAir()) {
            return false;
        }
        if (slot.isSimilar(prototype)) {
            return true;
        }
        return slot.getType() == prototype.getType()
                && FilterItemMatcher.matches(slot, prototype)
                && FilterItemMatcher.matches(prototype, slot);
    }

    private static Inventory getContainerInventory(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    private static void syncContainer(Block block) {
        if (block == null) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof Container) {
            state.update(true, false);
        }
    }

    private static void returnToHopper(Inventory hopperInv, ItemStack stack, Block hopperBlock) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> left = hopperInv.addItem(stack);
        if (!left.isEmpty()) {
            for (ItemStack drop : left.values()) {
                hopperBlock.getWorld().dropItemNaturally(hopperBlock.getLocation().add(0.5, 0.5, 0.5), drop);
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

    private static boolean isBlockInventory(Inventory inventory, Block expectedBlock) {
        if (inventory == null || expectedBlock == null) {
            return false;
        }
        Block invBlock = getInventoryBlock(inventory);
        return invBlock != null && invBlock.equals(expectedBlock);
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
