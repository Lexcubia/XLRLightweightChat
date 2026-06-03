package xlingran;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 双路输出：漏斗向下输出成功后，向模板链接的漏斗仓库再存入一份相同物品。
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
        ItemStack copy = moved.clone();
        boxManager.addItem(owner, boxName, copy);
    }

    private UUID resolveOwner(Block block) {
        if (block == null) {
            return null;
        }
        var state = block.getState();
        if (!(state instanceof org.bukkit.block.TileState tileState)) {
            return null;
        }
        String ownerStr = tileState.getPersistentDataContainer().get(keys.owner, org.bukkit.persistence.PersistentDataType.STRING);
        if (ownerStr == null) {
            return null;
        }
        try {
            return UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Block getHopperBlock(Inventory inventory) {
        if (inventory == null || inventory.getType() != InventoryType.HOPPER) {
            return null;
        }
        if (inventory.getHolder() instanceof org.bukkit.block.BlockState blockState) {
            return blockState.getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }
}
