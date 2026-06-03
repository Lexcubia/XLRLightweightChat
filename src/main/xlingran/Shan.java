package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class Shan extends JavaPlugin {

    private static Shan instance;

    private HopperTemplateManager templateManager;
    private PlayerGuiSession playerGuiSession;
    private DataStore dataStore;
    private PlayerBoxManager boxManager;
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

    public PlayerBoxManager getBoxManager() {
        return boxManager;
    }

    public HopperKeys getHopperKeys() {
        return hopperKeys;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("data.yml", false);

        templateManager = new HopperTemplateManager();
        playerGuiSession = new PlayerGuiSession();
        dataStore = new DataStore(getDataFolder(), getLogger());
        boxManager = new PlayerBoxManager();
        dataStore.load(templateManager, boxManager);

        hopperKeys = new HopperKeys(this);
        gui = new Gui(this, templateManager, playerGuiSession, dataStore, boxManager, hopperKeys);

        PluginCommand cmd = getCommand("xlrhopper");
        if (cmd != null) {
            HopperCommand hopperCommand = new HopperCommand(gui);
            cmd.setExecutor(hopperCommand);
            cmd.setTabCompleter(hopperCommand);
        }

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new HopperListener(this, templateManager, hopperKeys), this);
        getServer().getPluginManager().registerEvents(new HopperReverseHandler(this, templateManager, hopperKeys), this);
        getServer().getPluginManager().registerEvents(
                new HopperBoxOutputHandler(templateManager, hopperKeys, boxManager, () -> {
                    if (dataStore != null && templateManager != null && boxManager != null) {
                        dataStore.save(templateManager, boxManager);
                    }
                }), this);
        getServer().getPluginManager().registerEvents(new BatchModeListener(hopperKeys, playerGuiSession), this);
        getServer().getPluginManager().registerEvents(new HopperSettingsListener(gui, templateManager, hopperKeys), this);

        Bukkit.getConsoleSender().sendMessage(
                ChatColor.GREEN + "欢迎使用寄寄の家 "
                        + ChatColor.AQUA + "漏斗过滤"
                        + ChatColor.GREEN + " 插件,交流群: 943446220");
    }

    @Override
    public void onDisable() {
        if (dataStore != null && templateManager != null && boxManager != null) {
            dataStore.save(templateManager, boxManager);
        }
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.RED + "插件 "
                        + ChatColor.AQUA + "漏斗过滤"
                        + ChatColor.RED + " 已卸载，感谢使用寄寄の家插件!");
    }
}
