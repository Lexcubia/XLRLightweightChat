package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import xlingran.core.HopperLane;
import xlingran.core.HopperLaneRegistry;
import xlingran.core.HopperWorkEvaluator;
import xlingran.gui.HopperLevelDef;
import xlingran.gui.UpdateConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * P1：仅 workQueue 排水 + 单步 feature（§0.2）。
 */
public final class HopperTickService implements Listener {

    public static final long HOPPER_TICK_PERIOD = 8L;
    private static final int MAX_PER_TICK = 256;

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final HopperLaneRegistry laneRegistry;
    private final HopperReservation reservation;
    private final HopperAutoSmeltService smeltService;
    private final HopperAutoCraftService craftService;
    private final UpdateConfig updateConfig;

    public HopperTickService(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys,
                             HopperLaneRegistry laneRegistry, UpdateConfig updateConfig) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.keys = keys;
        this.laneRegistry = laneRegistry;
        this.reservation = new HopperReservation();
        this.smeltService = new HopperAutoSmeltService();
        this.craftService = new HopperAutoCraftService();
        this.updateConfig = updateConfig;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, HOPPER_TICK_PERIOD, HOPPER_TICK_PERIOD);
    }

    public HopperLaneRegistry getLaneRegistry() {
        return laneRegistry;
    }

    public HopperTemplateManager getTemplateManager() {
        return templateManager;
    }

    public HopperKeys getKeys() {
        return keys;
    }

    public HopperReservation getReservation() {
        return reservation;
    }

    public HopperAutoSmeltService getSmeltService() {
        return smeltService;
    }

    public UpdateConfig getUpdateConfig() {
        return updateConfig;
    }

    public void onLaneRemoved(Location loc) {
        laneRegistry.unregisterLane(loc);
        smeltService.clear(loc);
        reservation.clear(loc);
        HopperTransferGate.getInstance().clear(loc);
    }

    /**
     * 异步 reindex 完成后在主线程调用。
     */
    public void registerLoadedHopper(Block block) {
        HopperLane lane = laneRegistry.registerLane(block, keys, templateManager, updateConfig);
        if (lane != null) {
            HopperWorkEvaluator.evaluateAndQueue(block, laneRegistry, keys, smeltService);
        }
    }

    private void tickAll() {
        List<HopperLane> lanes = laneRegistry.workQueueSnapshot(MAX_PER_TICK);
        int processed = 0;
        for (HopperLane lane : lanes) {
            if (++processed > MAX_PER_TICK) {
                plugin.getLogger().warning("[XLRHopper] 漏斗工作队列积压，剩余延后处理");
                break;
            }
            Location loc = lane.location();
            if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                laneRegistry.removeFromWorkQueue(HopperLane.laneKey(loc));
                continue;
            }
            Block block = loc.getBlock();
            if (block.getType() != Material.HOPPER || !lane.hasSnapshot()) {
                onLaneRemoved(loc);
                continue;
            }
            HopperTemplate template = lane.template();
            if (template == null) {
                onLaneRemoved(loc);
                continue;
            }

            HopperLevelResolver.applyLevelToLane(lane, block, keys, updateConfig);
            lane.incrementTicksSinceLastStep();
            if (lane.ticksSinceLastStep() < lane.transferTick()) {
                boolean keepWaiting = HopperWorkEvaluator.shouldRemainInQueue(block, lane, keys, smeltService);
                laneRegistry.removeLaneFromQueueAfterTick(lane, keepWaiting);
                continue;
            }
            lane.resetTicksSinceLastStep();

            Set<Integer> reserved = new HashSet<>();
            if (lane.isAutoSmelt()) {
                reserved.addAll(smeltService.tick(block, template, keys));
            }
            if (lane.isAutoCraft()) {
                reserved.addAll(craftService.tryCraft(block, template, keys));
            }
            reservation.setReserved(loc, reserved);

            if (lane.isReverse()) {
                int moved = HopperTransferReverse.transferStep(block, template, keys, reservation, lane.maxItem());
                if (moved > 0) {
                    HopperLevelDef levelDef = HopperLevelResolver.resolveForBlock(block, keys, updateConfig);
                    if (levelDef != null) {
                        HopperTransferGate.getInstance().recordMoves(block, levelDef, GameTickCounter.getInstance().currentTick(), moved);
                    }
                }
            }

            boolean keep = HopperWorkEvaluator.shouldRemainInQueue(block, lane, keys, smeltService);
            laneRegistry.removeLaneFromQueueAfterTick(lane, keep);
        }
    }
}
