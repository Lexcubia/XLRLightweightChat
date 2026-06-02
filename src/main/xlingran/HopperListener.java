package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class HopperListener implements Listener {

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;

    public HopperListener(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }
        Player player = event.getPlayer();
        String enabledName = templateManager.getEnabledTemplateName(player.getUniqueId());
        if (enabledName == null) {
            return;
        }
        if (templateManager.getTemplate(player.getUniqueId(), enabledName) == null) {
            return;
        }
        Location placeLoc = event.getBlockPlaced().getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> applyHopperTemplate(placeLoc, player, enabledName));
    }

    private void applyHopperTemplate(Location location, Player player, String enabledName) {
        Block block = location.getBlock();
        if (block.getType() != Material.HOPPER) {
            return;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return;
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(keys.template, PersistentDataType.STRING, enabledName);
        pdc.set(keys.owner, PersistentDataType.STRING, player.getUniqueId().toString());
        tileState.update(true);

        player.sendMessage(ChatColor.GREEN + "当前使用漏斗模板: " + ChatColor.AQUA + enabledName);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        if (dest.getType() != InventoryType.HOPPER) {
            return;
        }
        if (!shouldAllowTransfer(dest, event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.HOPPER) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!shouldAllowTransfer(inventory, stack)) {
            event.setCancelled(true);
        }
    }

  /**
     * 玩家 Q 丢物品到漏斗附近：在拾取事件之前拦截（取消则物品不会掉落）。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        Block hopper = findHopperNear(event.getItemDrop().getLocation());
        if (hopper == null) {
            return;
        }
        HopperTemplate template = resolveTemplate(hopper);
        if (template == null) {
            return;
        }
        if (!template.allows(stack.clone())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "该物品不符合当前漏斗模板过滤规则");
        }
    }

    private boolean shouldAllowTransfer(Inventory hopperInventory, ItemStack stack) {
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
        return resolveTemplate(getHopperBlock(hopperInventory));
    }

    private HopperTemplate resolveTemplate(Block block) {
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

    private Block findHopperNear(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Block center = location.getBlock();
        if (center.getType() == Material.HOPPER) {
            return center;
        }
        Block below = center.getRelative(BlockFace.DOWN);
        if (below.getType() == Material.HOPPER) {
            return below;
        }
        Block above = center.getRelative(BlockFace.UP);
        if (above.getType() == Material.HOPPER) {
            return above;
        }
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = center.getRelative(face);
            if (relative.getType() == Material.HOPPER) {
                return relative;
            }
        }
        return null;
    }
}
