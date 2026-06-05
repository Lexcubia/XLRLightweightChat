package xlingran.display;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import eu.decentsoftware.holograms.event.DecentHologramsReloadEvent;
import xlingran.HopperChunkScanUtil;
import xlingran.XLRHopperConfig;

import java.util.ArrayList;
import java.util.List;

public final class HopperOverlayListener implements Listener {

    private final JavaPlugin plugin;
    private final HopperOverlayDisplayService overlayService;
    private final XLRHopperConfig pluginConfig;

    public HopperOverlayListener(JavaPlugin plugin, HopperOverlayDisplayService overlayService,
                                 XLRHopperConfig pluginConfig) {
        this.plugin = plugin;
        this.overlayService = overlayService;
        this.pluginConfig = pluginConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block dest = hopperBlock(event.getDestination());
        Block src = hopperBlock(event.getSource());
        if (dest != null && overlayService.isHoverEnabled(dest)) {
            overlayService.refreshDebounced(dest);
        }
        if (src != null && overlayService.isHoverEnabled(src)) {
            overlayService.refreshDebounced(src);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        if (event.getInventory().getType() != InventoryType.HOPPER) {
            return;
        }
        Block block = hopperBlock(event.getInventory());
        if (block != null && overlayService.isHoverEnabled(block)) {
            overlayService.refreshDebounced(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Block block = hopperBlockFromView(event.getView());
        if (block == null || !touchesHopperTop(event)) {
            return;
        }
        refreshIfEnabled(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Block block = hopperBlockFromView(event.getView());
        if (block == null || !dragTouchesHopperTop(event)) {
            return;
        }
        refreshIfEnabled(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Block block = hopperBlockFromView(event.getView());
        if (block != null) {
            refreshIfEnabled(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            overlayService.hide(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.HOPPER) {
                overlayService.hide(block);
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        overlayService.hideAllInChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (!pluginConfig.isPluginWorld(chunk.getWorld())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> restoreChunkOverlays(chunk));
    }

    @EventHandler
    public void onDecentHologramsReload(DecentHologramsReloadEvent event) {
        Bukkit.getScheduler().runTask(plugin, overlayService::restoreAllAfterReload);
    }

    private void restoreChunkOverlays(Chunk chunk) {
        List<Location> hoppers = new ArrayList<>();
        for (Block block : HopperChunkScanUtil.hoppersInChunk(chunk)) {
            if (overlayService.isHoverEnabled(block)) {
                hoppers.add(block.getLocation());
            }
        }
        restoreOverlays(hoppers);
    }

    private void refreshIfEnabled(Block block) {
        if (overlayService.isHoverEnabled(block)) {
            overlayService.refresh(block);
        }
    }

    private void restoreOverlays(List<Location> hoppers) {
        for (Location loc : hoppers) {
            Block block = loc.getBlock();
            if (block.getType() == Material.HOPPER && overlayService.isHoverEnabled(block)) {
                overlayService.show(block);
            }
        }
    }

    private static Block hopperBlockFromView(InventoryView view) {
        if (view == null || view.getTopInventory().getType() != InventoryType.HOPPER) {
            return null;
        }
        return hopperBlock(view.getTopInventory());
    }

    private static boolean touchesHopperTop(InventoryClickEvent event) {
        InventoryView view = event.getView();
        int topSize = view.getTopInventory().getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < topSize) {
            return true;
        }
        if (event.isShiftClick()) {
            Inventory clicked = event.getClickedInventory();
            if (clicked != null && clicked.getType() != InventoryType.HOPPER) {
                ItemStack current = event.getCurrentItem();
                return current != null && !current.getType().isAir();
            }
        }
        return false;
    }

    private static boolean dragTouchesHopperTop(InventoryDragEvent event) {
        InventoryView view = event.getView();
        int topSize = view.getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                return true;
            }
        }
        return false;
    }

    private static Block hopperBlock(Inventory inventory) {
        if (inventory == null || inventory.getType() != InventoryType.HOPPER) {
            return null;
        }
        if (!(inventory.getHolder() instanceof org.bukkit.block.Hopper hopper)) {
            return null;
        }
        return hopper.getBlock();
    }
}
