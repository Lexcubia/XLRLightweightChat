package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class HopperListener implements Listener {

    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;

    public HopperListener(HopperTemplateManager templateManager, HopperKeys keys) {
        this.templateManager = templateManager;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }
        Player player = event.getPlayer();
        String enabledName = templateManager.getEnabledTemplateName(player.getUniqueId());
        if (enabledName == null) {
            return;
        }
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), enabledName);
        if (template == null) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof TileState state)) {
            return;
        }
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(keys.template, PersistentDataType.STRING, enabledName);
        pdc.set(keys.owner, PersistentDataType.STRING, player.getUniqueId().toString());
        state.update();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        if (dest.getType() != InventoryType.HOPPER) {
            return;
        }
        HopperTemplate template = resolveTemplate(dest);
        if (template == null) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!template.allows(stack)) {
            event.setCancelled(true);
        }
    }

    private HopperTemplate resolveTemplate(Inventory hopperInventory) {
        if (hopperInventory.getHolder() instanceof org.bukkit.block.Hopper hopper) {
            Block block = hopper.getBlock();
            if (block.getState() instanceof TileState state) {
                PersistentDataContainer pdc = state.getPersistentDataContainer();
                String templateName = pdc.get(keys.template, PersistentDataType.STRING);
                String ownerStr = pdc.get(keys.owner, PersistentDataType.STRING);
                if (templateName == null || ownerStr == null) {
                    return null;
                }
                try {
                    UUID owner = UUID.fromString(ownerStr);
                    return templateManager.getTemplate(owner, templateName);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
