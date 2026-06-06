package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
public final class HopperTickService {

    public static final long HOPPER_TICK_PERIOD = 8L;

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final HopperLaneRegistry laneRegistry;
    private final HopperReservation reservation;
    private final HopperAutoSmeltService smeltService;
    private final HopperAutoCraftService craftService;
    private final UpdateConfig updateConfig;
    private final XLRHopperConfig pluginConfig;

    public HopperTickService(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys,
                             HopperLaneRegistry laneRegistry, UpdateConfig updateConfig,
                             XLRHopperConfig pluginConfig) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.keys = keys;
        this.laneRegistry = laneRegistry;
        this.reservation = new HopperReservation();
        this.smeltService = new HopperAutoSmeltService(pluginConfig);
        this.craftService = new HopperAutoCraftService(pluginConfig);
        this.updateConfig = updateConfig;
        this.pluginConfig = pluginConfig;
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
        craftService.clear(loc);
        reservation.clear(loc);
        HopperTransferGate.getInstance().clear(loc);
    }

    /**
     * 异步 reindex 完成后在主线程调用。
     */
    public void registerLoadedHopper(Block block) {
        if (!pluginConfig.isPluginWorld(block)) {
            return;
        }
        HopperLane lane = laneRegistry.registerLane(block, keys, templateManager, updateConfig);
        if (lane != null) {
            HopperWorkEvaluator.evaluateAndQueue(block, laneRegistry, keys, smeltService, craftService);
        }
    }

    /**
     * 入料后主线程即时尝试合成/熔炼，并刷新预留槽位（不等待 transferTick 冷却）。
     */
    public void runAutomationImmediate(Block block) {
        if (block == null || block.getType() != Material.HOPPER || !pluginConfig.isPluginWorld(block)) {
            return;
        }
        HopperLane lane = laneRegistry.getLane(block.getLocation());
        if (lane == null || !lane.hasSnapshot()) {
            lane = laneRegistry.registerLane(block, keys, templateManager, updateConfig);
        }
        if (lane == null || !lane.hasSnapshot()) {
            return;
        }
        HopperTemplate template = lane.template();
        if (template == null) {
            return;
        }
        Location loc = block.getLocation();
        Set<Integer> reserved = new HashSet<>(smeltService.getActiveReservedSlots(loc));
        reserved.addAll(craftService.getActiveReservedSlots(loc));
        if (lane.isAutoSmelt() && pluginConfig.isAutoSmeltEnabled()) {
            reserved.addAll(smeltService.tryStartSmelt(block, template, keys));
        }
        if (lane.isAutoCraft() && pluginConfig.isAutoCraftEnabled()) {
            reserved.addAll(craftService.tryStartCraft(block, template, keys));
        }
        if (reserved.isEmpty()) {
            reservation.clear(loc);
        } else {
            reservation.setReserved(loc, reserved);
        }
    }

    public HopperAutoCraftService getCraftService() {
        return craftService;
    }

    /**
     * 反向+自动合成/熔炼：产物仅经 deliverDownstream 下传，禁止 push 到上方箱。
     */
    private static boolean shouldSkipReversePushForAutomation(HopperLane lane) {
        return lane.isAutoCraft() || lane.isAutoSmelt();
    }

    private void tickAll() {
        int maxPerTick = pluginConfig.getPerTickMaxProcess();
        List<HopperLane> lanes = laneRegistry.workQueueSnapshot(maxPerTick);
        int processed = 0;
        for (HopperLane lane : lanes) {
            if (++processed > maxPerTick) {
                plugin.getLogger().warning("[XLRHopper] 漏斗工作队列积压，剩余延后处理");
                break;
            }
            Location loc = lane.location();
            if (loc.getWorld() == null || !pluginConfig.isPluginWorld(loc.getWorld())
                    || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
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

            boolean redstonePowered = HopperRedstoneTransferService.isRedstonePoweredTransferActive(
                    block, keys, pluginConfig);
            int maxItem = lane.maxItem();
            HopperTransferForward.ForwardTransferContext forwardCtx =
                    new HopperTransferForward.ForwardTransferContext(craftService, smeltService, pluginConfig);
            HopperTransferReverse.ReverseTransferContext reverseCtx =
                    new HopperTransferReverse.ReverseTransferContext(craftService, smeltService, pluginConfig);

            if (redstonePowered) {
                int redstoneMax = HopperRedstoneTransferService.resolveMaxItem(block, keys, updateConfig);
                HopperRedstoneTransferService.absorbStep(block, template, keys, pluginConfig, redstoneMax);
                runAutomationImmediate(block);
                Set<Integer> redstoneReserved = new HashSet<>();
                redstoneReserved.addAll(smeltService.getActiveReservedSlots(loc));
                redstoneReserved.addAll(craftService.getActiveReservedSlots(loc));
                HopperRedstoneTransferService.RedstoneTransferContext redstoneCtx =
                        new HopperRedstoneTransferService.RedstoneTransferContext(
                                craftService, smeltService, pluginConfig, redstoneReserved);
                HopperRedstoneTransferService.pushStep(block, template, keys, pluginConfig, redstoneMax, redstoneCtx);
            }

            boolean transferGateOpen = lane.ticksSinceLastStep() >= lane.transferTick();
            if (!transferGateOpen) {
                boolean keepWaiting = HopperWorkEvaluator.shouldRemainInQueue(block, lane, keys, smeltService,
                        craftService) || redstonePowered;
                laneRegistry.removeLaneFromQueueAfterTick(lane, keepWaiting);
                continue;
            }
            lane.resetTicksSinceLastStep();

            if (lane.isReverse() && pluginConfig.isReverseHopperEnabled()) {
                HopperTransferReverse.pullStep(block, template, keys, reservation, maxItem);
                runAutomationImmediate(block);
            } else if (!redstonePowered) {
                HopperTransferForward.pullStep(block, template, keys, maxItem);
                runAutomationImmediate(block);
            }

            Set<Integer> reserved = new HashSet<>();
            if (lane.isAutoSmelt() && pluginConfig.isAutoSmeltEnabled()) {
                reserved.addAll(smeltService.tick(block, template, keys));
            }
            if (lane.isAutoCraft() && pluginConfig.isAutoCraftEnabled()) {
                reserved.addAll(craftService.tick(block, template, keys));
            }
            reservation.setReserved(loc, reserved);

            if (lane.isReverse() && pluginConfig.isReverseHopperEnabled()
                    && !shouldSkipReversePushForAutomation(lane)) {
                HopperTransferReverse.ReverseTransferResult reverseResult = HopperTransferReverse.pushStep(
                        block, template, keys, reservation, maxItem, reverseCtx);
                if (reverseResult.pushTargetFull()) {
                    laneRegistry.markTargetFull(loc);
                } else if (reverseResult.moved() > 0) {
                    laneRegistry.invalidateTargetSpace(loc);
                }
            } else if (!redstonePowered) {
                HopperTransferForward.pushStep(block, template, keys, reserved, maxItem, forwardCtx);
            }

            HopperLevelDef levelDef = HopperLevelResolver.resolveForBlock(block, keys, updateConfig);
            if (levelDef != null) {
                HopperTransferGate.getInstance().recordTransfer(block, levelDef,
                        GameTickCounter.getInstance().currentTick());
            }

            boolean keep = HopperWorkEvaluator.shouldRemainInQueue(block, lane, keys, smeltService, craftService)
                    || redstonePowered;
            laneRegistry.removeLaneFromQueueAfterTick(lane, keep);
        }
    }
}
