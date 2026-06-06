package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import xlingran.core.HopperLaneListener;
import xlingran.core.HopperLaneRegistry;
import xlingran.display.HopperOverlayDisplayService;
import xlingran.display.HopperOverlayListener;
import xlingran.gui.GuiConfig;
import xlingran.gui.MessageConfig;
import xlingran.gui.TextPlaceholders;
import xlingran.gui.UpdateConfig;
import xlingran.storage.ShanDatabase;
import xlingran.storage.TemplateRepository;

import java.util.Map;

public class Shan extends JavaPlugin {

    private static Shan instance;

    private HopperTemplateManager templateManager;
    private PlayerGuiSession playerGuiSession;
    private GuiConfig guiConfig;
    private MessageConfig messageConfig;
    private UpdateConfig updateConfig;
    private TemplateRepository templateRepository;
    private ShanDatabase database;
    private Gui gui;
    private HopperKeys hopperKeys;
    private HopperTickService hopperTickService;
    private HopperLaneListener hopperLaneListener;
    private HopperLaneRegistry laneRegistry;
    private HopperOverlayDisplayService overlayDisplayService;
    private XLRHopperConfig pluginConfig;

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

    public UpdateConfig getUpdateConfig() {
        return updateConfig;
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

    public XLRHopperConfig getPluginConfig() {
        return pluginConfig;
    }

    public HopperOverlayDisplayService getOverlayDisplayService() {
        return overlayDisplayService;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        pluginConfig = new XLRHopperConfig(this);
        String configVersion = pluginConfig.getConfigVersion();
        String pluginVersion = getDescription().getVersion();
        if (!configVersion.equals(pluginVersion)) {
            getLogger().warning("[XLRHopper] config.yml Version=" + configVersion
                    + " 与 plugin.yml 版本 " + pluginVersion + " 不一致");
        }

        templateManager = new HopperTemplateManager();
        playerGuiSession = new PlayerGuiSession();
        guiConfig = new GuiConfig(this);
        guiConfig.load();
        messageConfig = new MessageConfig(this);
        messageConfig.load();
        updateConfig = new UpdateConfig(this);
        updateConfig.load();

        database = new ShanDatabase(this);
        try {
            database.initSchema();
        } catch (Exception e) {
            getLogger().severe("[XLRHopper] shan.db 初始化失败: " + e.getMessage());
        }
        templateRepository = new TemplateRepository(this, database);

        hopperKeys = new HopperKeys(this);
        GameTickCounter.getInstance().start(this);
        laneRegistry = new HopperLaneRegistry(pluginConfig);
        hopperTickService = new HopperTickService(this, templateManager, hopperKeys, laneRegistry, updateConfig,
                pluginConfig);
        hopperLaneListener = new HopperLaneListener(this, hopperTickService, pluginConfig);
        if (pluginConfig.isCheckUpdateEnabled()) {
            VersionChecker.checkOnEnable(this);
        }

        boolean decentHologramsAvailable = getServer().getPluginManager().isPluginEnabled("DecentHolograms");
        if (decentHologramsAvailable) {
            Bukkit.getConsoleSender().sendMessage(
                    ChatColor.GREEN + "全息显示前置"
                            + ChatColor.AQUA + " DecentHolograms "
                            + ChatColor.GREEN + "加载成功 ");
        } else {
            Bukkit.getConsoleSender().sendMessage(
                    ChatColor.RED + "未找到全息显示前置"
                            + ChatColor.AQUA + " DecentHolograms "
                            + ChatColor.RED + "已关闭全息显示功能");
        }
        overlayDisplayService = new HopperOverlayDisplayService(this, hopperKeys, templateManager, updateConfig,
                pluginConfig, decentHologramsAvailable);

        gui = new Gui(this, templateManager, playerGuiSession, templateRepository, hopperKeys, guiConfig,
                messageConfig, pluginConfig, hopperTickService, hopperLaneListener, overlayDisplayService);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                templateRepository.loadInto(templateManager);
            } catch (Exception e) {
                getLogger().severe("[XLRHopper] 加载 shan.db 失败: " + e.getMessage());
            }
            Bukkit.getScheduler().runTask(this, () -> Bukkit.getConsoleSender().sendMessage(
                    ChatColor.GREEN + "漏斗模板数据已加载完成"));
        });

        PluginCommand cmd = getCommand("xlrhopper");
        if (cmd != null) {
            HopperCommand hopperCommand = new HopperCommand(gui, this);
            cmd.setExecutor(hopperCommand);
            cmd.setTabCompleter(hopperCommand);
        }

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(
                new HopperListener(this, templateManager, hopperKeys, hopperLaneListener, hopperTickService,
                        messageConfig, updateConfig, pluginConfig),
                this);
        getServer().getPluginManager().registerEvents(hopperLaneListener, this);
        getServer().getPluginManager().registerEvents(
                new HopperOverlayListener(this, overlayDisplayService, pluginConfig), this);
        getServer().getPluginManager().registerEvents(
                new HopperReverseHandler(hopperKeys, hopperTickService, hopperLaneListener), this);
        getServer().getPluginManager().registerEvents(
                new HopperReverseNeighborListener(hopperKeys, hopperLaneListener, pluginConfig), this);
        getServer().getPluginManager().registerEvents(
                new HopperRedstoneListener(hopperKeys, hopperLaneListener, overlayDisplayService, pluginConfig),
                this);
        getServer().getPluginManager().registerEvents(
                new BatchModeListener(hopperKeys, playerGuiSession, hopperLaneListener, messageConfig, pluginConfig),
                this);
        getServer().getPluginManager().registerEvents(
                new HopperSettingsListener(gui, templateManager, hopperKeys, messageConfig), this);

        Bukkit.getConsoleSender().sendMessage(
                ChatColor.GREEN + "欢迎使用寄寄の家 "
                        + ChatColor.AQUA + "漏斗过滤"
                        + ChatColor.GREEN + " 插件,交流群: 943446220");
    }

    @Override
    public void onDisable() {
        GameTickCounter.getInstance().stop();
        if (overlayDisplayService != null) {
            overlayDisplayService.hideAll();
        }
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
        Bukkit.getScheduler().runTask(this, () -> {
            reloadConfig();
            pluginConfig.reload();
            guiConfig.reload();
            messageConfig.reload();
            updateConfig.reload();
            gui.refreshAfterConfigReload();
            HopperTransferGate.getInstance().clearAll();
            if (overlayDisplayService != null) {
                overlayDisplayService.restartPeriodicRefresh();
                overlayDisplayService.restoreAllAfterReload();
            }
            if (sender != null) {
                int status = updateConfig.levelIds().size();
                for (String line : pluginConfig.getReloadLines()) {
                    sender.sendMessage(TextPlaceholders.apply(line, Map.of("status", String.valueOf(status))));
                }
            }
        });
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                templateRepository.loadInto(templateManager);
                asyncReindexLoadedChunks();
            } catch (Exception e) {
                getLogger().severe("[XLRHopper] 模板数据库 reload 失败: " + e.getMessage());
                Bukkit.getScheduler().runTask(this, () -> {
                    if (sender != null) {
                        if (sender instanceof org.bukkit.entity.Player) {
                            sender.sendMessage(messageConfig.message("reload-fail"));
                        } else {
                            sender.sendMessage(messageConfig.message("reload-console-only-fail"));
                        }
                    }
                });
            }
        });
    }

    private void asyncReindexLoadedChunks() {
        Bukkit.getScheduler().runTask(this, () -> {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                if (!pluginConfig.isPluginWorld(world)) {
                    continue;
                }
                for (Chunk chunk : world.getLoadedChunks()) {
                    for (org.bukkit.block.Block block : HopperChunkScanUtil.hoppersInChunk(chunk)) {
                        hopperTickService.registerLoadedHopper(block);
                    }
                }
            }
        });
    }
}
