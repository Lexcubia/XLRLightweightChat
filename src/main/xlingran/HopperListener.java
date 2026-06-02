package xlingran;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
        state.update(true);

        player.sendMessage(ChatColor.GREEN + "当前使用漏斗模板: " + ChatColor.AQUA + enabledName);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        if (dest.getType() != InventoryType.HOPPER) {
            return;
        }
        if (!shouldBlockTransfer(dest, event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.HOPPER) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!shouldBlockTransfer(inventory, stack)) {
            event.setCancelled(true);
        }
    }

    private boolean shouldBlockTransfer(Inventory hopperInventory, ItemStack stack) {
        HopperTemplate template = resolveTemplate(hopperInventory);
        if (template == null) {
            return true;
        }
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return template.allows(stack.clone());
    }

    private HopperTemplate resolveTemplate(Inventory hopperInventory) {
        Block block = getHopperBlock(hopperInventory);
        if (block == null || block.getType() != Material.HOPPER) {
            return null;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return null;
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
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

    private Block getHopperBlock(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState blockState) {
            return blockState.getBlock();
        }
        Location location = inventory.getLocation();
        if (location != null) {
            return location.getBlock();
        }
        return null;
    }
}
