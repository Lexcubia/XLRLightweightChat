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
    private int transferTick = 24;
    private int maxItem = 1;
    private int ticksSinceLastStep;
    private int sleepCooldownTicks;
    private int idleTicks;

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

    public int transferTick() {
        return transferTick;
    }

    public void setTransferTick(int transferTick) {
        this.transferTick = transferTick;
    }

    public int maxItem() {
        return maxItem;
    }

    public void setMaxItem(int maxItem) {
        this.maxItem = maxItem;
    }

    public int ticksSinceLastStep() {
        return ticksSinceLastStep;
    }

    private static final int GLOBAL_TICK_PERIOD = 8;

    public void incrementTicksSinceLastStep() {
        ticksSinceLastStep += GLOBAL_TICK_PERIOD;
    }

    public void resetTicksSinceLastStep() {
        ticksSinceLastStep = 0;
    }

    public int sleepCooldownTicks() {
        return sleepCooldownTicks;
    }

    public void setSleepCooldownTicks(int sleepCooldownTicks) {
        this.sleepCooldownTicks = Math.max(0, sleepCooldownTicks);
    }

    public void decrementSleepCooldown(int delta) {
        if (sleepCooldownTicks > 0) {
            sleepCooldownTicks = Math.max(0, sleepCooldownTicks - delta);
        }
    }

    public int idleTicks() {
        return idleTicks;
    }

    public void addIdleTicks(int delta) {
        idleTicks += delta;
    }

    public void resetIdleTicks() {
        idleTicks = 0;
    }

    public static String laneKey(Location loc) {
        if (loc.getWorld() == null) {
            return "";
        }
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
