package xlingran;

import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * 漏斗 PDC 仅存模板名与所有者；过滤规则始终从 {@link HopperTemplateManager} 按名读取，
 * 修改模板后所有绑定该名的漏斗立即生效。
 */
public class HopperListener implements Listener {

    private static final String DENY_MESSAGE = ChatColor.RED + "该物品不符合当前漏斗模板过滤规则";
    private static final String PLACE_MESSAGE_PREFIX = ChatColor.GREEN + "当前使用漏斗模板: ";

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
        Location placeLoc = event.getBlockPlaced().getLocation().clone();
        if (applyHopperTemplate(placeLoc, player, enabledName)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyHopperTemplate(placeLoc, player, enabledName));
    }

    private boolean applyHopperTemplate(Location location, Player player, String enabledName) {
        Block block = location.getBlock();
        if (block.getType() != Material.HOPPER) {
            return false;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return false;
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(keys.template, PersistentDataType.STRING, enabledName);
        pdc.set(keys.owner, PersistentDataType.STRING, player.getUniqueId().toString());
        tileState.update(true);

        player.sendMessage(PLACE_MESSAGE_PREFIX + ChatColor.AQUA + enabledName);
        return true;
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

    /**
     * 地上物品被漏斗吸入时过滤（Q 丢、背包丢出、死亡掉落等均可正常落地，仅取消漏斗拾取）。
     */
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

    /** 玩家打开漏斗 GUI 直接放入（非地上丢弃） */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        if (view.getTopInventory().getType() != InventoryType.HOPPER) {
            return;
        }
        Block hopperBlock = getHopperBlock(view.getTopInventory());
        ItemStack incoming = extractItemEnteringHopper(event);
        if (incoming == null || incoming.getType().isAir()) {
            return;
        }
        if (rejectIfFiltered(player, hopperBlock, incoming)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        if (view.getTopInventory().getType() != InventoryType.HOPPER) {
            return;
        }
        Inventory top = view.getTopInventory();
        int topSize = top.getSize();
        ItemStack dragged = event.getOldCursor();
        if (dragged == null || dragged.getType().isAir()) {
            return;
        }
        boolean touchesHopper = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                touchesHopper = true;
                break;
            }
        }
        if (!touchesHopper) {
            return;
        }
        Block hopperBlock = getHopperBlock(top);
        if (rejectIfFiltered(player, hopperBlock, dragged)) {
            event.setCancelled(true);
        }
    }

    private boolean rejectIfFiltered(Player player, Block hopperBlock, ItemStack stack) {
        HopperTemplate template = resolveTemplate(hopperBlock);
        if (template == null) {
            return false;
        }
        if (!template.allows(stack.clone())) {
            player.sendMessage(DENY_MESSAGE);
            return true;
        }
        return false;
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

    private ItemStack extractItemEnteringHopper(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return null;
        }

        InventoryAction action = event.getAction();
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (clicked.equals(top)) {
            if (cursor != null && !cursor.getType().isAir()) {
                switch (action) {
                    case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                        return cursor;
                    }
                    default -> {
                    }
                }
            }
            if (action == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() >= 0
                    && event.getWhoClicked() instanceof Player player) {
                return player.getInventory().getItem(event.getHotbarButton());
            }
            return null;
        }

        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && current != null && !current.getType().isAir()) {
            return current;
        }
        return null;
    }
}
