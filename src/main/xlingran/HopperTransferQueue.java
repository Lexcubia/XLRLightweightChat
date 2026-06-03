package xlingran;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按漏斗方块串行化传输请求，TPS 低时合并排队、单 worker 处理，避免重复 runTask 导致刷物品。
 */
public final class HopperTransferQueue {

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final PlayerBoxManager boxManager;
    private final Runnable persistBoxes;
    private final Map<String, HopperLane> lanes = new ConcurrentHashMap<>();

    public HopperTransferQueue(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys,
                               PlayerBoxManager boxManager, Runnable persistBoxes) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.keys = keys;
        this.boxManager = boxManager;
        this.persistBoxes = persistBoxes;
    }

    public void enqueue(Location hopperLoc, Location belowLoc, UUID owner, String boxName,
                        ItemStack prototype, int amount) {
        if (amount <= 0 || prototype == null || prototype.getType().isAir()) {
            return;
        }
        String key = laneKey(hopperLoc);
        HopperLane lane = lanes.computeIfAbsent(key, k -> new HopperLane());
        lane.queue.offer(new TransferRequest(
                hopperLoc.clone(), belowLoc.clone(), owner, boxName, prototype.clone(), amount));
        scheduleWorker(key, lane);
    }

    private void scheduleWorker(String key, HopperLane lane) {
        if (!lane.workerScheduled.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> drainLane(key));
    }

    private void drainLane(String key) {
        HopperLane lane = lanes.get(key);
        if (lane == null) {
            return;
        }
        boolean boxDirty = false;
        try {
            TransferRequest req;
            int processed = 0;
            while ((req = lane.queue.poll()) != null) {
                if (++processed > 256) {
                    plugin.getLogger().warning("[XLRHopper] 漏斗传输队列积压过多，剩余延后处理: " + key);
                    break;
                }
                HopperDualPathTransfer.TransferResult result = HopperDualPathTransfer.execute(
                        req.hopperLoc, req.belowLoc, req.owner, req.boxName, req.prototype, req.amount,
                        templateManager, keys, boxManager);
                if (result == HopperDualPathTransfer.TransferResult.BOX_CHANGED) {
                    boxDirty = true;
                }
            }
        } finally {
            lane.workerScheduled.set(false);
            if (!lane.queue.isEmpty()) {
                scheduleWorker(key, lane);
            } else {
                lanes.remove(key, lane);
            }
        }
        if (boxDirty && persistBoxes != null) {
            persistBoxes.run();
        }
    }

    private static String laneKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static final class HopperLane {
        private final ConcurrentLinkedQueue<TransferRequest> queue = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean workerScheduled = new AtomicBoolean(false);
    }

    private record TransferRequest(Location hopperLoc, Location belowLoc, UUID owner, String boxName,
                                   ItemStack prototype, int amount) {
    }
}
