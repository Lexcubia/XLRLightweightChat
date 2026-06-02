package xlingran;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopperKeys {

    public final NamespacedKey template;
    public final NamespacedKey owner;

    public HopperKeys(JavaPlugin plugin) {
        this.template = new NamespacedKey(plugin, "template");
        this.owner = new NamespacedKey(plugin, "owner");
    }
}
