package xlingran.gui;

import java.util.List;

public final class HopperLevelDef {

    private final String id;
    private final String displayName;
    private final List<String> lore;
    private final int transferTick;
    private final int maxItem;

    public HopperLevelDef(String id, String displayName, List<String> lore, int transferTick, int maxItem) {
        this.id = id;
        this.displayName = displayName;
        this.lore = List.copyOf(lore);
        this.transferTick = transferTick;
        this.maxItem = maxItem;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public int transferTick() {
        return transferTick;
    }

    public int maxItem() {
        return maxItem;
    }
}
