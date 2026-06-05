package xlingran.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xlingran.HopperAutoCraftService;
import xlingran.HopperAutoSmeltService;
import xlingran.HopperChunkScanUtil;
import xlingran.HopperKeys;
import xlingran.HopperTemplateManager;
import xlingran.HopperTemplateResolver;
import xlingran.HopperTickService;
import xlingran.XLRHopperConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HopperLaneListener implements Listener {

    private static final long EVALUATE_DEBOUNCE_TICKS = 4L;

    private final JavaPlugin plugin;
    private final HopperTickService tickService;
    private final XLRHopperConfig pluginConfig;
    private final Map<String, BukkitTask> debouncedEvaluate = new ConcurrentHashMap<>();

    public HopperLaneListener(JavaPlugin plugin, HopperTickService tickService, XLRHopperConfig pluginConfig) {
        this.plugin = plugin;
        this.tickService = tickService;
        this.pluginConfig = pluginConfig;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (!pluginConfig.isPluginWorld(chunk.getWorld())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> registerChunkHoppers(chunk));
    }

    private void registerChunkHoppers(Chunk chunk) {
        List<Block> hoppers = HopperChunkScanUtil.hoppersInChunk(chunk);
        if (hoppers.isEmpty()) {
            return;
        }
        HopperKeys keys = tickService.getKeys();
        HopperTemplateManager tm = tickService.getTemplateManager();
        HopperLaneRegistry registry = tickService.getLaneRegistry();
        HopperAutoSmeltService smelt = tickService.getSmeltService();
        HopperAutoCraftService craft = tickService.getCraftService();
        for (Block block : hoppers) {
            HopperLane lane = registry.registerLane(block, keys, tm, tickService.getUpdateConfig());
            if (lane != null) {
                HopperWorkEvaluator.evaluateAndQueue(block, registry, keys, smelt, craft);
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!pluginConfig.isClearOnChunkUnload()) {
            return;
        }
        for (Block block : HopperChunkScanUtil.hoppersInChunk(event.getChunk())) {
            tickService.onLaneRemoved(block.getLocation());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == org.bukkit.Material.HOPPER) {
            scheduleEvaluate(event.getBlockPlaced());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == org.bukkit.Material.HOPPER) {
            cancelEvaluate(event.getBlock());
            tickService.onLaneRemoved(event.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == org.bukkit.Material.HOPPER) {
                cancelEvaluate(block);
                tickService.onLaneRemoved(block.getLocation());
            }
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block dest = xlingran.HopperBlockUtil.resolveHopperBlock(event.getDestination());
        Block src = xlingran.HopperBlockUtil.resolveHopperBlock(event.getSource());
        if (dest != null) {
            scheduleEvaluateImmediate(dest);
        }
        if (src != null) {
            scheduleEvaluate(src);
        }
    }

    @EventHandler
    public void onPickup(InventoryPickupItemEvent event) {
        if (event.getInventory().getType() != InventoryType.HOPPER) {
            return;
        }
        Block block = xlingran.HopperBlockUtil.resolveHopperBlock(event.getInventory());
        if (block != null) {
            scheduleEvaluateImmediate(block);
        }
    }

    public void scheduleEvaluate(Block hopperBlock) {
        scheduleEvaluate(hopperBlock, false);
    }

    public void scheduleEvaluateImmediate(Block hopperBlock) {
        scheduleEvaluate(hopperBlock, true);
    }

    private void scheduleEvaluate(Block hopperBlock, boolean immediate) {
        if (hopperBlock == null || hopperBlock.getType() != org.bukkit.Material.HOPPER) {
            return;
        }
        if (!pluginConfig.isPluginWorld(hopperBlock)) {
            return;
        }
        if (HopperTemplateResolver.resolve(hopperBlock, tickService.getKeys(), tickService.getTemplateManager()) == null) {
            return;
        }
        String key = HopperLane.laneKey(hopperBlock.getLocation());
        if (key.isEmpty()) {
            return;
        }
        if (immediate || hasAutomationLane(hopperBlock)) {
            BukkitTask pending = debouncedEvaluate.remove(key);
            if (pending != null) {
                pending.cancel();
            }
            Bukkit.getScheduler().runTask(plugin, () -> runEvaluateAndAutomate(hopperBlock));
            return;
        }
        BukkitTask pending = debouncedEvaluate.remove(key);
        if (pending != null) {
            pending.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            debouncedEvaluate.remove(key);
            runEvaluate(hopperBlock);
        }, EVALUATE_DEBOUNCE_TICKS);
        debouncedEvaluate.put(key, task);
    }

    private boolean hasAutomationLane(Block hopperBlock) {
        HopperLane lane = tickService.getLaneRegistry().getLane(hopperBlock.getLocation());
        if (lane == null) {
            lane = tickService.getLaneRegistry().registerLane(hopperBlock, tickService.getKeys(),
                    tickService.getTemplateManager(), tickService.getUpdateConfig());
        }
        return lane != null && (lane.isAutoCraft() || lane.isAutoSmelt());
    }

    private void runEvaluateAndAutomate(Block hopperBlock) {
        runEvaluate(hopperBlock);
        tickService.runAutomationImmediate(hopperBlock);
    }

    private void runEvaluate(Block hopperBlock) {
        if (hopperBlock.getType() != org.bukkit.Material.HOPPER || !pluginConfig.isPluginWorld(hopperBlock)) {
            return;
        }
        HopperLane lane = tickService.getLaneRegistry().registerLane(hopperBlock, tickService.getKeys(),
                tickService.getTemplateManager(), tickService.getUpdateConfig());
        if (lane != null) {
            HopperWorkEvaluator.markPending(hopperBlock, tickService.getLaneRegistry(), tickService.getKeys(),
                    tickService.getSmeltService(), tickService.getCraftService());
        }
    }

    private void cancelEvaluate(Block block) {
        if (block == null) {
            return;
        }
        BukkitTask pending = debouncedEvaluate.remove(HopperLane.laneKey(block.getLocation()));
        if (pending != null) {
            pending.cancel();
        }
    }
}
