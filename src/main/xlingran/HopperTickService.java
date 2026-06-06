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
        Set<Integer> reserved = runAutomation(block, template, lane, loc);
        if (reserved.isEmpty()) {
            reservation.clear(loc);
        } else {
            reservation.setReserved(loc, reserved);
        }
    }

    private Set<Integer> collectAutomationReserved(Location loc) {
        Set<Integer> reserved = new HashSet<>(smeltService.getActiveReservedSlots(loc));
        reserved.addAll(craftService.getActiveReservedSlots(loc));
        reserved.addAll(reservation.getReserved(loc));
        return reserved;
    }

    /** 推进熔炼/合成计时；无 job 时尝试启动。 */
    private Set<Integer> runAutomation(Block block, HopperTemplate template, HopperLane lane, Location loc) {
        Set<Integer> reserved = new HashSet<>();
        if (lane.isAutoSmelt() && pluginConfig.isAutoSmeltEnabled()) {
            reserved.addAll(smeltService.tick(block, template, keys));
            reserved.addAll(smeltService.tryStartSmelt(block, template, keys));
        }
        if (lane.isAutoCraft() && pluginConfig.isAutoCraftEnabled()) {
            reserved.addAll(craftService.tick(block, template, keys));
            reserved.addAll(craftService.tryStartCraft(block, template, keys));
        }
        return reserved;
    }

    public HopperAutoCraftService getCraftService() {
        return craftService;
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
            boolean isReverse = lane.isReverse() && pluginConfig.isReverseHopperEnabled();

            if (redstonePowered) {
                int redstoneMax = HopperRedstoneTransferService.resolveMaxItem(block, keys, updateConfig);
                HopperRedstoneTransferService.absorbStep(block, template, keys, pluginConfig, redstoneMax);
                Set<Integer> automationReserved = runAutomation(block, template, lane, loc);
                reservation.setReserved(loc, automationReserved);
                Set<Integer> redstoneReserved = new HashSet<>(automationReserved);
                HopperRedstoneTransferService.RedstoneTransferContext redstoneCtx =
                        new HopperRedstoneTransferService.RedstoneTransferContext(
                                craftService, smeltService, pluginConfig, redstoneReserved);
                HopperRedstoneTransferService.pushStep(block, template, keys, pluginConfig, redstoneMax, redstoneCtx);
            }

            boolean transferGateOpen = lane.ticksSinceLastStep() >= lane.transferTick();
            if (!transferGateOpen) {
                if (!redstonePowered) {
                    Set<Integer> automationReserved = runAutomation(block, template, lane, loc);
                    reservation.setReserved(loc, automationReserved);
                }
                boolean keepWaiting = HopperWorkEvaluator.shouldRemainInQueue(block, lane, keys, smeltService,
                        craftService) || redstonePowered;
                laneRegistry.removeLaneFromQueueAfterTick(lane, keepWaiting);
                continue;
            }
            lane.resetTicksSinceLastStep();

            boolean transferred = false;
            Set<Integer> pullReserved = collectAutomationReserved(loc);
            if (isReverse && !redstonePowered) {
                int pulled = HopperTransferReverse.pullStep(block, template, keys, reservation, maxItem);
                transferred = pulled > 0;
            } else if (!redstonePowered) {
                int pulled = HopperTransferForward.pullStep(block, template, keys, maxItem, pullReserved);
                transferred = pulled > 0;
            }

            Set<Integer> reserved = runAutomation(block, template, lane, loc);
            reservation.setReserved(loc, reserved);

            if (isReverse && !redstonePowered) {
                HopperTransferReverse.ReverseTransferResult reverseResult = HopperTransferReverse.pushStep(
                        block, template, keys, reservation, maxItem, reverseCtx);
                if (reverseResult.pushTargetFull()) {
                    laneRegistry.markTargetFull(loc);
                } else if (reverseResult.moved() > 0) {
                    laneRegistry.invalidateTargetSpace(loc);
                    transferred = true;
                }
            } else if (!redstonePowered) {
                int pushed = HopperTransferForward.pushStep(block, template, keys, reserved, maxItem, forwardCtx);
                if (pushed > 0) {
                    transferred = true;
                }
            }

            if (transferred) {
                HopperLevelDef levelDef = HopperLevelResolver.resolveForBlock(block, keys, updateConfig);
                if (levelDef != null) {
                    HopperTransferGate.getInstance().recordTransfer(block, levelDef,
                            GameTickCounter.getInstance().currentTick());
                }
            }

            boolean keep = HopperWorkEvaluator.shouldRemainInQueue(block, lane, keys, smeltService, craftService)
                    || redstonePowered;
            laneRegistry.removeLaneFromQueueAfterTick(lane, keep);
        }
    }
}
