package xlingran.core;

import org.bukkit.Location;
import xlingran.HopperTemplate;

public final class HopperLane {

    private final Location location;
    private FilterSnapshot snapshot;
    private boolean reverse;
    private boolean autoCraft;
    private boolean autoSmelt;
    private boolean targetHasSpace = true;

    public HopperLane(Location location) {
        this.location = location.clone();
    }

    public Location location() {
        return location.clone();
    }

    public FilterSnapshot snapshot() {
        return snapshot;
    }

    public void setSnapshot(FilterSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public boolean hasSnapshot() {
        return snapshot != null && snapshot.template() != null;
    }

    public HopperTemplate template() {
        return snapshot != null ? snapshot.template() : null;
    }

    public boolean isReverse() {
        return reverse;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public boolean isAutoCraft() {
        return autoCraft;
    }

    public void setAutoCraft(boolean autoCraft) {
        this.autoCraft = autoCraft;
    }

    public boolean isAutoSmelt() {
        return autoSmelt;
    }

    public void setAutoSmelt(boolean autoSmelt) {
        this.autoSmelt = autoSmelt;
    }

    public boolean hasAutomation() {
        return reverse || autoCraft || autoSmelt;
    }

    public boolean isTargetHasSpace() {
        return targetHasSpace;
    }

    public void setTargetHasSpace(boolean targetHasSpace) {
        this.targetHasSpace = targetHasSpace;
    }

    public static String laneKey(Location loc) {
        if (loc.getWorld() == null) {
            return "";
        }
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
