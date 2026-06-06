package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xlingran.core.HopperLane;
import xlingran.core.HopperLaneListener;
import xlingran.core.HopperLaneRegistry;
import xlingran.gui.HopperLevelDef;
import xlingran.gui.MessageConfig;
import xlingran.gui.UpdateConfig;

import java.util.Map;

public class HopperListener implements Listener {

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final HopperLaneListener laneListener;
    private final HopperTickService tickService;
    private final MessageConfig messageConfig;
    private final UpdateConfig updateConfig;
    private final XLRHopperConfig pluginConfig;

    public HopperListener(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys,
                          HopperLaneListener laneListener, HopperTickService tickService,
                          MessageConfig messageConfig, UpdateConfig updateConfig,
                          XLRHopperConfig pluginConfig) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.keys = keys;
        this.laneListener = laneListener;
        this.tickService = tickService;
        this.messageConfig = messageConfig;
        this.updateConfig = updateConfig;
        this.pluginConfig = pluginConfig;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }
        Block placed = event.getBlockPlaced();
        if (!pluginConfig.isPluginWorld(placed)) {
            return;
        }
        String levelId = HopperLevelItems.readLevelFromItem(event.getItemInHand(), keys);
        if (levelId != null) {
            HopperLevelItems.applyLevelToBlock(placed, keys, levelId);
        }
        Player player = event.getPlayer();
        String enabledName = templateManager.getEnabledTemplateName(player.getUniqueId());
        if (enabledName == null) {
            return;
        }
        if (templateManager.getTemplate(player.getUniqueId(), enabledName) == null) {
            return;
        }
        Location placeLoc = placed.getLocation().clone();
        if (applyHopperTemplate(placeLoc, player, enabledName)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyHopperTemplate(placeLoc, player, enabledName));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Item item = event.getItem();
        if (item == null) {
            return;
        }
        ItemStack picked = item.getItemStack();
        String levelId = HopperLevelItems.readLevelFromItem(picked, keys);
        if (levelId == null) {
            return;
        }
        ItemStack canonical = HopperLevelItems.createLevelHopper(updateConfig, keys, levelId, picked.getAmount());
        if (canonical == null || canonical.isSimilar(picked)) {
            return;
        }
        item.setItemStack(canonical);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.HOPPER || !pluginConfig.isPluginWorld(block)) {
            return;
        }
        ItemStack drop = HopperLevelItems.createDropFromBlock(block, keys, updateConfig);
        if (drop == null) {
            return;
        }
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!pluginConfig.isExplosionHopperProtection()) {
            return;
        }
        for (Block block : event.blockList()) {
            dropLevelHopperIfNeeded(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!pluginConfig.isExplosionHopperProtection()) {
            return;
        }
        for (Block block : event.blockList()) {
            dropLevelHopperIfNeeded(block);
        }
    }

    private void dropLevelHopperIfNeeded(Block block) {
        if (block.getType() != Material.HOPPER || !pluginConfig.isPluginWorld(block)) {
            return;
        }
        ItemStack drop = HopperLevelItems.createDropFromBlock(block, keys, updateConfig);
        if (drop != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }
    }

    private boolean applyHopperTemplate(Location location, Player player, String enabledName) {
        Block block = location.getBlock();
        if (!HopperPdc.applyTemplate(block, keys, player.getUniqueId(), enabledName)) {
            return false;
        }
        player.sendMessage(messageConfig.message("hopper-place-template",
                Map.of("Template", enabledName)));
        laneListener.scheduleEvaluate(block);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        Inventory src = event.getSource();
        ItemStack moving = event.getItem();
        if (dest.getType() == InventoryType.HOPPER) {
            Block hopperBlock = HopperBlockUtil.resolveHopperBlock(dest);
            if (!isActiveHopper(hopperBlock) || !shouldAllowTransfer(hopperBlock, moving)) {
                event.setCancelled(true);
            }
        }
        if (src.getType() == InventoryType.HOPPER) {
            Block hopperBlock = HopperBlockUtil.resolveHopperBlock(src);
            if (!isActiveHopper(hopperBlock) || !shouldAllowTransfer(hopperBlock, moving)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveHoldOutbound(InventoryMoveItemEvent event) {
        if (event.getSource().getType() != InventoryType.HOPPER) {
            return;
        }
        Block hopperBlock = HopperBlockUtil.resolveHopperBlock(event.getSource());
        if (!isActiveHopper(hopperBlock)) {
            return;
        }
        ItemStack moving = event.getItem();
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        if (shouldHoldHopperOutbound(hopperBlock, moving)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveGate(InventoryMoveItemEvent event) {
        ItemStack moving = event.getItem();
        if (moving == null || moving.getType().isAir() || updateConfig == null) {
            return;
        }
        long tick = GameTickCounter.getInstance().currentTick();
        HopperTransferGate gate = HopperTransferGate.getInstance();

        if (event.getDestination().getType() == InventoryType.HOPPER) {
            Block hopperBlock = HopperBlockUtil.resolveHopperBlock(event.getDestination());
            if (hopperBlock != null && isActiveHopper(hopperBlock)
                    && !allowTieredTransfer(hopperBlock, moving, tick, gate)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getSource().getType() == InventoryType.HOPPER) {
            Block hopperBlock = HopperBlockUtil.resolveHopperBlock(event.getSource());
            if (hopperBlock != null && isActiveHopper(hopperBlock)
                    && !allowTieredTransfer(hopperBlock, moving, tick, gate)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.HOPPER) {
            return;
        }
        Block hopperBlock = HopperBlockUtil.resolveHopperBlock(inventory);
        if (!isActiveHopper(hopperBlock)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!shouldAllowTransfer(hopperBlock, stack)) {
            event.setCancelled(true);
            destroyIfAuto(hopperBlock, stack, event.getItem());
        }
    }

    private boolean isActiveHopper(Block hopperBlock) {
        return hopperBlock != null && pluginConfig.isPluginWorld(hopperBlock);
    }

    private boolean allowTieredTransfer(Block hopperBlock, ItemStack moving, long tick, HopperTransferGate gate) {
        HopperLevelDef def = HopperLevelResolver.resolveForBlock(hopperBlock, keys, updateConfig);
        if (def == null || !gate.tryAcquire(hopperBlock, def, tick)) {
            return false;
        }
        capTransferAmount(moving, def.maxItem());
        return true;
    }

    private static void capTransferAmount(ItemStack moving, int maxItem) {
        if (moving != null && moving.getAmount() > maxItem) {
            moving.setAmount(maxItem);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        if (view.getTopInventory().getType() != InventoryType.HOPPER) {
            return;
        }
        Block hopperBlock = HopperBlockUtil.resolveHopperBlock(view.getTopInventory());
        if (!isActiveHopper(hopperBlock)) {
            return;
        }
        ItemStack incoming = extractItemEnteringHopper(event);
        if (incoming == null || incoming.getType().isAir()) {
            return;
        }
        if (rejectIfFiltered(player, hopperBlock, incoming)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperInventoryClickAfter(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryView view = event.getView();
        if (view.getTopInventory().getType() != InventoryType.HOPPER) {
            return;
        }
        Block hopperBlock = HopperBlockUtil.resolveHopperBlock(view.getTopInventory());
        if (!isActiveHopper(hopperBlock)) {
            return;
        }
        ItemStack incoming = extractItemEnteringHopper(event);
        if (incoming != null && !incoming.getType().isAir()) {
            laneListener.scheduleEvaluateImmediate(hopperBlock);
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
        Block hopperBlock = HopperBlockUtil.resolveHopperBlock(top);
        if (!isActiveHopper(hopperBlock)) {
            return;
        }
        if (rejectIfFiltered(player, hopperBlock, dragged)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDragAfter(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryView view = event.getView();
        if (view.getTopInventory().getType() != InventoryType.HOPPER) {
            return;
        }
        Block hopperBlock = HopperBlockUtil.resolveHopperBlock(view.getTopInventory());
        if (isActiveHopper(hopperBlock)) {
            laneListener.scheduleEvaluateImmediate(hopperBlock);
        }
    }

    private boolean rejectIfFiltered(Player player, Block hopperBlock, ItemStack stack) {
        HopperTemplate template = resolveTemplate(hopperBlock);
        if (template == null) {
            return false;
        }
        if (!template.allows(stack.clone(), hopperBlock, keys)) {
            player.sendMessage(messageConfig.message("filter-deny"));
            return true;
        }
        return false;
    }

    private boolean shouldAllowTransfer(Block hopperBlock, ItemStack stack) {
        HopperTemplate template = resolveTemplate(hopperBlock);
        if (template == null) {
            return true;
        }
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return template.allows(stack.clone(), hopperBlock, keys);
    }

    private void destroyIfAuto(Block hopperBlock, ItemStack stack, Item entity) {
        if (!pluginConfig.isDestroyUnmatchedEnabled()) {
            return;
        }
        HopperTemplate template = resolveTemplate(hopperBlock);
        if (template == null || !template.isAutoDestroy()) {
            return;
        }
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        if (template.allows(stack.clone(), hopperBlock, keys)) {
            return;
        }
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    private HopperTemplate resolveTemplate(Block block) {
        return HopperTemplateResolver.resolve(block, keys, templateManager);
    }

    private boolean shouldHoldHopperOutbound(Block hopperBlock, ItemStack moving) {
        HopperTemplate template = resolveTemplate(hopperBlock);
        if (template == null) {
            return false;
        }
        HopperLaneRegistry registry = tickService.getLaneRegistry();
        HopperLane lane = registry.getLane(hopperBlock.getLocation());
        if (lane == null) {
            lane = registry.registerLane(hopperBlock, keys, templateManager, updateConfig);
        }
        if (lane != null && lane.isAutoCraft() && pluginConfig.isAutoCraftEnabled()
                && tickService.getCraftService().shouldHoldOutbound(hopperBlock, template, keys, moving)) {
            return true;
        }
        return lane != null && lane.isAutoSmelt() && pluginConfig.isAutoSmeltEnabled()
                && tickService.getSmeltService().shouldHoldOutbound(hopperBlock, template, keys, moving);
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
