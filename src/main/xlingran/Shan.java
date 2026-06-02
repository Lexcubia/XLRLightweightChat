package xlingran;

import org.bukkit.plugin.java.JavaPlugin;

public class Shan extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        getLogger().info("[XLRHopper] 插件已加载 (v1.0.0)");
    }

    @Override
    public void onDisable() {
        getLogger().info("[XLRHopper] 插件已卸载");
    }
}
