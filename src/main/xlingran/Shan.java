package xlingran;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class Shan extends JavaPlugin {

    private static Shan instance;

    private HopperTemplateManager templateManager;
    private PlayerGuiSession playerGuiSession;
    private DataStore dataStore;
    private Gui gui;
    private HopperKeys hopperKeys;

    public static Shan getInstance() {
        return instance;
    }

    public HopperTemplateManager getTemplateManager() {
        return templateManager;
    }

    public Gui getGui() {
        return gui;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("data.yml", false);

        templateManager = new HopperTemplateManager();
        playerGuiSession = new PlayerGuiSession();
        dataStore = new DataStore(getDataFolder(), getLogger());
        dataStore.load(templateManager);

        hopperKeys = new HopperKeys(this);
        gui = new Gui(this, templateManager, playerGuiSession, dataStore);

        PluginCommand cmd = getCommand("xlrhopper");
        if (cmd != null) {
            cmd.setExecutor(new HopperCommand(gui));
        }

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new HopperListener(templateManager, hopperKeys), this);

        getLogger().info("[XLRHopper] 插件已加载 (v1.0.0)");
    }

    @Override
    public void onDisable() {
        if (dataStore != null && templateManager != null) {
            dataStore.save(templateManager);
        }
        getLogger().info("[XLRHopper] 插件已卸载");
    }
}
