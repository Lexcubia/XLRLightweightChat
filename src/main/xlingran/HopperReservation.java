package xlingran;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 合成/熔炼预留的漏斗槽位，不参与反向搬运输出。 */
public final class HopperReservation {

    private final Map<String, Set<Integer>> reservedSlots = new ConcurrentHashMap<>();

    public void setReserved(Location hopperLoc, Set<Integer> slots) {
        if (hopperLoc == null || hopperLoc.getWorld() == null) {
            return;
        }
        String key = laneKey(hopperLoc);
        if (slots == null || slots.isEmpty()) {
            reservedSlots.remove(key);
        } else {
            reservedSlots.put(key, new HashSet<>(slots));
        }
    }

    public Set<Integer> getReserved(Location hopperLoc) {
        if (hopperLoc == null) {
            return Collections.emptySet();
        }
        Set<Integer> slots = reservedSlots.get(laneKey(hopperLoc));
        return slots == null ? Collections.emptySet() : Collections.unmodifiableSet(slots);
    }

    public boolean isReserved(Location hopperLoc, int slot) {
        return getReserved(hopperLoc).contains(slot);
    }

    public void clear(Location hopperLoc) {
        if (hopperLoc != null && hopperLoc.getWorld() != null) {
            reservedSlots.remove(laneKey(hopperLoc));
        }
    }

    private static String laneKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
