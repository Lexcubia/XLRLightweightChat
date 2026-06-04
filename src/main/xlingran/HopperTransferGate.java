package xlingran;

import org.bukkit.Location;
import org.bukkit.block.Block;
import xlingran.core.HopperLane;
import xlingran.gui.HopperLevelDef;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 漏斗等级传输门控：transfer-tick 窗口 + 每窗口 max-item 次数（原版 MoveItem / 吸取 / 插件反向共用）。
 */
public final class HopperTransferGate {

    private static final HopperTransferGate INSTANCE = new HopperTransferGate();

    private final ConcurrentHashMap<String, WindowState> byLocation = new ConcurrentHashMap<>();

    private HopperTransferGate() {
    }

    public static HopperTransferGate getInstance() {
        return INSTANCE;
    }

    /**
     * 尝试占用一次传输配额；失败表示本窗口已达 max-item 或仍在冷却。
     */
    public boolean tryAcquire(Block hopper, HopperLevelDef def, long currentTick) {
        if (hopper == null || def == null) {
            return false;
        }
        String key = HopperLane.laneKey(hopper.getLocation());
        if (key.isEmpty()) {
            return false;
        }
        WindowState state = byLocation.computeIfAbsent(key, k -> new WindowState());
        synchronized (state) {
            if (currentTick - state.windowStartTick >= def.transferTick()) {
                state.windowStartTick = currentTick;
                state.movesInWindow = 0;
            }
            if (state.movesInWindow >= def.maxItem()) {
                return false;
            }
            state.movesInWindow++;
            return true;
        }
    }

    /**
     * 插件反向搬运成功时计入配额（与 MoveItem 共用窗口）。
     */
    public void recordMoves(Block hopper, HopperLevelDef def, long currentTick, int count) {
        if (hopper == null || def == null || count <= 0) {
            return;
        }
        String key = HopperLane.laneKey(hopper.getLocation());
        if (key.isEmpty()) {
            return;
        }
        WindowState state = byLocation.computeIfAbsent(key, k -> new WindowState());
        synchronized (state) {
            if (currentTick - state.windowStartTick >= def.transferTick()) {
                state.windowStartTick = currentTick;
                state.movesInWindow = 0;
            }
            state.movesInWindow = Math.min(def.maxItem(), state.movesInWindow + count);
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

    private static final class WindowState {
        long windowStartTick;
        int movesInWindow;
    }
}
