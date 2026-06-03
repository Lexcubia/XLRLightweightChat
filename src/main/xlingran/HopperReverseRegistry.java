package xlingran;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 记录开启反向吸取的漏斗方块，供定时任务驱动搬运（不依赖 InventoryMoveItemEvent）。 */
public final class HopperReverseRegistry {

    private final Map<String, Location> active = new ConcurrentHashMap<>();

    void setActive(Location location, boolean enabled) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        String key = laneKey(location);
        if (enabled) {
            active.put(key, location.clone());
        } else {
            active.remove(key);
        }
    }

    void syncFromBlock(Block block, HopperKeys keys) {
        if (block == null || block.getType() != Material.HOPPER) {
            return;
        }
        setActive(block.getLocation(), HopperBlockConfig.isReverse(block, keys));
    }

    List<Location> snapshot() {
        return new ArrayList<>(active.values());
    }

    private static String laneKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
