package xlingran;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopperKeys {

    public final NamespacedKey template;
    public final NamespacedKey owner;
    public final NamespacedKey redstoneListToggle;
    public final NamespacedKey reverseSuction;
    public final NamespacedKey hoverDisplay;
    public final NamespacedKey overlayMarker;
    public final NamespacedKey hopperLevel;
    public final NamespacedKey hopperLevelItem;

    public HopperKeys(JavaPlugin plugin) {
        this.template = new NamespacedKey(plugin, "template");
        this.owner = new NamespacedKey(plugin, "owner");
        this.redstoneListToggle = new NamespacedKey(plugin, "redstone-list-toggle");
        this.reverseSuction = new NamespacedKey(plugin, "reverse-suction");
        this.hoverDisplay = new NamespacedKey(plugin, "hover-display");
        this.overlayMarker = new NamespacedKey(plugin, "overlay-marker");
        this.hopperLevel = new NamespacedKey(plugin, "hopper-level");
        this.hopperLevelItem = new NamespacedKey(plugin, "hopper-level-item");
    }
}
