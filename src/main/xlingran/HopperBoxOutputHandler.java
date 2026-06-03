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
 * 双路同时传输：漏斗向正下方容器每成功转出一份，同时向链接仓库存入一份相同物品（总量为下方的 2 倍，受仓库容量限制）。
 */
public class HopperBoxOutputHandler implements Listener {

    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final PlayerBoxManager boxManager;

    public HopperBoxOutputHandler(HopperTemplateManager templateManager, HopperKeys keys, PlayerBoxManager boxManager) {
        this.templateManager = templateManager;
        this.keys = keys;
        this.boxManager = boxManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (event.getSource().getType() != InventoryType.HOPPER) {
            return;
        }
        Block hopperBlock = getHopperBlock(event.getSource());
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
        ItemStack moved = event.getItem();
        if (moved == null || moved.getType().isAir()) {
            return;
        }
        if (!template.allows(moved, hopperBlock, keys)) {
            return;
        }

        ItemStack copy = moved.clone();
        HashMap<Integer, ItemStack> leftover = boxManager.addItem(owner, boxName, copy);
        for (ItemStack left : leftover.values()) {
            dropNearHopper(hopperBlock, left);
        }
    }

    private static void dropNearHopper(Block hopperBlock, ItemStack stack) {
        if (hopperBlock == null || stack == null || stack.getType().isAir()) {
            return;
        }
        hopperBlock.getWorld().dropItemNaturally(hopperBlock.getLocation().add(0.5, 0.5, 0.5), stack);
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
