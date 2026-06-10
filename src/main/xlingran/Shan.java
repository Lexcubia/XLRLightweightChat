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
    
    private RequiredActions playerRequired = new RequiredActions();
    private RequiredActions chatRequired = new RequiredActions();
    
    // 物品展示配置
    private boolean displayItemEnabled = false; // 是否启用 [item] 占位符
    private String displayItemLanguage = "zh-cn"; // 物品显示语言：zh-cn（中文简体）或 en-us（英文）
    private boolean displayItemInGradient = false; // [item] 是否参与 %chat% 渐变
    private final Item itemService = new Item();

    /** 聊天组件插入点（不参与 & 色码转换） */
    private static final String CHAT_PART_MARKER = "\uE000XLRCHAT\uE001";
    /** 称号段插入点（用于悬浮 Lore，避免渐变后 indexOf 失败） */
    private static final String TITLE_PART_MARKER = "\uE000XLRTITLE\uE001";

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
                int matchedId = resolveTitleId(title);
                if (matchedId > 0) {
                    String canonical = playerTitles.get(matchedId);
                    if (canonical != null) {
                        title = canonical;
                    }
                }
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
        playerTitles = new TreeMap<>();
        playerTitleLore = new TreeMap<>();
        
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
        playerRequired = new RequiredActions();
        chatRequired = new RequiredActions();
        if (!config.contains("Required")) {
            return;
        }
        ConfigurationSection requiredSection = config.getConfigurationSection("Required");
        if (requiredSection == null) {
            return;
        }
        if (requiredSection.contains("player")) {
            playerRequired = RequiredActions.fromConfigList(requiredSection.getList("player"));
        }
        if (requiredSection.contains("chat")) {
            chatRequired = RequiredActions.fromConfigList(requiredSection.getList("chat"));
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
        
        String format = resolveChatFormat(player);
        if (format == null) {
            // 如果连默认格式都没有，直接发送原始消息
            runOnMainThread(() -> broadcastMessage(player.getName(), message));
            return;
        }

        final String chatFormat = format;
        runOnMainThread(() -> {
            try {
                BaseComponent[] components = processFormatToComponent(chatFormat, player, message);
                broadcastProcessedMessage(components);
            } catch (Throwable t) {
                getLogger().severe("[XLRLightweightChat] 聊天发送失败: " + t.getMessage());
                t.printStackTrace();
            }
        });
    }

    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    private String resolveChatFormat(Player player) {
        return PermissionGuards.resolveChatFormat(config.getConfigurationSection("Chat"), player::hasPermission);
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void sendPlayerMessages(Player player, List<String> messages, Map<String, String> placeholders) {
        if (messages.isEmpty()) {
            return;
        }
        for (String msg : messages) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    msg = msg.replace(entry.getKey(), entry.getValue());
                }
            }
            player.sendMessage(colorize(msg));
        }
    }

    private String applyColorVariables(String text) {
        String result = text;
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
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
        BaseComponent[] chatHoverComponents = chatRequired.hasHover()
                ? buildHoverComponents(chatRequired.hoverLore) : null;
        ClickEvent chatClick = chatRequired.createClickEvent(player, getLogger(), "chat");

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
                for (BaseComponent component : ChatComponents.parseLegacyTextWithHexColors(content)) {
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
        for (BaseComponent component : ChatComponents.parseLegacyTextWithHexColors(text)) {
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
                for (BaseComponent component : ChatComponents.parseLegacyTextWithHexColors(section)) {
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
            titleId = resolveTitleId(title);
            title = processTitleColors(title);
            if (titleId > 0) {
                List<String> lore = playerTitleLore.get(titleId);
                needTitleHover = lore != null && !lore.isEmpty();
            }
            if (needTitleHover) {
                result = result.replace("%title%", TITLE_PART_MARKER);
            } else {
                result = result.replace("%title%", title);
            }
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
        
        boolean needHover = result.contains("%player%") && playerRequired.hasHover();

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
        
        // 处理称号悬浮提示（在 %player% 之前，通过 TITLE_PART_MARKER 定位）
        if (needTitleHover && titleId > 0 && title != null) {
            int titleMarkerIndex = beforePlayer.indexOf(TITLE_PART_MARKER);
            if (titleMarkerIndex != -1) {
                String beforeTitle = beforePlayer.substring(0, titleMarkerIndex);
                if (!beforeTitle.isEmpty()) {
                    BaseComponent[] beforeComponents = ChatComponents.parseLegacyTextWithHexColors(beforeTitle);
                    for (BaseComponent component : beforeComponents) {
                        builder.append(component);
                    }
                }

                BaseComponent[] titleComponents = ChatComponents.parseLegacyTextWithHexColors(title);
                TextComponent titleComponent;
                if (titleComponents.length > 0 && titleComponents[0] instanceof TextComponent) {
                    titleComponent = (TextComponent) titleComponents[0];
                } else {
                    titleComponent = new TextComponent(title);
                }
                if (titleComponents.length > 1) {
                    for (int i = 1; i < titleComponents.length; i++) {
                        titleComponent.addExtra(titleComponents[i]);
                    }
                }

                List<String> titleLore = playerTitleLore.get(titleId);
                if (titleLore != null && !titleLore.isEmpty()) {
                    BaseComponent[] titleHoverComponents = buildHoverComponents(titleLore);
                    if (titleHoverComponents != null && titleHoverComponents.length > 0) {
                        titleComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, titleHoverComponents));
                    }
                }
                builder.append(titleComponent);
                beforePlayer = beforePlayer.substring(titleMarkerIndex + TITLE_PART_MARKER.length());
            }
        }
        
        // 添加 %player% 前面的文本（需要正确解析 16 进制颜色代码）
        if (!beforePlayer.isEmpty()) {
            // 将包含 § 格式的字符串转换为 BaseComponent
            BaseComponent[] frontComponents = ChatComponents.parseLegacyTextWithHexColors(beforePlayer);
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
            BaseComponent[] gradientComponents = ChatComponents.parseLegacyTextWithHexColors(gradientText);
            
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
        BaseComponent[] hoverComponents = buildHoverComponents(playerRequired.hoverLore);
        if (hoverComponents != null && hoverComponents.length > 0) {
            playerComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents));
        }
        ClickEvent playerClick = playerRequired.createClickEvent(player, getLogger(), "player");
        if (playerClick != null) {
            playerComponent.setClickEvent(playerClick);
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
        BaseComponent[] chatHoverComponents = chatRequired.hasHover()
                ? buildHoverComponents(chatRequired.hoverLore) : null;
        ClickEvent chatClick = chatRequired.createClickEvent(player, getLogger(), "chat");
        for (BaseComponent component : ChatComponents.parseLegacyTextWithHexColors(section)) {
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
        UUID playerId = player.getUniqueId();
        String currentTitle = playerCurrentTitles.get(playerId);
        String permittedTitle = PermissionGuards.resolvePermittedTitle(currentTitle, this::resolveTitleId,
                player::hasPermission);
        if (currentTitle != null && permittedTitle == null) {
            playerCurrentTitles.remove(playerId);
            savePlayerData();
        }
        return permittedTitle;
    }

    /**
     * 设置玩家当前穿戴的称号
     */
    public void setPlayerCurrentTitle(Player player, String title) {
        UUID playerId = player.getUniqueId();
        String previousTitle = playerCurrentTitles.get(playerId);
        if (Objects.equals(previousTitle, title)) {
            return;
        }
        if (title == null) {
            playerCurrentTitles.remove(playerId);
        } else {
            playerCurrentTitles.put(playerId, title);
        }
        savePlayerData();
    }

    /**
     * 处理称号中的颜色变量
     * 注意：称号名称和 Lore 已经在加载时转换为 § 格式，这里只处理颜色变量
     */
    public String processTitleColors(String title) {
        return applyGradientPlaceholders(title);
    }

    /**
     * 根据玩家当前穿戴的称号文本解析配置 ID（需与配置名同样经过 {@link #processTitleColors} 再比较）。
     */
    private int resolveTitleId(String wornTitle) {
        if (wornTitle == null || wornTitle.isEmpty()) {
            return -1;
        }
        String wornDisplay = processTitleColors(wornTitle);
        for (Map.Entry<Integer, String> entry : playerTitles.entrySet()) {
            if (processTitleColors(entry.getValue()).equals(wornDisplay)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private String applyGradientPlaceholders(String text) {
        String result = text;
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            String placeholder = entry.getKey();
            if (!result.contains(placeholder)) {
                continue;
            }
            int placeholderIndex = result.indexOf(placeholder);
            String beforePlaceholder = result.substring(0, placeholderIndex);
            String afterPlaceholder = result.substring(placeholderIndex + placeholder.length());
            result = beforePlaceholder + applyGradient(entry.getValue(), afterPlaceholder);
        }
        return result;
    }

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
        if (components == null || components.length == 0) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.spigot().sendMessage(components);
        }
    }

    private BaseComponent[] buildHoverComponents(List<String> hoverLore) {
        if (hoverLore == null || hoverLore.isEmpty()) {
            return null;
        }
        ComponentBuilder builder = new ComponentBuilder();
        for (int i = 0; i < hoverLore.size(); i++) {
            if (i > 0) {
                builder.append("\n");
            }
            String translated = colorize(applyGradientPlaceholders(hoverLore.get(i)));
            for (BaseComponent component : ChatComponents.parseLegacyTextWithHexColors(translated)) {
                builder.append(component);
            }
        }
        return builder.create();
    }

    private void broadcastMessage(String playerName, String message) {
        String formatted = "&a" + playerName + ": &f" + message;
        String translated = colorize(formatted);
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
                sender.sendMessage(colorize(noPlayerMsg));
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
                    sendNoPermission(player);
                    return true;
                }
                if (guiManager != null) {
                    guiManager.openTitleGUI(player);
                } else {
                    player.sendMessage(colorize("&c称号系统未初始化！"));
                }
                }
                
                // /xlrchat reload - 重载配置
                case "reload" -> {
                if (!player.hasPermission("xlr.admin.reload")) {
                    sendNoPermission(player);
                    return true;
                }
                savePlayerData();
                reloadConfig();
                List<String> reloadMessages = config.getStringList("Command.reload");
                if (reloadMessages.isEmpty()) {
                    player.sendMessage(colorize("&7配置已重新加载"));
                } else {
                    sendPlayerMessages(player, reloadMessages, Map.of(
                            "%chat_format%", String.valueOf(getChatFormatCount()),
                            "%color_config%", String.valueOf(colorVariables.size())));
                }
                }
                
                // /xlrchat help - 显示帮助
                case "help" -> sendHelpMessage(player);
                
                // 未知子命令
                default -> player.sendMessage(colorize(
                        config.getString("Message.UnknownSubCmd", "&c未知的子命令")));
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * 发送帮助信息
     */
    private void sendNoPermission(Player player) {
        player.sendMessage(colorize(config.getString("Message.NoPermission", "&c你没有权限执行此命令")));
    }

    private void sendHelpMessage(Player player) {
        List<String> helpMessages = config.getStringList("Command.help");
        if (helpMessages.isEmpty()) {
            sendPlayerMessages(player, List.of(
                    "&6===== [XLRLightweightChat] =====",
                    "&7- /xlrchat cp &6打开称号仓库",
                    "&7- /xlrchat reload &6重载该插件",
                    "&7- /xlrchat help &6显示帮助"), null);
        } else {
            sendPlayerMessages(player, helpMessages, null);
        }
    }
}