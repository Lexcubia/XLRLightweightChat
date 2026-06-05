package xlingran.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import xlingran.HopperAutoSmeltService;
import xlingran.HopperBlockConfig;
import xlingran.HopperKeys;
import xlingran.HopperTemplate;
import xlingran.HopperTemplateManager;
import xlingran.HopperLevelResolver;
import xlingran.HopperTemplateResolver;
import xlingran.XLRHopperConfig;
import xlingran.gui.UpdateConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * active 登记 + workQueue；tick 仅消费 workQueue。
 */
public final class HopperLaneRegistry {

    private final XLRHopperConfig pluginConfig;
    private final Map<String, HopperLane> lanes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> workQueue = new ConcurrentLinkedQueue<>();

    public HopperLaneRegistry(XLRHopperConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public HopperLane getLane(Location loc) {
        return lanes.get(HopperLane.laneKey(loc));
    }

    public HopperLane registerLane(Block block, HopperKeys keys, HopperTemplateManager templateManager,
                                    UpdateConfig updateConfig) {
        if (block == null || block.getType() != Material.HOPPER) {
            return null;
        }
        if (!pluginConfig.isPluginWorld(block)) {
            unregisterLane(block.getLocation());
            return null;
        }
        if (lanes.size() >= pluginConfig.getMaxQueueLimit()) {
            return null;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(block, keys, templateManager);
        Location loc = block.getLocation();
        String key = HopperLane.laneKey(loc);
        if (template == null) {
            unregisterLane(loc);
            return null;
        }
        HopperLane lane = lanes.computeIfAbsent(key, k -> new HopperLane(loc));
        lane.setSnapshot(new FilterSnapshot(template));
        HopperBlockConfig config = HopperBlockConfig.read(block, keys);
        boolean reverseAllowed = pluginConfig.isReverseHopperEnabled();
        lane.setReverse(reverseAllowed && config.isReverseSuction());
        template.normalizeAutomationExclusivity();
        lane.setAutoCraft(pluginConfig.isAutoCraftEnabled() && template.isAutoCraftEnabled());
        lane.setAutoSmelt(pluginConfig.isAutoSmeltEnabled() && template.isAutoSmeltEnabled());
        HopperLevelResolver.applyLevelToLane(lane, block, keys, updateConfig);
        if (!lane.hasAutomation()) {
            removeFromWorkQueue(key);
        }
        return lane;
    }

    public void unregisterLane(Location loc) {
        if (loc == null) {
            return;
        }
        String key = HopperLane.laneKey(loc);
        lanes.remove(key);
        removeFromWorkQueue(key);
    }

    public void offerWork(String laneKey) {
        if (laneKey == null || laneKey.isEmpty()) {
            return;
        }
        int maxSize = pluginConfig.getMaxQueueSize();
        if (maxSize > 0 && workQueue.size() >= maxSize) {
            if (!pluginConfig.isQueueOverflowRetry()) {
                return;
            }
        }
        if (!workQueue.contains(laneKey)) {
            workQueue.offer(laneKey);
        }
    }

    public void removeFromWorkQueue(String laneKey) {
        workQueue.remove(laneKey);
    }

    public void invalidateTargetSpace(Location loc) {
        HopperLane lane = getLane(loc);
        if (lane != null) {
            lane.setTargetHasSpace(true);
        }
    }

    public void markTargetFull(Location loc) {
        HopperLane lane = getLane(loc);
        if (lane != null) {
            lane.setTargetHasSpace(false);
            removeFromWorkQueue(HopperLane.laneKey(loc));
        }
    }

    public List<HopperLane> workQueueSnapshot(int max) {
        List<HopperLane> out = new ArrayList<>();
        for (String key : workQueue) {
            if (out.size() >= max) {
                break;
            }
            HopperLane lane = lanes.get(key);
            if (lane != null) {
                out.add(lane);
            }
        }
        return out;
    }

    public void removeLaneFromQueueAfterTick(HopperLane lane, boolean keepInQueue) {
        String key = HopperLane.laneKey(lane.location());
        if (keepInQueue) {
            if (!workQueue.contains(key)) {
                workQueue.offer(key);
            }
        } else {
            workQueue.remove(key);
        }
    }

}
