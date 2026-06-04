package xlingran;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 主线程 game tick 计数（Spigot API 无 {@code Bukkit.getCurrentTick()} 时的等价物）。
 */
public final class GameTickCounter {

    private static final GameTickCounter INSTANCE = new GameTickCounter();

    private volatile long tick;
    private BukkitTask task;

    private GameTickCounter() {
    }

    public static GameTickCounter getInstance() {
        return INSTANCE;
    }

    public void start(JavaPlugin plugin) {
        stop();
        tick = 0L;
        // 与 game tick 对齐：首帧即 +1，此后每 tick +1（避免首 1 tick 内 window 基准为 0 的偏差）
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick++, 0L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public long currentTick() {
        return tick;
    }
}
