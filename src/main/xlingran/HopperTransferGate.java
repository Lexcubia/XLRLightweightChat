package xlingran;

import org.bukkit.Location;
import org.bukkit.block.Block;
import xlingran.core.HopperLane;
import xlingran.gui.HopperLevelDef;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 漏斗等级容器间传输门控：transfer-tick 为传输间隔；max-item（传输速度）由 InventoryMoveItem 等容器间事件侧限制。
 * 地面 InventoryPickupItemEvent 不走此门控。
 */
public final class HopperTransferGate {

    private static final HopperTransferGate INSTANCE = new HopperTransferGate();

    private final ConcurrentHashMap<String, CooldownState> byLocation = new ConcurrentHashMap<>();

    private HopperTransferGate() {
    }

    public static HopperTransferGate getInstance() {
        return INSTANCE;
    }

    /**
     * 尝试开始一次传输；失败表示仍在 transfer-tick 冷却中。
     */
    public boolean tryAcquire(Block hopper, HopperLevelDef def, long currentTick) {
        if (hopper == null || def == null) {
            return false;
        }
        String key = HopperLane.laneKey(hopper.getLocation());
        if (key.isEmpty()) {
            return false;
        }
        CooldownState state = byLocation.computeIfAbsent(key, k -> new CooldownState());
        synchronized (state) {
            if (currentTick < state.nextAllowedTick) {
                return false;
            }
            state.nextAllowedTick = currentTick + def.transferTick();
            return true;
        }
    }

    /**
     * 插件反向搬运成功时推进冷却（一步反向计为一次传输）。
     */
    public void recordTransfer(Block hopper, HopperLevelDef def, long currentTick) {
        if (hopper == null || def == null) {
            return;
        }
        String key = HopperLane.laneKey(hopper.getLocation());
        if (key.isEmpty()) {
            return;
        }
        CooldownState state = byLocation.computeIfAbsent(key, k -> new CooldownState());
        synchronized (state) {
            state.nextAllowedTick = currentTick + def.transferTick();
        }
    }

    public void clear(Location loc) {
        if (loc == null) {
            return;
        }
        byLocation.remove(HopperLane.laneKey(loc));
    }

    public void clearAll() {
        byLocation.clear();
    }

    private static final class CooldownState {
        long nextAllowedTick;
    }
}
