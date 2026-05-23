package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shan extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private FileConfiguration guiConfig; // Gui.yml 配置
    private Map<String, String> colorVariables;
    private Map<Integer, String> playerTitles; // 称号配置：ID -> 前缀
    private final Map<UUID, String> playerCurrentTitles = new HashMap<>(); // 玩家当前穿戴的称号
    private GuiManager guiManager; // GUI 管理器
    private File playerDataFile; // 玩家数据文件
    private FileConfiguration playerData; // 玩家数据配置
    
    // 悬浮提示配置
    private List<String> playerHoverLore; // 玩家名称悬浮提示内容
    private String playerSuggestCommand; // 点击后预填命令（Command）
    private String playerRunCommand; // 点击后直接执行命令（RunCommand）

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        reloadConfig();
        
        // 加载配置
        config = getConfig();
        loadGuiConfig();
        loadColorVariables();
        loadPlayerTitles();
        loadPlayerHoverConfig();
        
        // 加载玩家数据
        loadPlayerData();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 初始化 GUI 管理器
        guiManager = new GuiManager(this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        
        getLogger().info("插件已加载");
    }

    @Override
    public void onDisable() {
        // 保存玩家数据
        savePlayerData();
        getLogger().info("插件已卸载");
    }

    /**
     * 加载Gui.yml配置
     */
    private void loadGuiConfig() {
        File guiFile = new File(getDataFolder(), "Gui.yml");
        if (!guiFile.exists()) {
            saveResource("Gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
    }

    /**
     * 加载玩家数据
     */
    private void loadPlayerData() {
        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            playerData = new YamlConfiguration();
        } else {
            playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        }
        
        // 加载所有在线玩家的称号数据
        for (Map.Entry<String, Object> entry : playerData.getValues(false).entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                String title = entry.getValue().toString();
                playerCurrentTitles.put(uuid, title);
            } catch (IllegalArgumentException e) {
                String uuidErrMsg = config.getString("Cmd.playerUUIDNo", "无效的玩家UUID: %uuid%");
                uuidErrMsg = uuidErrMsg.replace("%uuid%", entry.getKey());
                getLogger().warning(uuidErrMsg);
            }
        }
    }

    /**
     * 保存玩家数据
     */
    private void savePlayerData() {
        if (playerData == null) {
            playerData = new YamlConfiguration();
        }
        
        // 清空旧数据
        playerData = new YamlConfiguration();
        
        // 保存所有玩家的称号数据
        for (Map.Entry<UUID, String> entry : playerCurrentTitles.entrySet()) {
            playerData.set(entry.getKey().toString(), entry.getValue());
        }
        
        // 保存到文件
        try {
            playerData.save(playerDataFile);
        } catch (IOException e) {
            String saveErrMsg = config.getString("Cmd.playerDataNo", "无法保存玩家数据!");
            getLogger().severe(saveErrMsg + " " + e.getMessage());
        }
    }

    /**
     * 监听玩家加入事件，加载称号数据
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 如果玩家数据已加载，不需要额外操作
        // 称号数据已经在 loadPlayerData() 中加载
    }

    /**
     * 监听玩家退出事件，保存称号数据
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家退出时立即保存数据
        savePlayerData();
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        super.reloadConfig();
        config = getConfig();
        loadGuiConfig();
        loadColorVariables();
        loadPlayerTitles();
        loadPlayerHoverConfig();
        // 重新加载玩家数据
        loadPlayerData();
    }

    /**
     * 加载颜色变量配置
     */
    private void loadColorVariables() {
        colorVariables = new HashMap<>();
        if (config.contains("Variable")) {
            ConfigurationSection section = config.getConfigurationSection("Variable");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String value = section.getString(key);
                    if (value != null) {
                        colorVariables.put("%" + key + "%", value);
                    }
                }
            }
        }
    }

    /**
     * 加载称号配置
     */
    private void loadPlayerTitles() {
        playerTitles = new TreeMap<>(); // 使用 TreeMap 保持 ID 顺序
        if (config.contains("PlayerTitle")) {
            ConfigurationSection section = config.getConfigurationSection("PlayerTitle");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        String prefix = section.getString(key);
                        if (prefix != null) {
                            playerTitles.put(id, prefix);
                        }
                    } catch (NumberFormatException e) {
                        // 忽略非数字 ID
                    }
                }
            }
        }
    }

    /**
     * 加载玩家悬浮提示配置
     */
    private void loadPlayerHoverConfig() {
        playerHoverLore = new ArrayList<>();
        playerSuggestCommand = null;
        playerRunCommand = null;
        
        if (config.contains("player")) {
            List<String> playerConfig = config.getStringList("player");
            
            for (String line : playerConfig) {
                if (line.startsWith("Command:")) {
                    // 这是预填命令配置
                    playerSuggestCommand = line.substring(8); // 移除 "Command:" 前缀
                } else if (line.startsWith("RunCommand:")) {
                    // 这是直接执行命令配置
                    playerRunCommand = line.substring(11); // 移除 "RunCommand:" 前缀
                } else {
                    // 这是悬浮提示文本
                    playerHoverLore.add(line);
                }
            }
        }
    }

    /**
     * 监听聊天事件
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // 取消默认消息
        event.setCancelled(true);
        
        // 查找匹配的聊天格式
        String format = findMatchingFormat(player);
        
        if (format == null) {
            // 如果没有匹配格式，检查是否有 default 权限
            if (player.hasPermission("xlr.chat.default")) {
                format = config.getString("Chat.default");
            }
            
            // 如果还是没有，使用第一个格式作为兜底
            if (format == null) {
                format = getDefaultFormat();
            }
        }
        
        if (format == null) {
            // 如果连默认格式都没有，直接发送原始消息
            broadcastMessage(player.getName(), message);
            return;
        }
        
        // 处理格式字符串（返回 BaseComponent[] 以支持悬浮提示）
        BaseComponent[] components = processFormatToComponent(format, player, message);
        
        // 广播消息
        broadcastProcessedMessage(components);
    }

    /**
     * 查找匹配的聊天格式（按优先级从上往下检查）
     * 配置文件中位置靠上的格式优先级更高
     * 找到第一个有权限的格式后立即返回，不再检查后续
     */
    private String findMatchingFormat(Player player) {
        if (!config.contains("Chat")) {
            return null;
        }
        
        ConfigurationSection section = config.getConfigurationSection("Chat");
        if (section == null) {
            return null;
        }
        
        Set<String> formats = section.getKeys(false);
        for (String formatName : formats) {
            String permission = "xlr.chat." + formatName;
            if (player.hasPermission(permission)) {
                return section.getString(formatName);
            }
        }
        
        return null;
    }

    /**
     * 获取默认聊天格式（配置中的第一个格式）
     */
    private String getDefaultFormat() {
        if (!config.contains("Chat")) {
            return null;
        }
        
        ConfigurationSection section = config.getConfigurationSection("Chat");
        if (section == null) {
            return null;
        }
        
        Set<String> formats = section.getKeys(false);
        if (formats.isEmpty()) {
            return null;
        }
        
        // 返回第一个格式
        String firstFormat = formats.iterator().next();
        return section.getString(firstFormat);
    }

    /**
     * 处理格式字符串，替换占位符并应用颜色
     */
    private BaseComponent[] processFormatToComponent(String format, Player player, String message) {
        // 构建完整的消息
        String result = format;
        
        // 只使用玩家当前穿戴的称号（不穿戴则不显示）
        String title = getPlayerCurrentTitle(player);
        
        if (title != null) {
            // 处理称号中的颜色变量
            title = processTitleColors(title);
            result = result.replace("%title%", title);
        } else {
            // 玩家没有穿戴称号，不显示称号
            result = result.replace("%title%", "");
        }
        
        // 先替换颜色变量 %color1%, %color2% 等（在 %chat% 之前）
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            if (result.contains(entry.getKey())) {
                String gradientConfig = entry.getValue();
                String placeholder = entry.getKey();
                
                // 查找 %colorX%%chat% 并替换为渐变后的消息
                String pattern = placeholder + "%chat%";
                if (result.contains(pattern)) {
                    String gradientResult = applyGradient(gradientConfig, message);
                    result = result.replace(pattern, gradientResult);
                }
            }
        }
        
        // 最后替换 %chat%（处理没有颜色变量的情况）
        result = result.replace("%chat%", message);
        
        // 转换传统颜色代码 & -> §
        result = ChatColor.translateAlternateColorCodes('&', result);
        
        // 检查是否包含 %player% 且需要悬浮提示
        if (result.contains("%player%") && playerHoverLore != null && !playerHoverLore.isEmpty()) {
            return buildComponentWithHover(result, player);
        }
        
        // 不需要悬浮提示，直接替换 %player%
        result = result.replace("%player%", player.getName());
        return new BaseComponent[]{new TextComponent(result)};
    }

    /**
     * 构建带悬浮提示的组件
     */
    private BaseComponent[] buildComponentWithHover(String message, Player player) {
        // 分割消息，找到 %player% 的位置
        String[] parts = message.split("%player%", 2);
        
        if (parts.length == 0) {
            return new BaseComponent[]{new TextComponent(message)};
        }
        
        ComponentBuilder builder = new ComponentBuilder();
        
        // 添加前面的文本
        if (parts.length > 0 && !parts[0].isEmpty()) {
            builder.append(parts[0]);
        }
        
        // 创建玩家名称组件（带悬浮提示和点击事件）
        TextComponent playerComponent = new TextComponent(player.getName());
        
        // 设置悬浮提示
        BaseComponent[] hoverComponents = buildHoverComponents();
        if (hoverComponents != null && hoverComponents.length > 0) {
            playerComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents));
        }
        
        // 设置点击事件（预填命令或直接执行）
        // 优先使用 RunCommand（直接执行），其次使用 Command（预填）
        if (playerRunCommand != null && !playerRunCommand.isEmpty()) {
            // 直接执行命令
            String command = playerRunCommand.replace("%player%", player.getName());
            playerComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command));
        } else if (playerSuggestCommand != null && !playerSuggestCommand.isEmpty()) {
            // 预填命令
            String command = playerSuggestCommand.replace("%player%", player.getName());
            playerComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + command));
        }
        
        // 添加玩家名称
        builder.append(playerComponent);
        
        // 添加后面的文本
        if (parts.length > 1 && !parts[1].isEmpty()) {
            builder.append(parts[1]);
        }
        
        return builder.create();
    }

    /**
     * 获取称号配置
     */
    public Map<Integer, String> getPlayerTitles() {
        return playerTitles;
    }

    /**
     * 获取Gui配置
     */
    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    /**
     * 获取玩家当前穿戴的称号
     */
    public String getPlayerCurrentTitle(Player player) {
        return playerCurrentTitles.get(player.getUniqueId());
    }

    /**
     * 设置玩家当前穿戴的称号
     */
    public void setPlayerCurrentTitle(Player player, String title) {
        if (title == null) {
            playerCurrentTitles.remove(player.getUniqueId());
        } else {
            playerCurrentTitles.put(player.getUniqueId(), title);
        }
    }

    /**
     * 处理称号中的颜色变量
     */
    public String processTitleColors(String title) {
        String result = title;
        
        // 替换颜色变量
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            if (result.contains(entry.getKey())) {
                String gradientConfig = entry.getValue();
                String placeholder = entry.getKey();
                
                // 对称号文本应用渐变
                if (result.contains(placeholder)) {
                    // 简单替换：移除占位符，后续由 ChatColor.translateAlternateColorCodes 处理
                    result = result.replace(placeholder, "");
                    // 应用渐变到整个称号
                    result = applyGradient(gradientConfig, result);
                }
            }
        }
        
        return result;
    }

    /**
     * 应用渐变颜色到文本
     */
    private String applyGradient(String gradientConfig, String text) {
        // 解析渐变配置，格式: "#RRGGBB-#RRGGBB"
        String[] colors = gradientConfig.split("-");
        if (colors.length != 2) {
            return text;
        }
        
        String startColor = colors[0].trim();
        String endColor = colors[1].trim();
        
        // 移除 # 符号
        startColor = startColor.replace("#", "");
        endColor = endColor.replace("#", "");
        
        // 解析 RGB 值
        int startR = Integer.parseInt(startColor.substring(0, 2), 16);
        int startG = Integer.parseInt(startColor.substring(2, 4), 16);
        int startB = Integer.parseInt(startColor.substring(4, 6), 16);
        
        int endR = Integer.parseInt(endColor.substring(0, 2), 16);
        int endG = Integer.parseInt(endColor.substring(2, 4), 16);
        int endB = Integer.parseInt(endColor.substring(4, 6), 16);
        
        // 计算可见字符数量（跳过颜色代码）
        List<Integer> visibleCharIndices = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                // 跳过 § 格式的颜色代码
                i++;
                continue;
            }
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == 'x' || Character.isLetterOrDigit(next)) {
                    // 跳过 & 格式的颜色代码
                    i++;
                    continue;
                }
            }
            visibleCharIndices.add(i);
        }
        
        if (visibleCharIndices.isEmpty()) {
            return text;
        }
        
        // 构建渐变文本
        StringBuilder result = new StringBuilder();
        int visibleCount = visibleCharIndices.size();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // 处理 § 格式颜色代码
            if (c == '§' && i + 1 < text.length()) {
                result.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            
            // 处理 & 格式颜色代码
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == 'x' || Character.isLetterOrDigit(next)) {
                    result.append('§').append(next);
                    i++;
                    continue;
                }
            }
            
            // 计算当前可见字符的索引
            int visibleIndex = -1;
            for (int j = 0; j < visibleCharIndices.size(); j++) {
                if (visibleCharIndices.get(j) == i) {
                    visibleIndex = j;
                    break;
                }
            }
            
            if (visibleIndex >= 0) {
                // 计算渐变颜色
                float ratio = visibleCount > 1 ? (float) visibleIndex / (visibleCount - 1) : 0;
                int r = (int) (startR + (endR - startR) * ratio);
                int g = (int) (startG + (endG - startG) * ratio);
                int b = (int) (startB + (endB - startB) * ratio);
                
                // 生成 16 进制颜色代码 (§x§R§R§G§G§B§B)
                String hexColor = String.format("§x§%c§%c§%c§%c§%c§%c",
                    Character.forDigit((r >> 4) & 0xF, 16),
                    Character.forDigit(r & 0xF, 16),
                    Character.forDigit((g >> 4) & 0xF, 16),
                    Character.forDigit(g & 0xF, 16),
                    Character.forDigit((b >> 4) & 0xF, 16),
                    Character.forDigit(b & 0xF, 16));
                
                result.append(hexColor).append(c);
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }

    /**
     * 广播处理后的消息（支持 BaseComponent）
     */
    private void broadcastProcessedMessage(BaseComponent[] components) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(components);
        }
    }

    /**
     * 构建悬浮提示组件
     */
    private BaseComponent[] buildHoverComponents() {
        if (playerHoverLore == null || playerHoverLore.isEmpty()) {
            return null;
        }
        
        ComponentBuilder builder = new ComponentBuilder();
        
        for (int i = 0; i < playerHoverLore.size(); i++) {
            String line = playerHoverLore.get(i);
            String translated = ChatColor.translateAlternateColorCodes('&', line);
            
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(translated);
        }
        
        return builder.create();
    }

    /**
     * 广播原始消息（备用方法）
     */
    private void broadcastMessage(String playerName, String message) {
        String formatted = "&a" + playerName + ": &f" + message;
        String translated = ChatColor.translateAlternateColorCodes('&', formatted);
        // 将 String 转换为 BaseComponent[]
        BaseComponent[] components = new BaseComponent[]{new TextComponent(translated)};
        broadcastProcessedMessage(components);
    }

    /**
     * 处理命令
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("xlrchat")) {
            // 检查是否由玩家执行
            if (!(sender instanceof Player player)) {
                String noPlayerMsg = config.getString("Message.NoPlayer", "&c此命令只能由玩家执行!");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPlayerMsg));
                return true;
            }
            
            // 没有参数或参数为空时显示帮助
            if (args.length == 0) {
                sendHelpMessage(player);
                return true;
            }
            
            String subCommand = args[0].toLowerCase();
            
            // /xlrchat cp - 打开称号仓库
            if (subCommand.equals("cp")) {
                if (!player.hasPermission("xlr.command.cp")) {
                    String noPermMsg = config.getString("Message.NoPermission", "&c你没有权限执行此命令");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermMsg));
                    return true;
                }
                
                if (guiManager != null) {
                    guiManager.openTitleGUI(player);
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c称号系统未初始化！"));
                }
                return true;
            }
            
            // /xlrchat reload - 重载配置
            if (subCommand.equals("reload")) {
                if (!player.hasPermission("xlr.admin.reload")) {
                    String noPermMsg = config.getString("Message.NoPermission", "&c你没有权限执行此命令");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermMsg));
                    return true;
                }
                
                reloadConfig();
                
                // 发送重载消息（支持多行）
                List<String> reloadMessages = config.getStringList("Command.reload");
                if (reloadMessages.isEmpty()) {
                    // 默认消息
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7配置已重新加载"));
                } else {
                    for (String msg : reloadMessages) {
                        // 替换占位符
                        msg = msg.replace("%chat_format%", String.valueOf(colorVariables.size()));
                        msg = msg.replace("%color_config%", String.valueOf(playerTitles.size()));
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                    }
                }
                return true;
            }
            
            // /xlrchat help - 显示帮助
            if (subCommand.equals("help")) {
                sendHelpMessage(player);
                return true;
            }
            
            // 未知子命令
            String unknownMsg = config.getString("Message.UnknownSubCmd", "&c未知的子命令");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', unknownMsg));
            return true;
        }
        
        return false;
    }
    
    /**
     * 发送帮助信息
     */
    private void sendHelpMessage(Player player) {
        List<String> helpMessages = config.getStringList("Command.help");
        if (helpMessages.isEmpty()) {
            // 默认帮助信息
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6===== [XLRightweightChat] ====="));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7- /xlrchat cp &6打开称号仓库"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7- /xlrchat reload &6重载该插件"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7- /xlrchat help &6显示帮助"));
        } else {
            for (String msg : helpMessages) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            }
        }
    }
}