package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import xlingran.core.HopperLaneListener;
import xlingran.core.HopperLaneRegistry;
import xlingran.gui.GuiConfig;
import xlingran.gui.MessageConfig;
import xlingran.storage.ShanDatabase;
import xlingran.storage.TemplateRepository;

public class Shan extends JavaPlugin {

    private static Shan instance;

    private HopperTemplateManager templateManager;
    private PlayerGuiSession playerGuiSession;
    private GuiConfig guiConfig;
    private MessageConfig messageConfig;
    private TemplateRepository templateRepository;
    private ShanDatabase database;
    private Gui gui;
    private HopperKeys hopperKeys;
    private HopperTickService hopperTickService;
    private HopperLaneListener hopperLaneListener;
    private HopperLaneRegistry laneRegistry;

    public static Shan getInstance() {
        return instance;
    }

    public HopperTemplateManager getTemplateManager() {
        return templateManager;
    }

    public Gui getGui() {
        return gui;
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
    }

    public MessageConfig getMessageConfig() {
        return messageConfig;
    }

    public TemplateRepository getTemplateRepository() {
        return templateRepository;
    }

    public HopperKeys getHopperKeys() {
        return hopperKeys;
    }

    public HopperTickService getHopperTickService() {
        return hopperTickService;
    }

    public HopperLaneListener getHopperLaneListener() {
        return hopperLaneListener;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("Gui.yml", false);
        saveResource("Message.yml", false);

        templateManager = new HopperTemplateManager();
        playerGuiSession = new PlayerGuiSession();
        guiConfig = new GuiConfig(this);
        guiConfig.load();
        messageConfig = new MessageConfig(this);
        messageConfig.load();

        database = new ShanDatabase(this);
        try {
            database.initSchema();
        } catch (Exception e) {
            getLogger().severe("[XLRHopper] shan.db 初始化失败: " + e.getMessage());
        }
        templateRepository = new TemplateRepository(this, database);

        hopperKeys = new HopperKeys(this);
        laneRegistry = new HopperLaneRegistry();
        hopperTickService = new HopperTickService(this, templateManager, hopperKeys, laneRegistry);
        hopperLaneListener = new HopperLaneListener(this, hopperTickService);

        gui = new Gui(this, templateManager, playerGuiSession, templateRepository, hopperKeys, guiConfig,
                messageConfig, hopperTickService, hopperLaneListener);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                templateRepository.migrateFromDataYmlIfNeeded(templateManager);
                templateRepository.loadInto(templateManager);
            } catch (Exception e) {
                getLogger().severe("[XLRHopper] 加载 shan.db 失败: " + e.getMessage());
            }
            Bukkit.getScheduler().runTask(this, () -> getLogger().info("[XLRHopper] 模板数据已加载"));
        });

        PluginCommand cmd = getCommand("xlrhopper");
        if (cmd != null) {
            HopperCommand hopperCommand = new HopperCommand(gui, this);
            cmd.setExecutor(hopperCommand);
            cmd.setTabCompleter(hopperCommand);
        }

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(
                new HopperListener(this, templateManager, hopperKeys, hopperLaneListener, messageConfig), this);
        getServer().getPluginManager().registerEvents(hopperLaneListener, this);
        getServer().getPluginManager().registerEvents(
                new HopperReverseHandler(hopperKeys, hopperTickService, hopperLaneListener), this);
        getServer().getPluginManager().registerEvents(
                new BatchModeListener(hopperKeys, playerGuiSession, hopperLaneListener, messageConfig), this);
        getServer().getPluginManager().registerEvents(
                new HopperSettingsListener(gui, templateManager, hopperKeys, messageConfig), this);

        Bukkit.getConsoleSender().sendMessage(
                ChatColor.GREEN + "欢迎使用寄寄の家 "
                        + ChatColor.AQUA + "漏斗过滤"
                        + ChatColor.GREEN + " 插件,交流群: 943446220");
    }

    @Override
    public void onDisable() {
        if (templateRepository != null && templateManager != null) {
            templateRepository.flushSync(templateManager);
        }
        if (database != null) {
            database.close();
        }
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.RED + "插件 "
                        + ChatColor.AQUA + "漏斗过滤"
                        + ChatColor.RED + " 已卸载，感谢使用寄寄の家插件!");
    }

    public void reload(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                guiConfig.reload();
                messageConfig.reload();
                templateRepository.loadInto(templateManager);
            } catch (Exception e) {
                getLogger().severe("[XLRHopper] reload 失败: " + e.getMessage());
                Bukkit.getScheduler().runTask(this, () -> {
                    if (sender != null) {
                        if (sender instanceof org.bukkit.entity.Player) {
                            sender.sendMessage(messageConfig.message("reload-fail"));
                        } else {
                            sender.sendMessage(messageConfig.message("reload-console-only-fail"));
                        }
                    }
                });
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                asyncReindexLoadedChunks();
                if (sender != null) {
                    sender.sendMessage(messageConfig.message("reload-success"));
                }
            });
        });
    }

    private void asyncReindexLoadedChunks() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            java.util.List<org.bukkit.Location> coords = new java.util.ArrayList<>();
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                                if (chunk.getBlock(x, y, z).getType() == org.bukkit.Material.HOPPER) {
                                    coords.add(chunk.getBlock(x, y, z).getLocation());
                                }
                            }
                        }
                    }
                }
            }
            Bukkit.getScheduler().runTask(this, () -> {
                for (org.bukkit.Location loc : coords) {
                    hopperTickService.registerLoadedHopper(loc.getBlock());
                }
            });
        });
    }
}
