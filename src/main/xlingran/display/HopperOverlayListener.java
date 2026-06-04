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
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import xlingran.HopperBlockConfig;
import xlingran.HopperKeys;

import java.util.ArrayList;
import java.util.List;

public final class HopperOverlayListener implements Listener {

    private final JavaPlugin plugin;
    private final HopperOverlayDisplayService overlayService;
    private final HopperKeys keys;

    public HopperOverlayListener(JavaPlugin plugin, HopperOverlayDisplayService overlayService, HopperKeys keys) {
        this.plugin = plugin;
        this.overlayService = overlayService;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block dest = hopperBlock(event.getDestination());
        Block src = hopperBlock(event.getSource());
        if (dest != null && overlayService.isHoverEnabled(dest)) {
            overlayService.refresh(dest);
        }
        if (src != null && overlayService.isHoverEnabled(src)) {
            overlayService.refresh(src);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        if (event.getInventory().getType() != InventoryType.HOPPER) {
            return;
        }
        Block block = hopperBlock(event.getInventory());
        if (block != null && overlayService.isHoverEnabled(block)) {
            overlayService.refresh(block);
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
        Chunk chunk = event.getChunk();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == Material.HOPPER && overlayService.isHoverEnabled(block)) {
                        overlayService.hide(block);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Location> enabled = new ArrayList<>();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() == Material.HOPPER
                                && HopperBlockConfig.read(block, keys).isHoverDisplay()) {
                            enabled.add(block.getLocation());
                        }
                    }
                }
            }
            if (enabled.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Location loc : enabled) {
                    Block block = loc.getBlock();
                    if (block.getType() == Material.HOPPER && overlayService.isHoverEnabled(block)) {
                        overlayService.show(block);
                    }
                }
            });
        });
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
