package xlingran.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import xlingran.HopperAutoSmeltService;
import xlingran.HopperKeys;
import xlingran.HopperReservation;
import xlingran.HopperTemplateManager;
import xlingran.HopperTickService;

import java.util.ArrayList;
import java.util.List;

public final class HopperLaneListener implements Listener {

    private final JavaPlugin plugin;
    private final HopperTickService tickService;

    public HopperLaneListener(JavaPlugin plugin, HopperTickService tickService) {
        this.plugin = plugin;
        this.tickService = tickService;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Location> hoppers = new ArrayList<>();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                        if (chunk.getBlock(x, y, z).getType() == Material.HOPPER) {
                            hoppers.add(chunk.getBlock(x, y, z).getLocation());
                        }
                    }
                }
            }
            if (hoppers.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> registerLocations(hoppers));
        });
    }

    private void registerLocations(List<Location> locations) {
        HopperKeys keys = tickService.getKeys();
        HopperTemplateManager tm = tickService.getTemplateManager();
        HopperLaneRegistry registry = tickService.getLaneRegistry();
        HopperAutoSmeltService smelt = tickService.getSmeltService();
        for (Location loc : locations) {
            Block block = loc.getBlock();
            if (block.getType() != Material.HOPPER) {
                continue;
            }
            HopperLane lane = registry.registerLane(block, keys, tm);
            if (lane != null) {
                HopperWorkEvaluator.evaluateAndQueue(block, registry, keys, smelt);
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    if (chunk.getBlock(x, y, z).getType() == Material.HOPPER) {
                        Location loc = chunk.getBlock(x, y, z).getLocation();
                        tickService.onLaneRemoved(loc);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }
        scheduleEvaluate(event.getBlockPlaced());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            tickService.onLaneRemoved(event.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.HOPPER) {
                tickService.onLaneRemoved(block.getLocation());
            }
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block dest = hopperBlock(event.getDestination());
        Block src = hopperBlock(event.getSource());
        if (dest != null) {
            scheduleEvaluate(dest);
        }
        if (src != null) {
            scheduleEvaluate(src);
        }
    }

    @EventHandler
    public void onPickup(InventoryPickupItemEvent event) {
        if (event.getInventory().getType() == InventoryType.HOPPER) {
            Block block = hopperBlock(event.getInventory());
            if (block != null) {
                scheduleEvaluate(block);
            }
        }
    }

    public void scheduleEvaluate(Block hopperBlock) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            HopperLane lane = tickService.getLaneRegistry().registerLane(hopperBlock, tickService.getKeys(),
                    tickService.getTemplateManager());
            if (lane != null) {
                HopperWorkEvaluator.markPending(hopperBlock, tickService.getLaneRegistry(), tickService.getKeys(),
                        tickService.getSmeltService());
            }
        });
    }

    private static Block hopperBlock(Inventory inventory) {
        if (inventory == null || inventory.getType() != InventoryType.HOPPER) {
            return null;
        }
        if (inventory.getHolder() instanceof org.bukkit.block.BlockState state) {
            return state.getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }
}
