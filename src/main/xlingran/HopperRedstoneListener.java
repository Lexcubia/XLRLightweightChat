package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import xlingran.core.HopperLaneListener;
import xlingran.display.HopperOverlayDisplayService;

/**
 * 红石信号变化时刷新开启 redstone-list-toggle 的邻域漏斗。
 */
public final class HopperRedstoneListener implements Listener {

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final HopperKeys keys;
    private final HopperLaneListener laneListener;
    private final HopperOverlayDisplayService overlayService;
    private final XLRHopperConfig pluginConfig;

    public HopperRedstoneListener(HopperKeys keys, HopperLaneListener laneListener,
                                  HopperOverlayDisplayService overlayService, XLRHopperConfig pluginConfig) {
        this.keys = keys;
        this.laneListener = laneListener;
        this.overlayService = overlayService;
        this.pluginConfig = pluginConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (!pluginConfig.isRedstoneToggleEnabled()) {
            return;
        }
        Block changed = event.getBlock();
        if (changed == null) {
            return;
        }
        if (changed.getType() == Material.HOPPER) {
            refreshIfRedstoneHopper(changed);
        }
        for (BlockFace face : ADJACENT_FACES) {
            Block neighbor = changed.getRelative(face);
            if (neighbor.getType() == Material.HOPPER) {
                refreshIfRedstoneHopper(neighbor);
            }
        }
    }

    private void refreshIfRedstoneHopper(Block hopper) {
        if (!pluginConfig.isPluginWorld(hopper)) {
            return;
        }
        HopperBlockConfig config = HopperBlockConfig.read(hopper, keys);
        if (!config.isRedstoneListToggle()) {
            return;
        }
        laneListener.scheduleEvaluateImmediate(hopper);
        if (overlayService.isHoverEnabled(hopper)) {
            overlayService.refreshDebounced(hopper);
        }
    }
}
