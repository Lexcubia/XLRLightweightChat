package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Shan extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private FileConfiguration guiConfig; // Gui.yml 配置
    private Map<String, String> colorVariables;
    private Map<Integer, String> playerTitles; // 称号配置：ID -> 名称
    private Map<Integer, List<String>> playerTitleLore; // 称号描述：ID -> Lore列表
    private final Map<UUID, String> playerCurrentTitles = new HashMap<>(); // 玩家当前穿戴的称号
    private GuiManager guiManager; // GUI 管理器
    private File playerDataFile; // 玩家数据文件
    private FileConfiguration playerData; // 玩家数据配置
    
    // 悬浮提示配置
    private List<String> playerHoverLore; // 玩家名称悬浮提示内容（Required.player）
    private List<String> playerSuggestCommands; // 点击后预填命令列表（Command - player）
    private List<String> playerRunCommands; // 点击后直接执行命令列表（RunCommand - player）
    
    // %chat% 悬浮提示配置
    private List<String> chatHoverLore; // 聊天消息悬浮提示内容（Required.chat）
    private List<String> chatSuggestCommands; // 点击后预填命令列表（Command - chat）
    private List<String> chatRunCommands; // 点击后直接执行命令列表（RunCommand - chat）
    
    // 物品展示配置
    private boolean displayItemEnabled = false; // 是否启用 [item] 占位符
    private String displayItemLanguage = "zh-cn"; // 物品显示语言：zh-cn（中文简体）或 en-us（英文）
    private boolean displayItemHover = true; // [item] 是否 SHOW_ITEM 悬浮
    private boolean displayItemInGradient = false; // [item] 是否参与 %chat% 渐变
    private final Item itemService = new Item();

    /** 聊天组件插入点（不参与 & 色码转换） */
    private static final String CHAT_PART_MARKER = "\uE000XLRCHAT\uE001";

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
        loadDisplayItemConfig();
        
        // 初始化悬浮配置列表
        if (playerHoverLore == null) playerHoverLore = new ArrayList<>();
        if (playerSuggestCommands == null) playerSuggestCommands = new ArrayList<>();
        if (playerRunCommands == null) playerRunCommands = new ArrayList<>();
        if (chatHoverLore == null) chatHoverLore = new ArrayList<>();
        if (chatSuggestCommands == null) chatSuggestCommands = new ArrayList<>();
        if (chatRunCommands == null) chatRunCommands = new ArrayList<>();
        
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

        playerCurrentTitles.clear();

        // 从文件加载玩家称号数据
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
        loadDisplayItemConfig();
        loadPlayerData();
    }

    /**
     * 获取配置中的聊天格式数量
     */
    private int getChatFormatCount() {
        if (!config.contains("Chat")) {
            return 0;
        }
        ConfigurationSection section = config.getConfigurationSection("Chat");
        return section != null ? section.getKeys(false).size() : 0;
    }

    /**
     * 加载 DisplayItem 配置
     */
    private void loadDisplayItemConfig() {
        displayItemEnabled = config.getBoolean("Displayitem", false);
        displayItemLanguage = config.getString("DiaplayLanguage", "zh-cn").toLowerCase();
        displayItemHover = config.getBoolean("DisplayitemHover", true);
        displayItemInGradient = config.getBoolean("DisplayitemInGradient", false);

        if (!displayItemLanguage.equals("zh-cn") && !displayItemLanguage.equals("en-us")) {
            getLogger().warning("[警告] DiaplayLanguage 配置无效: " + displayItemLanguage);
            getLogger().warning("[提示] 有效值: zh-cn（中文简体）, en-us（英文）");
            displayItemLanguage = "zh-cn";
        }
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
        playerTitleLore = new TreeMap<>(); // 称号描述
        
        if (config.contains("PlayerTitle")) {
            ConfigurationSection section = config.getConfigurationSection("PlayerTitle");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        
                        // 新格式：包含 name 和 Lore
                        if (section.isConfigurationSection(key)) {
                            ConfigurationSection titleSection = section.getConfigurationSection(key);
                            if (titleSection != null) {
                                String name = titleSection.getString("name");
                                if (name != null) {
                                    // 转换传统颜色代码 & -> §，确保后续比较一致性
                                    String processedName = ChatColor.translateAlternateColorCodes('&', name);
                                    playerTitles.put(id, processedName);
                                }
                                
                                List<String> lore = titleSection.getStringList("Lore");
                                if (!lore.isEmpty()) {
                                    // 转换 Lore 中的传统颜色代码
                                    List<String> processedLore = new ArrayList<>();
                                    for (String loreLine : lore) {
                                        processedLore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
                                    }
                                    playerTitleLore.put(id, processedLore);
                                }
                            }
                        } 
                        // 旧格式：直接字符串（向后兼容）
                        else {
                            String prefix = section.getString(key);
                            if (prefix != null) {
                                playerTitles.put(id, prefix);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 忽略非数字 ID
                    }
                }
            }
        }
        
        // 称号配置加载完成
    }

    /**
     * 加载 Required 配置（player 和 chat 的悬浮提示和点击事件）
     */
    private void loadPlayerHoverConfig() {
        // 初始化列表
        playerHoverLore = new ArrayList<>();
        playerSuggestCommands = new ArrayList<>();
        playerRunCommands = new ArrayList<>();
        chatHoverLore = new ArrayList<>();
        chatSuggestCommands = new ArrayList<>();
        chatRunCommands = new ArrayList<>();
        
        if (!config.contains("Required")) {
            // 未找到 Required 配置
            return;
        }
        
        ConfigurationSection requiredSection = config.getConfigurationSection("Required");
        if (requiredSection == null) {
            // Required 不是配置节
            return;
        }
        
        // 加载 player 配置
        if (requiredSection.contains("player")) {
            List<?> playerList = requiredSection.getList("player");
            if (playerList != null) {
                extractCommandsAndLore(playerList, playerHoverLore, playerSuggestCommands, playerRunCommands, "player");
            }
        }
        
        // 加载 chat 配置
        if (requiredSection.contains("chat")) {
            List<?> chatList = requiredSection.getList("chat");
            if (chatList != null) {
                extractCommandsAndLore(chatList, chatHoverLore, chatSuggestCommands, chatRunCommands, "chat");
            }
        }
        
        // Required 配置加载完成
    }
    
    /**
     * 从列表中提取悬浮文本和命令
     */
    private void extractCommandsAndLore(List<?> list, List<String> hoverLore, 
                                        List<String> suggestCommands, List<String> runCommands,
                                        String configName) {
        for (Object item : list) {
            // 处理 Map 格式（YAML 会将 "- Command: xxx" 解析为 Map）
            if (item instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    String value = String.valueOf(entry.getValue());
                    
                    if (key.equalsIgnoreCase("Command")) {
                        if (!value.isEmpty()) {
                            suggestCommands.add(value);
                            // 添加 Command
                        }
                    } else if (key.equalsIgnoreCase("RunCommand")) {
                        if (!value.isEmpty()) {
                            runCommands.add(value);
                            // 添加 RunCommand
                        }
                    }
                }
            } else {
                // 普通字符串
                String line = String.valueOf(item);
                
                if (line.startsWith("Command:")) {
                    String command = line.substring(8).trim();
                    if (!command.isEmpty()) {
                        suggestCommands.add(command);
                        // 添加 Command
                    }
                } else if (line.startsWith("RunCommand:")) {
                    String command = line.substring(11).trim();
                    if (!command.isEmpty()) {
                        runCommands.add(command);
                        // 添加 RunCommand
                    }
                } else {
                    hoverLore.add(line);
                    // 添加悬浮文本
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
            CraftChatBridge.broadcastOnMainThread(this, () -> broadcastMessage(player.getName(), message));
            return;
        }

        final String chatFormat = format;
        CraftChatBridge.broadcastOnMainThread(this, () -> {
            try {
                if (!broadcastItemChatIfPossible(chatFormat, player, message)) {
                    BaseComponent[] components = processFormatToComponent(chatFormat, player, message);
                    CraftChatBridge.broadcast(components);
                }
            } catch (Throwable t) {
                getLogger().severe("[XLRLightweightChat] 聊天发送失败: " + t.getMessage());
                t.printStackTrace();
            }
        });
    }

    /**
     * 含 [item] 时在主线程用 JSON+NMS 发送（1.21 components）；成功返回 true。
     */
    private boolean broadcastItemChatIfPossible(String format, Player player, String message) {
        if (!displayItemEnabled || !displayItemHover || !message.contains("[item]")) {
            return false;
        }
        boolean needPlayerHover = format.contains("%player%")
                && playerHoverLore != null && !playerHoverLore.isEmpty();
        boolean needTitleHover = false;
        String title = getPlayerCurrentTitle(player);
        if (title != null) {
            for (Map.Entry<Integer, List<String>> entry : playerTitleLore.entrySet()) {
                String processedPrefix = processTitleColors(playerTitles.get(entry.getKey()));
                if (processedPrefix.equals(processTitleColors(title))) {
                    List<String> lore = entry.getValue();
                    needTitleHover = lore != null && !lore.isEmpty();
                    break;
                }
            }
        }
        if (needPlayerHover || needTitleHover) {
            getLogger().info("[XLRLightweightChat] 格式含玩家/称号悬浮时暂用 Bungee 发送，[item] 物品悬浮可能不完整");
            return false;
        }
        JsonArray extra = buildFullChatJsonExtra(format, player, message);
        if (extra == null || extra.isEmpty()) {
            return false;
        }
        if (CraftChatBridge.broadcastWithExtra(extra)) {
            return true;
        }
        getLogger().warning("[XLRLightweightChat] JSON 物品聊天发送失败，已退回 Bungee 路径");
        return false;
    }

    private JsonArray buildFullChatJsonExtra(String format, Player player, String message) {
        String result = format;
        String title = getPlayerCurrentTitle(player);
        if (title != null) {
            result = result.replace("%title%", processTitleColors(title));
        } else {
            result = result.replace("%title%", "");
        }
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            String pattern = entry.getKey() + "%player%";
            if (result.contains(pattern)) {
                result = result.replace(pattern, "%player%");
            }
        }
        String chatGradientConfig = null;
        String chatGradientPattern = extractChatGradientPattern(result);
        if (chatGradientPattern != null) {
            String varKey = chatGradientPattern.replace("%chat%", "");
            chatGradientConfig = colorVariables.get(varKey);
            result = result.replace(chatGradientPattern, CHAT_PART_MARKER);
        } else if (result.contains("%chat%")) {
            result = result.replace("%chat%", CHAT_PART_MARKER);
        }
        List<ChatMessagePart> messageParts = buildMessageParts(player, message);
        JsonArray chatExtra = buildChatJsonParts(player, messageParts, chatGradientConfig);
        return assembleFormatJsonExtra(result, player, chatExtra);
    }

    private JsonArray buildChatJsonParts(Player player, List<ChatMessagePart> parts, String gradientConfig) {
        JsonArray extra = new JsonArray();
        for (ChatMessagePart part : parts) {
            if (part instanceof ChatMessagePart.Text textPart) {
                String text = textPart.content();
                if (gradientConfig != null) {
                    text = applyGradient(gradientConfig, text);
                }
                text = ChatColor.translateAlternateColorCodes('&', text);
                if (!text.isEmpty()) {
                    extra.add(CraftChatBridge.textNode(text));
                }
            } else if (part instanceof ChatMessagePart.Item itemPart) {
                String content = itemPart.segment().displayText();
                if (displayItemInGradient && gradientConfig != null) {
                    content = applyGradient(gradientConfig, content);
                }
                content = ChatColor.translateAlternateColorCodes('&', content);
                extra.add(CraftChatBridge.itemTextNode(content, itemPart.segment().snapshot()));
            }
        }
        return extra;
    }

    private JsonArray assembleFormatJsonExtra(String formatResult, Player player, JsonArray chatExtra) {
        JsonArray extra = new JsonArray();
        String[] sections = formatResult.split(CHAT_PART_MARKER, -1);
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i];
            if (!section.isEmpty()) {
                section = section.replace("%player%", player.getName());
                section = applyColorVariables(section);
                section = ChatColor.translateAlternateColorCodes('&', section);
                extra.add(CraftChatBridge.textNode(section));
            }
            if (i < sections.length - 1 && chatExtra != null) {
                for (JsonElement element : chatExtra) {
                    extra.add(element);
                }
            }
        }
        return extra;
    }

    private String applyColorVariables(String text) {
        String result = text;
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
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

    public String getItemDisplayName(org.bukkit.Material material) {
        return itemService.getDisplayName(material, displayItemLanguage);
    }

    public Item getItemService() {
        return itemService;
    }

    private List<ChatMessagePart> buildMessageParts(Player player, String message) {
        if (!message.contains("[item]")) {
            return List.of(new ChatMessagePart.Text(message));
        }
        if (!displayItemEnabled) {
            getLogger().warning("[物品展示] 检测到 [item] 但功能未启用！请检查 config.yml 中 Displayitem: true");
            return List.of(new ChatMessagePart.Text(message));
        }
        List<ChatMessagePart> parts = new ArrayList<>();
        final String token = "[item]";
        int idx = 0;
        while (true) {
            int pos = message.indexOf(token, idx);
            if (pos < 0) {
                if (idx < message.length()) {
                    parts.add(new ChatMessagePart.Text(message.substring(idx)));
                }
                break;
            }
            if (pos > idx) {
                parts.add(new ChatMessagePart.Text(message.substring(idx, pos)));
            }
            ItemDisplaySegment segment = itemService.buildDisplaySegment(player, displayItemLanguage);
            if (segment != null) {
                parts.add(new ChatMessagePart.Item(segment));
            }
            idx = pos + token.length();
        }
        if (parts.isEmpty()) {
            parts.add(new ChatMessagePart.Text(message));
        }
        return parts;
    }

    private BaseComponent[] buildChatComponentsFromParts(Player player, List<ChatMessagePart> parts,
                                                         String gradientConfig) {
        ComponentBuilder builder = new ComponentBuilder();
        boolean applyChatHover = chatHoverLore != null && !chatHoverLore.isEmpty();
        BaseComponent[] chatHoverComponents = applyChatHover ? buildHoverComponents(chatHoverLore) : null;
        ClickEvent chatClick = resolveChatClickEvent(player);

        for (ChatMessagePart part : parts) {
            if (part instanceof ChatMessagePart.Text textPart) {
                appendTextPart(builder, textPart.content(), gradientConfig, true,
                        chatHoverComponents, chatClick);
            } else if (part instanceof ChatMessagePart.Item itemPart) {
                String content = itemPart.segment().displayText();
                if (displayItemInGradient && gradientConfig != null) {
                    content = applyGradient(gradientConfig, content);
                }
                content = ChatColor.translateAlternateColorCodes('&', content);
                ItemStack snapshot = itemPart.segment().snapshot();
                HoverEvent itemHover = null;
                if (displayItemHover) {
                    try {
                        itemHover = itemService.createItemHoverEvent(snapshot);
                    } catch (Throwable t) {
                        getLogger().log(java.util.logging.Level.WARNING,
                                "[XLRLightweightChat] 物品悬浮构建失败", t);
                    }
                }
                for (BaseComponent component : parseLegacyTextWithHexColors(content)) {
                    if (component instanceof TextComponent textComp && itemHover != null) {
                        textComp.setHoverEvent(itemHover);
                    }
                    builder.append(component);
                }
            }
        }
        return builder.create();
    }

    private void appendTextPart(ComponentBuilder builder, String content, String gradientConfig,
                                boolean applyGradientToPart, BaseComponent[] chatHover,
                                ClickEvent chatClick) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String text = content;
        if (applyGradientToPart && gradientConfig != null) {
            text = applyGradient(gradientConfig, text);
        }
        text = ChatColor.translateAlternateColorCodes('&', text);
        for (BaseComponent component : parseLegacyTextWithHexColors(text)) {
            if (component instanceof TextComponent textComp) {
                if (chatHover != null && chatHover.length > 0) {
                    textComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, chatHover));
                }
                if (chatClick != null) {
                    textComp.setClickEvent(chatClick);
                }
            }
            builder.append(component);
        }
    }

    private ClickEvent resolveChatClickEvent(Player player) {
        if (!chatSuggestCommands.isEmpty() && !chatRunCommands.isEmpty()) {
            return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, joinCommands(player, chatSuggestCommands));
        }
        if (!chatSuggestCommands.isEmpty()) {
            return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, joinCommands(player, chatSuggestCommands));
        }
        if (!chatRunCommands.isEmpty()) {
            return new ClickEvent(ClickEvent.Action.RUN_COMMAND, joinCommands(player, chatRunCommands));
        }
        return null;
    }

    private String joinCommands(Player player, List<String> commands) {
        StringBuilder commandBuilder = new StringBuilder();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i).replace("%player%", player.getName());
            if (!command.startsWith("/")) {
                command = "/" + command;
            }
            commandBuilder.append(command);
            if (i < commands.size() - 1) {
                commandBuilder.append("; ");
            }
        }
        return commandBuilder.toString();
    }

    private String extractChatGradientPattern(String formatResult) {
        for (String placeholder : colorVariables.keySet()) {
            String pattern = placeholder + "%chat%";
            if (formatResult.contains(pattern)) {
                return pattern;
            }
        }
        return null;
    }

    private BaseComponent[] assembleFormatWithChat(String formatResult, Player player,
                                                   BaseComponent[] chatComponents) {
        String[] sections = formatResult.split(CHAT_PART_MARKER, -1);
        ComponentBuilder builder = new ComponentBuilder();

        for (int i = 0; i < sections.length; i++) {
            String section = sections[i];
            if (!section.isEmpty()) {
                section = section.replace("%player%", player.getName());
                for (BaseComponent component : parseLegacyTextWithHexColors(section)) {
                    builder.append(component);
                }
            }
            if (i < sections.length - 1 && chatComponents != null) {
                for (BaseComponent chatComponent : chatComponents) {
                    builder.append(chatComponent);
                }
            }
        }
        return builder.create();
    }

    /**
     * 处理格式字符串，替换占位符并应用颜色
     */
    private BaseComponent[] processFormatToComponent(String format, Player player, String message) {
        // 构建完整的消息
        String result = format;
        
        // 只使用玩家当前穿戴的称号（不穿戴则不显示）
        String title = getPlayerCurrentTitle(player);
        
        // 检查是否需要称号悬浮提示
        boolean needTitleHover = false;
        int titleId = -1;
        
        if (title != null) {
            // 处理称号中的颜色变量
            title = processTitleColors(title);
            
            // 查找称号 ID
            for (Map.Entry<Integer, String> entry : playerTitles.entrySet()) {
                String titlePrefix = entry.getValue();
                // 处理称号中的颜色变量后再比较
                String processedPrefix = processTitleColors(titlePrefix);
                if (processedPrefix.equals(title)) {
                    titleId = entry.getKey();
                    // 检查该称号是否有 Lore
                    List<String> lore = playerTitleLore.get(titleId);
                    if (lore != null && !lore.isEmpty()) {
                        needTitleHover = true;
                    }
                    break;
                }
            }
            
            result = result.replace("%title%", title);
        } else {
            // 玩家没有穿戴称号，不显示称号
            result = result.replace("%title%", "");
        }
        
        // 检查是否有颜色变量应用到 %player% 上
        String playerColorGradient = null;
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            String placeholder = entry.getKey();
            String pattern = placeholder + "%player%";
            if (result.contains(pattern)) {
                playerColorGradient = entry.getValue();
                // 先移除 %colorX%%player% 组合，后续单独处理
                result = result.replace(pattern, "%player%");
                break;
            }
        }
        
        boolean needHover = result.contains("%player%") && playerHoverLore != null && !playerHoverLore.isEmpty();

        List<ChatMessagePart> messageParts = buildMessageParts(player, message);
        String chatGradientConfig = null;
        String chatGradientPattern = extractChatGradientPattern(result);
        if (chatGradientPattern != null) {
            String varKey = chatGradientPattern.replace("%chat%", "");
            chatGradientConfig = colorVariables.get(varKey);
            result = result.replace(chatGradientPattern, CHAT_PART_MARKER);
        } else if (result.contains("%chat%")) {
            result = result.replace("%chat%", CHAT_PART_MARKER);
        }

        BaseComponent[] chatComponents = buildChatComponentsFromParts(player, messageParts, chatGradientConfig);

        net.md_5.bungee.api.ChatColor playerColor = needHover ? extractLastColorCode(result) : null;
        result = ChatColor.translateAlternateColorCodes('&', result);

        if (needHover || needTitleHover) {
            return buildComponentWithHover(result, player, playerColor, playerColorGradient, title, titleId,
                    needTitleHover, chatComponents);
        }

        return assembleFormatWithChat(result, player, chatComponents);
    }

    /**
     * 从文本中提取最后一个传统颜色代码（& 格式）
     * 注意：不会提取 16 进制颜色代码（&x&R&R&G&G&B&B）中的部分
     * @param text 包含颜色代码的文本（& 格式，尚未转换为 §）
     * @return 最后一个传统颜色代码，如果没有则返回 null
     */
    private net.md_5.bungee.api.ChatColor extractLastColorCode(String text) {
        // 先移除所有 16 进制颜色代码（&x 开头，后面跟 12 个 &+字符）
        String cleanedText = text.replaceAll("&x(&[0-9a-fA-F]){6}", "");
        
        // 从后往前查找最后一个 & 颜色代码
        for (int i = cleanedText.length() - 1; i >= 0; i--) {
            char c = cleanedText.charAt(i);
            
            // 找到 & 符号
            if (c == '&' && i + 1 < cleanedText.length()) {
                char next = cleanedText.charAt(i + 1);
                // 检查是否是有效的颜色代码字符
                if (Character.isLetterOrDigit(next)) {
                    return net.md_5.bungee.api.ChatColor.getByChar(next);
                }
            }
        }
        
        return null;
    }

    /**
     * 构建带悬浮提示的组件
     * @param message 已转换颜色的消息（§ 格式）
     * @param player 玩家对象
     * @param playerColor 在转换前提取的玩家名称颜色代码（传统颜色）
     * @param playerColorGradient 玩家名称的渐变颜色配置（16进制颜色）
     * @param title 称号文本（可为 null）
     * @param titleId 称号 ID（-1 表示无称号）
     * @param needTitleHover 是否需要称号悬浮提示
     */
    private BaseComponent[] buildComponentWithHover(String message, Player player, 
                                                     net.md_5.bungee.api.ChatColor playerColor,
                                                     String playerColorGradient,
                                                     String title,
                                                     int titleId,
                                                     boolean needTitleHover,
                                                     BaseComponent[] chatComponents) {
        // 在替换 %player% 之前，先分割消息
        // 使用 split 并限制为 2，确保只分割第一个 %player%
        int playerIndex = message.indexOf("%player%");
        
        if (playerIndex == -1) {
            if (message.contains(CHAT_PART_MARKER) && chatComponents != null) {
                return assembleFormatWithChat(message, player, chatComponents);
            }
            return new BaseComponent[]{new TextComponent(message)};
        }
        
        String beforePlayer = message.substring(0, playerIndex);
        String afterPlayer = message.substring(playerIndex + 8); // 8 是 "%player%" 的长度
        
        ComponentBuilder builder = new ComponentBuilder();
        
        // 处理称号悬浮提示（在 %player% 之前）
        if (needTitleHover && titleId > 0 && title != null) {
            // 在 beforePlayer 中查找称号的位置
            int titleIndex = beforePlayer.indexOf(title);
            if (titleIndex != -1) {
                // 添加称号前面的文本
                String beforeTitle = beforePlayer.substring(0, titleIndex);
                if (!beforeTitle.isEmpty()) {
                    BaseComponent[] beforeComponents = parseLegacyTextWithHexColors(beforeTitle);
                    for (BaseComponent component : beforeComponents) {
                        builder.append(component);
                    }
                }
                
                // 创建称号组件（带悬浮提示）
                // 称号文本已包含渐变颜色代码，需要正确解析
                BaseComponent[] titleComponents = parseLegacyTextWithHexColors(title);
                
                // 获取第一个组件作为主组件
                TextComponent titleComponent;
                if (titleComponents.length > 0 && titleComponents[0] instanceof TextComponent) {
                    titleComponent = (TextComponent) titleComponents[0];
                } else {
                    titleComponent = new TextComponent(title);
                }
                
                // 如果有多个组件，将剩余的附加到第一个组件的 extra 中
                if (titleComponents.length > 1) {
                    for (int i = 1; i < titleComponents.length; i++) {
                        titleComponent.addExtra(titleComponents[i]);
                    }
                }
                
                // 获取称号 Lore 并设置为悬浮提示
                List<String> titleLore = playerTitleLore.get(titleId);
                if (titleLore != null && !titleLore.isEmpty()) {
                    BaseComponent[] titleHoverComponents = buildHoverComponents(titleLore);
                    if (titleHoverComponents != null && titleHoverComponents.length > 0) {
                        titleComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, titleHoverComponents));
                    }
                }
                
                // 添加称号组件
                builder.append(titleComponent);
                
                // 更新 beforePlayer，移除已处理的部分（称号 + 称号前的文本）
                beforePlayer = beforePlayer.substring(titleIndex + title.length());
            }
        }
        
        // 添加 %player% 前面的文本（需要正确解析 16 进制颜色代码）
        if (!beforePlayer.isEmpty()) {
            // 将包含 § 格式的字符串转换为 BaseComponent
            BaseComponent[] frontComponents = parseLegacyTextWithHexColors(beforePlayer);
            for (BaseComponent component : frontComponents) {
                builder.append(component);
            }
        }
        
        // 创建玩家名称组件（带悬浮提示和点击事件）
        TextComponent playerComponent;
        
        // 优先使用渐变颜色，如果没有则使用传统颜色
        if (playerColorGradient != null) {
            // 应用渐变颜色到玩家名称（返回 String）
            String gradientText = applyGradient(playerColorGradient, player.getName());
            
            // 将包含渐变颜色的 String 转换为 BaseComponent 数组
            BaseComponent[] gradientComponents = parseLegacyTextWithHexColors(gradientText);
            
            // 获取第一个组件作为主组件
            if (gradientComponents.length > 0 && gradientComponents[0] instanceof TextComponent) {
                playerComponent = (TextComponent) gradientComponents[0];
                
                // 如果有多个组件，将剩余的附加到第一个组件的 extra 中
                if (gradientComponents.length > 1) {
                    for (int i = 1; i < gradientComponents.length; i++) {
                        playerComponent.addExtra(gradientComponents[i]);
                    }
                }
            } else {
                playerComponent = new TextComponent(player.getName());
            }
        } else {
            playerComponent = new TextComponent(player.getName());
            
            // 应用提取到的颜色（在转换前提取的 &a 颜色代码）
            if (playerColor != null) {
                playerComponent.setColor(playerColor);
                // 应用传统颜色到玩家名称
            }
        }
        
        // 设置悬浮提示
        BaseComponent[] hoverComponents = buildHoverComponents(playerHoverLore);
        if (hoverComponents != null && hoverComponents.length > 0) {
            playerComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents));
        }
        
        // 设置点击事件（只支持一种类型：Command 或 RunCommand）
        // 优先使用 Command（预填），如果没有 Command 才使用 RunCommand（执行）
        if (!playerSuggestCommands.isEmpty() && !playerRunCommands.isEmpty()) {
            // 如果同时配置了两种，输出警告并使用 Command
            getLogger().warning("[警告] 同时配置了 Command 和 RunCommand，只使用 Command（预填）");
            getLogger().warning("[提示] 请只保留其中一种配置");
            
            // 使用 SUGGEST_COMMAND（预填命令到聊天栏）
            StringBuilder commandBuilder = new StringBuilder();
            for (int i = 0; i < playerSuggestCommands.size(); i++) {
                String command = playerSuggestCommands.get(i).replace("%player%", player.getName());
                if (!command.startsWith("/")) {
                    command = "/" + command;
                }
                commandBuilder.append(command);
                if (i < playerSuggestCommands.size() - 1) {
                    commandBuilder.append("; ");
                }
            }
            String finalCommand = commandBuilder.toString();
            playerComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, finalCommand));
        } else if (!playerSuggestCommands.isEmpty()) {
            // 只有 Command，使用 SUGGEST_COMMAND（预填命令到聊天栏）
            StringBuilder commandBuilder = new StringBuilder();
            for (int i = 0; i < playerSuggestCommands.size(); i++) {
                String command = playerSuggestCommands.get(i).replace("%player%", player.getName());
                if (!command.startsWith("/")) {
                    command = "/" + command;
                }
                commandBuilder.append(command);
                if (i < playerSuggestCommands.size() - 1) {
                    commandBuilder.append("; ");
                }
            }
            String finalCommand = commandBuilder.toString();
            playerComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, finalCommand));
        } else if (!playerRunCommands.isEmpty()) {
            // 只有 RunCommand，使用 RUN_COMMAND（直接执行命令）
            StringBuilder commandBuilder = new StringBuilder();
            for (int i = 0; i < playerRunCommands.size(); i++) {
                String command = playerRunCommands.get(i).replace("%player%", player.getName());
                if (!command.startsWith("/")) {
                    command = "/" + command;
                }
                commandBuilder.append(command);
                if (i < playerRunCommands.size() - 1) {
                    commandBuilder.append("; ");
                }
            }
            String finalCommand = commandBuilder.toString();
            playerComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, finalCommand));
        }
        
        // 添加玩家名称
        builder.append(playerComponent);
        
        if (!afterPlayer.isEmpty()) {
            appendAfterPlayerSection(builder, afterPlayer, player, chatComponents);
        }

        return builder.create();
    }

    private void appendAfterPlayerSection(ComponentBuilder builder, String afterPlayer, Player player,
                                          BaseComponent[] chatComponents) {
        int markerIndex = afterPlayer.indexOf(CHAT_PART_MARKER);
        if (markerIndex >= 0 && chatComponents != null) {
            String beforeChat = afterPlayer.substring(0, markerIndex);
            String afterChat = afterPlayer.substring(markerIndex + CHAT_PART_MARKER.length());
            appendPlainSectionWithChatHover(builder, beforeChat, player);
            for (BaseComponent chatComponent : chatComponents) {
                builder.append(chatComponent);
            }
            appendPlainSectionWithChatHover(builder, afterChat, player);
            return;
        }
        appendPlainSectionWithChatHover(builder, afterPlayer, player);
    }

    private void appendPlainSectionWithChatHover(ComponentBuilder builder, String section, Player player) {
        if (section == null || section.isEmpty()) {
            return;
        }
        BaseComponent[] chatHoverComponents = (chatHoverLore != null && !chatHoverLore.isEmpty())
                ? buildHoverComponents(chatHoverLore) : null;
        ClickEvent chatClick = resolveChatClickEvent(player);
        if (!chatSuggestCommands.isEmpty() && !chatRunCommands.isEmpty()) {
            getLogger().warning("[警告] chat 同时配置了 Command 和 RunCommand，只使用 Command（预填）");
        }
        for (BaseComponent component : parseLegacyTextWithHexColors(section)) {
            if (component instanceof TextComponent textComp) {
                textComp.setHoverEvent(null);
                textComp.setClickEvent(null);
                if (chatHoverComponents != null && chatHoverComponents.length > 0) {
                    textComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, chatHoverComponents));
                }
                if (chatClick != null) {
                    textComp.setClickEvent(chatClick);
                }
            }
            builder.append(component);
        }
    }

    /**
     * 获取称号配置
     */
    public Map<Integer, String> getPlayerTitles() {
        return playerTitles;
    }
    
    /**
     * 获取称号描述配置
     */
    public Map<Integer, List<String>> getPlayerTitleLore() {
        return playerTitleLore;
    }
    
    /**
     * 获取颜色变量配置（公共方法，供GuiManager使用）
     */
    public Map<String, String> getColorVariables() {
        return colorVariables;
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
     * 注意：称号名称和 Lore 已经在加载时转换为 § 格式，这里只处理颜色变量
     */
    public String processTitleColors(String title) {
        String result = title;
        
        // 替换颜色变量
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            if (result.contains(entry.getKey())) {
                String gradientConfig = entry.getValue();
                String placeholder = entry.getKey();
                
                // 提取占位符后面的称号文本
                int placeholderIndex = result.indexOf(placeholder);
                if (placeholderIndex != -1) {
                    String afterPlaceholder = result.substring(placeholderIndex + placeholder.length());
                    String beforePlaceholder = result.substring(0, placeholderIndex);
                    
                    // 对称号文本应用渐变
                    String gradientText = applyGradient(gradientConfig, afterPlaceholder);
                    
                    // 重新组合：前置文本 + 渐变后的称号
                    result = beforePlaceholder + gradientText;
                }
            }
        }
        
        return result;
    }

    /**
     * 应用渐变颜色到文本
     */
    /**
     * 应用渐变颜色到文本（支持任意数量的颜色）
     * @param gradientConfig 颜色配置，格式: "#RRGGBB-#RRGGBB-#RRGGBB..."
     * @param text 要应用渐变的文本
     * @return 应用渐变后的文本
     */
    private String applyGradient(String gradientConfig, String text) {
        // 解析渐变配置，格式: "#RRGGBB-#RRGGBB" 或 "#RRGGBB-#RRGGBB-#RRGGBB"
        String[] colorConfigs = gradientConfig.split("-");
        if (colorConfigs.length < 2) {
            return text; // 至少需要两个颜色
        }
        
        // 解析所有颜色的 RGB 值
        int[][] colors = new int[colorConfigs.length][3]; // [colorIndex][R, G, B]
        for (int i = 0; i < colorConfigs.length; i++) {
            String color = colorConfigs[i].trim().replace("#", "");
            if (color.length() != 6) {
                return text; // 颜色格式错误
            }
            try {
                colors[i][0] = Integer.parseInt(color.substring(0, 2), 16); // R
                colors[i][1] = Integer.parseInt(color.substring(2, 4), 16); // G
                colors[i][2] = Integer.parseInt(color.substring(4, 6), 16); // B
            } catch (NumberFormatException e) {
                return text; // 解析失败
            }
        }
        
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
        int colorSegmentCount = colors.length - 1; // 颜色段数量
        
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
                // 计算当前字符应该使用哪个颜色段
                float overallRatio = visibleCount > 1 ? (float) visibleIndex / (visibleCount - 1) : 0;
                int colorSegmentIndex = (int) (overallRatio * colorSegmentCount);
                
                // 确保不越界
                if (colorSegmentIndex >= colorSegmentCount) {
                    colorSegmentIndex = colorSegmentCount - 1;
                }
                
                // 在当前颜色段内计算比例
                float segmentRatio = (overallRatio * colorSegmentCount) - colorSegmentIndex;
                
                // 计算渐变颜色
                int r = (int) (colors[colorSegmentIndex][0] + 
                              (colors[colorSegmentIndex + 1][0] - colors[colorSegmentIndex][0]) * segmentRatio);
                int g = (int) (colors[colorSegmentIndex][1] + 
                              (colors[colorSegmentIndex + 1][1] - colors[colorSegmentIndex][1]) * segmentRatio);
                int b = (int) (colors[colorSegmentIndex][2] + 
                              (colors[colorSegmentIndex + 1][2] - colors[colorSegmentIndex][2]) * segmentRatio);
                
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
     * 应用渐变颜色到文本（公共方法，供GuiManager使用）
     * @param gradientConfig 颜色配置，格式: "#RRGGBB-#RRGGBB-#RRGGBB..."
     * @param text 要应用渐变的文本
     * @return 应用渐变后的文本
     */
    public String applyGradientForGui(String gradientConfig, String text) {
        return applyGradient(gradientConfig, text);
    }

    /**
     * 广播处理后的消息（支持 BaseComponent）
     */
    private void broadcastProcessedMessage(BaseComponent[] components) {
        CraftChatBridge.broadcast(components);
    }

    /**
     * 构建悬浮提示组件
     */
    /**
     * 构建悬浮提示组件
     * @param hoverLore 悬浮文本列表
     * @return BaseComponent 数组
     */
    private BaseComponent[] buildHoverComponents(List<String> hoverLore) {
        if (hoverLore == null || hoverLore.isEmpty()) {
            return null;
        }
        
        ComponentBuilder builder = new ComponentBuilder();
        
        for (int i = 0; i < hoverLore.size(); i++) {
            String line = hoverLore.get(i);
            
            // 先处理颜色变量（如 %color4%）
            String processedLine = line;
            for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
                if (processedLine.contains(entry.getKey())) {
                    String gradientConfig = entry.getValue();
                    String placeholder = entry.getKey();
                    
                    // 提取占位符后面的文本
                    int placeholderIndex = processedLine.indexOf(placeholder);
                    if (placeholderIndex != -1) {
                        String afterPlaceholder = processedLine.substring(placeholderIndex + placeholder.length());
                        String beforePlaceholder = processedLine.substring(0, placeholderIndex);
                        
                        // 对文本应用渐变
                        String gradientText = applyGradient(gradientConfig, afterPlaceholder);
                        
                        // 重新组合
                        processedLine = beforePlaceholder + gradientText;
                    }
                }
            }
            
            // 转换传统颜色代码
            String translated = ChatColor.translateAlternateColorCodes('&', processedLine);
            
            if (i > 0) {
                builder.append("\n");
            }
            
            // 关键修改：使用 parseLegacyTextWithHexColors 解析 16 进制颜色代码
            BaseComponent[] lineComponents = parseLegacyTextWithHexColors(translated);
            for (BaseComponent component : lineComponents) {
                builder.append(component);
            }
        }
        
        return builder.create();
    }

    /**
     * 解析包含 16 进制颜色代码的文本为 BaseComponent 数组
     * 支持 §x§R§R§G§G§B§B 格式的 16 进制颜色和传统颜色代码
     */
    private BaseComponent[] parseLegacyTextWithHexColors(String text) {
        List<BaseComponent> components = new ArrayList<>();
        TextComponent currentComponent = new TextComponent("");
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // 处理 § 格式
            if (c == '§' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                
                // 检查是否是 16 进制颜色代码 §x§R§R§G§G§B§B
                if (next == 'x' && i + 13 < text.length()) {
                    // 解析 16 进制颜色
                    try {
                        int r = (Character.digit(text.charAt(i + 3), 16) << 4) | Character.digit(text.charAt(i + 5), 16);
                        int g = (Character.digit(text.charAt(i + 7), 16) << 4) | Character.digit(text.charAt(i + 9), 16);
                        int b = (Character.digit(text.charAt(i + 11), 16) << 4) | Character.digit(text.charAt(i + 13), 16);
                        
                        // 添加当前组件
                        if (!currentComponent.getText().isEmpty()) {
                            components.add(currentComponent);
                        }
                        
                        // 创建新组件并设置 16 进制颜色
                        currentComponent = new TextComponent("");
                        // 使用 BungeeCord ChatColor 的 of 方法设置 16 进制颜色
                        String hexColor = String.format("#%02x%02x%02x", r, g, b);
                        currentComponent.setColor(net.md_5.bungee.api.ChatColor.of(hexColor));
                        
                        // 跳过颜色代码
                        i += 13;
                        continue;
                    } catch (Exception e) {
                        // 如果解析失败，当作普通字符处理
                    }
                }
                // 传统颜色代码处理
                else if (Character.isLetterOrDigit(next)) {
                    // 添加当前组件
                    if (!currentComponent.getText().isEmpty()) {
                        components.add(currentComponent);
                    }
                    
                    // 创建新组件并设置传统颜色
                    currentComponent = new TextComponent("");
                    // 使用 BungeeCord 的 ChatColor 解析传统颜色代码
                    net.md_5.bungee.api.ChatColor bungeeColor = net.md_5.bungee.api.ChatColor.getByChar(next);
                    if (bungeeColor != null) {
                        currentComponent.setColor(bungeeColor);
                    }
                    
                    i++; // 跳过下一个字符
                    continue;
                }
            }
            
            // 普通字符，添加到当前组件
            currentComponent.setText(currentComponent.getText() + c);
        }
        
        // 添加最后一个组件
        if (!currentComponent.getText().isEmpty()) {
            components.add(currentComponent);
        }
        
        return components.toArray(new BaseComponent[0]);
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
            switch (subCommand) {
                case "cp" -> {
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
                }
                
                // /xlrchat reload - 重载配置
                case "reload" -> {
                if (!player.hasPermission("xlr.admin.reload")) {
                    String noPermMsg = config.getString("Message.NoPermission", "&c你没有权限执行此命令");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermMsg));
                    return true;
                }
                
                savePlayerData();
                reloadConfig();

                // 发送重载消息（支持多行）
                List<String> reloadMessages = config.getStringList("Command.reload");
                if (reloadMessages.isEmpty()) {
                    // 默认消息
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7配置已重新加载"));
                } else {
                    for (String msg : reloadMessages) {
                        msg = msg.replace("%chat_format%", String.valueOf(getChatFormatCount()));
                        msg = msg.replace("%color_config%", String.valueOf(colorVariables.size()));
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                    }
                }
                }
                
                // /xlrchat help - 显示帮助
                case "help" -> sendHelpMessage(player);
                
                // 未知子命令
                default -> {
                    String unknownMsg = config.getString("Message.UnknownSubCmd", "&c未知的子命令");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', unknownMsg));
                }
            }
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