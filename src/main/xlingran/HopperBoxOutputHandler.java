package xlingran;

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

import java.util.UUID;

/**
 * 拦截漏斗向下原版传输，交由 {@link HopperTransferQueue} 串行队列处理双路拆分。
 */
public class HopperBoxOutputHandler implements Listener {

    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final PlayerBoxManager boxManager;
    private final HopperTransferQueue transferQueue;

    public HopperBoxOutputHandler(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys,
                                  PlayerBoxManager boxManager, Runnable persistBoxes) {
        this.templateManager = templateManager;
        this.keys = keys;
        this.boxManager = boxManager;
        this.transferQueue = new HopperTransferQueue(plugin, templateManager, keys, boxManager, persistBoxes);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
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
        if (boxManager.maxFit(owner, boxName, moving) <= 0) {
            return;
        }

        event.setCancelled(true);
        transferQueue.enqueue(
                hopperBlock.getLocation(),
                belowBlock.getLocation(),
                owner,
                boxName,
                moving,
                moving.getAmount());
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
