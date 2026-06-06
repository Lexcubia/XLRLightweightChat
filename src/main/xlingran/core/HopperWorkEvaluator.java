package xlingran.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xlingran.HopperAutoCraftService;
import xlingran.HopperAutoSmeltService;
import xlingran.HopperContainerUtil;
import xlingran.HopperKeys;
import xlingran.HopperRedstoneTransferService;
import xlingran.Shan;
import xlingran.XLRHopperConfig;

/**
 * 事件侧入队判定（禁止在 8 tick 定时器内调用）。
 */
public final class HopperWorkEvaluator {

    private static final int TICK_STEP = 8;

    private HopperWorkEvaluator() {
    }

    public static void evaluateAndQueue(Block block, HopperLaneRegistry registry, HopperKeys keys,
                                      HopperAutoSmeltService smeltService,
                                      HopperAutoCraftService craftService) {
        if (block == null || block.getType() != Material.HOPPER) {
            return;
        }
        XLRHopperConfig config = config();
        if (config != null && !config.isPluginWorld(block)) {
            return;
        }
        HopperLane lane = registry.getLane(block.getLocation());
        if (lane == null || !lane.hasSnapshot()) {
            return;
        }
        if (!lane.hasAutomation() && !hasRedstonePoweredWork(block, keys, config)
                && !hasForwardTransferWork(block, lane, keys, config)) {
            return;
        }
        if (lane.sleepCooldownTicks() > 0) {
            lane.decrementSleepCooldown(TICK_STEP);
            return;
        }
        String key = HopperLane.laneKey(block.getLocation());
        if (!pendingWork(block, lane, keys, smeltService, craftService, config)) {
            applySleep(lane, config);
            registry.removeFromWorkQueue(key);
            return;
        }
        lane.resetIdleTicks();
        lane.setSleepCooldownTicks(0);
        registry.offerWork(key);
    }

    public static void markPending(Block block, HopperLaneRegistry registry, HopperKeys keys,
                                   HopperAutoSmeltService smeltService,
                                   HopperAutoCraftService craftService) {
        registry.invalidateTargetSpace(block.getLocation());
        evaluateAndQueue(block, registry, keys, smeltService, craftService);
    }

    public static boolean shouldRemainInQueue(Block block, HopperLane lane, HopperKeys keys,
                                              HopperAutoSmeltService smeltService,
                                              HopperAutoCraftService craftService) {
        return pendingWork(block, lane, keys, smeltService, craftService, config());
    }

    private static void applySleep(HopperLane lane, XLRHopperConfig config) {
        if (config == null) {
            return;
        }
        lane.addIdleTicks(TICK_STEP);
        int cooldown;
        if (lane.idleTicks() >= config.getDeepSleepTick()) {
            cooldown = config.getDeepSleepTick();
        } else if (lane.idleTicks() >= config.getSleepTick()) {
            cooldown = config.getSleepTick();
        } else {
            return;
        }
        lane.setSleepCooldownTicks(cooldown);
    }

    private static boolean hasRedstonePoweredWork(Block block, HopperKeys keys, XLRHopperConfig config) {
        return config != null && HopperRedstoneTransferService.isRedstonePoweredTransferActive(block, keys, config);
    }

    private static boolean hasForwardTransferWork(Block block, HopperLane lane, HopperKeys keys,
                                                  XLRHopperConfig config) {
        if (lane.isReverse() || hasRedstonePoweredWork(block, keys, config)) {
            return false;
        }
        var template = lane.template();
        if (template == null) {
            return false;
        }
        if (!(block.getState() instanceof Container container)) {
            return false;
        }
        Inventory inv = container.getInventory();
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && !stack.getType().isAir() && template.allows(stack, block, keys)) {
                return true;
            }
        }
        Block above = block.getRelative(BlockFace.UP);
        Inventory aboveInv = HopperContainerUtil.getContainerInventory(above);
        if (aboveInv != null) {
            for (ItemStack stack : aboveInv.getContents()) {
                if (stack != null && !stack.getType().isAir() && template.allows(stack, block, keys)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean pendingWork(Block block, HopperLane lane, HopperKeys keys,
                                       HopperAutoSmeltService smeltService,
                                       HopperAutoCraftService craftService, XLRHopperConfig config) {
        if (hasRedstonePoweredWork(block, keys, config)) {
            return true;
        }
        if (hasForwardTransferWork(block, lane, keys, config)) {
            return true;
        }
        if (registryHasSmelt(smeltService, block.getLocation())) {
            return true;
        }
        if (registryHasCraft(craftService, block.getLocation())) {
            return true;
        }
        if (!(block.getState() instanceof Container container)) {
            return false;
        }
        Inventory inv = container.getInventory();
        var template = lane.template();
        if (template == null) {
            return false;
        }
        if (config != null && config.isSleepEmptyHopper() && isInventoryEmpty(inv)) {
            if (!lane.isReverse()) {
                return false;
            }
        }
        if (config != null && config.isSleepFullContainer() && !lane.isReverse() && isDownstreamFull(block)) {
            return false;
        }
        if (lane.isAutoCraft() || lane.isAutoSmelt()) {
            for (ItemStack stack : inv.getContents()) {
                if (stack != null && !stack.getType().isAir() && template.allows(stack, block, keys)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDownstreamFull(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        Inventory belowInv = HopperContainerUtil.getContainerInventory(below);
        if (belowInv == null) {
            return false;
        }
        for (ItemStack stack : belowInv.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean registryHasSmelt(HopperAutoSmeltService smeltService, Location loc) {
        return smeltService != null && smeltService.hasJob(loc);
    }

    private static boolean registryHasCraft(HopperAutoCraftService craftService, Location loc) {
        return craftService != null && craftService.hasJob(loc);
    }

    private static XLRHopperConfig config() {
        Shan plugin = Shan.getInstance();
        return plugin != null ? plugin.getPluginConfig() : null;
    }
}
