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
        loadDisplayItemConfig();
        // 重新加载玩家数据
        loadPlayerData();
    }

    /**
     * 加载 DisplayItem 配置
     */
    private void loadDisplayItemConfig() {
        displayItemEnabled = config.getBoolean("Displayitem", false);
        displayItemLanguage = config.getString("DiaplayLanguage", "zh-cn").toLowerCase();
        
        // 验证语言配置
        if (!displayItemLanguage.equals("zh-cn") && !displayItemLanguage.equals("en-us")) {
            getLogger().warning("[警告] DiaplayLanguage 配置无效: " + displayItemLanguage);
            getLogger().warning("[提示] 有效值: zh-cn（中文简体）, en-us（英文）");
            displayItemLanguage = "zh-cn"; // 默认使用中文
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
     * 获取玩家手持物品的显示文本
     * @param player 玩家
     * @return 物品显示文本，格式: [物品名称 x数量]，如果没有物品则返回 null
     */
    private String getHandItemDisplay(Player player) {
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        
        // 检查是否有物品（Spigot API 保证不会返回 null，只检查 AIR）
        if (item.getType() == org.bukkit.Material.AIR) {
            return null;
        }
        
        // 获取物品名称和颜色（根据语言配置）
        String itemName = getItemDisplayName(item.getType());
        String itemColor = getItemColorCode(item.getType());
        int amount = item.getAmount();
        
        // 返回格式: &7[&7(物品颜色)物品 &f&xe数量&7]
        // 例如: &7[&b箱子 &f&xe30&7]
        return "&7[" + itemColor + itemName + " &f&xe" + amount + "&7]";
    }
    
    /**
     * 获取物品的颜色代码
     * @param material 物品类型
     * @return 颜色代码（& 格式），例如 &b 表示青色
     */
    private String getItemColorCode(org.bukkit.Material material) {
        // 根据物品类型返回对应的颜色
        return switch (material) {
            // 矿石类 - 根据颜色分类
            case COAL_ORE, COAL, CHARCOAL -> "&7"; // 灰色
            case IRON_ORE, IRON_INGOT, IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_HOE, IRON_SWORD,
                 IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS -> "&f"; // 白色
            case GOLD_ORE, GOLD_INGOT, GOLDEN_PICKAXE, GOLDEN_AXE, GOLDEN_SHOVEL, GOLDEN_HOE, GOLDEN_SWORD,
                 GOLDEN_HELMET, GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS, GOLDEN_BOOTS, GOLDEN_APPLE,
                 ENCHANTED_GOLDEN_APPLE -> "&6"; // 金色
            case DIAMOND_ORE, DIAMOND, DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_HOE, DIAMOND_SWORD,
                 DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS -> "&b"; // 青色
            case EMERALD_ORE, EMERALD -> "&a"; // 绿色
            case LAPIS_ORE, LAPIS_LAZULI -> "&9"; // 蓝色
            case REDSTONE_ORE, REDSTONE -> "&c"; // 红色
            
            // 木材类 - 根据木材颜色
            case OAK_LOG, OAK_PLANKS, OAK_DOOR, OAK_TRAPDOOR, OAK_SAPLING, OAK_LEAVES -> "&6"; // 橡木色
            case SPRUCE_LOG, SPRUCE_PLANKS, SPRUCE_SAPLING, SPRUCE_LEAVES -> "&4"; // 深棕色
            case BIRCH_LOG, BIRCH_PLANKS, BIRCH_SAPLING, BIRCH_LEAVES -> "&f"; // 白色
            case JUNGLE_LOG, JUNGLE_PLANKS, JUNGLE_SAPLING, JUNGLE_LEAVES -> "&2"; // 深绿色
            case ACACIA_LOG, ACACIA_PLANKS, ACACIA_SAPLING, ACACIA_LEAVES -> "&c"; // 橙红色
            case DARK_OAK_LOG, DARK_OAK_PLANKS, DARK_OAK_SAPLING, DARK_OAK_LEAVES -> "&4"; // 深棕色
            
            // 方块类
            case STONE, COBBLESTONE, GRAVEL -> "&8"; // 深灰色
            case GRANITE -> "&c"; // 粉红色
            case DIORITE -> "&f"; // 白色
            case ANDESITE -> "&7"; // 灰色
            case GRASS_BLOCK, DIRT -> "&2"; // 绿色/棕色
            case SAND -> "&e"; // 黄色
            case RED_SAND -> "&6"; // 橙色
            case GLASS -> "&f"; // 透明/白色
            case BRICK -> "&c"; // 红色
            case BOOKSHELF -> "&6"; // 棕色
            case CHEST -> "&6"; // 棕色
            case CRAFTING_TABLE -> "&6"; // 棕色
            case FURNACE -> "&8"; // 灰色
            case TORCH -> "&e"; // 黄色
            case LADDER -> "&6"; // 棕色
            case BEDROCK -> "&8"; // 深灰色
            case TNT -> "&c"; // 红色
            
            // 工具类（按材质分类，已在上面定义）
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_HOE, WOODEN_SWORD, STICK -> "&6"; // 木色
            case STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_HOE, STONE_SWORD -> "&7"; // 石灰色
            case NETHERITE_PICKAXE, NETHERITE_AXE, NETHERITE_SHOVEL, NETHERITE_HOE, NETHERITE_SWORD,
                 NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> "&5"; // 紫色
            
            // 皮革盔甲
            case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS, LEATHER -> "&c"; // 红棕色
            
            // 锁链盔甲
            case CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS -> "&7"; // 灰色
            
            // 食物类
            case APPLE, BEETROOT -> "&c"; // 红色
            case BREAD, POTATO, BAKED_POTATO, PUMPKIN_PIE, COOKIE, GLOWSTONE_DUST, CLOCK, MAP -> "&e"; // 黄色
            case COOKED_PORKCHOP, COOKED_BEEF, COOKED_CHICKEN, COOKED_MUTTON, COOKED_RABBIT,
                 COOKED_COD, COOKED_SALMON, CARROT, COCOA_BEANS -> "&6"; // 棕色
            case MELON_SLICE, SLIME_BALL -> "&a"; // 绿色
            case CAKE, STRING, FEATHER, PAPER, BOOK, BONE, EGG, COMPASS, NAME_TAG, LEAD, MILK_BUCKET -> "&f"; // 白色
            
            // 材料类（已在食物类中合并）
            
            // 药水类
            case POTION, SPLASH_POTION, LINGERING_POTION -> "&b"; // 蓝色
            
            // 其他
            case BOW, FISHING_ROD -> "&6"; // 棕色
            case CROSSBOW, SHIELD, GUNPOWDER, FLINT, INK_SAC -> "&8"; // 深灰色/黑色
            case FLINT_AND_STEEL, SHEARS, BUCKET -> "&7"; // 灰色
            case SADDLE -> "&4"; // 深棕色
            case WATER_BUCKET -> "&9"; // 蓝色
            case LAVA_BUCKET -> "&c"; // 红色
            
            // 默认：返回白色
            default -> "&f";
        };
    }
    
    /**
     * 获取物品的显示名称（根据配置的语言）
     * @param material 物品类型
     * @return 物品名称（中文或英文）
     */
    private String getItemDisplayName(org.bukkit.Material material) {
        if (displayItemLanguage.equals("en-us")) {
            // 英文：返回格式化的英文名称
            return material.name().replace('_', ' ').toLowerCase();
        } else {
            // 中文：返回中文名称
            return getItemChineseName(material);
        }
    }
    
    /**
     * 获取物品的中文名称（公共方法，供GuiManager使用）
     * @param material 物品类型
     * @return 中文名称，如果没有映射则返回英文名称
     */
    public String getItemChineseName(org.bukkit.Material material) {
        // 常见物品的中文映射
        return switch (material) {
            // 方块类
            case STONE -> "石头";
            case GRANITE -> "花岗岩";
            case POLISHED_GRANITE -> "磨制花岗岩";
            case DIORITE -> "闪长岩";
            case POLISHED_DIORITE -> "磨制闪长岩";
            case ANDESITE -> "安山岩";
            case POLISHED_ANDESITE -> "磨制安山岩";
            case GRASS_BLOCK -> "草方块";
            case DIRT -> "泥土";
            case COARSE_DIRT -> "砂土";
            case PODZOL -> "灰化土";
            case COBBLESTONE -> "圆石";
            case MOSSY_COBBLESTONE -> "覆苔圆石";
            case OAK_PLANKS -> "橡木木板";
            case SPRUCE_PLANKS -> "云杉木板";
            case BIRCH_PLANKS -> "白桦木板";
            case JUNGLE_PLANKS -> "丛林木板";
            case ACACIA_PLANKS -> "金合欢木板";
            case DARK_OAK_PLANKS -> "深色橡木木板";
            case CRIMSON_PLANKS -> "绯红木板";
            case WARPED_PLANKS -> "诡异木板";
            case OAK_LOG -> "橡木原木";
            case SPRUCE_LOG -> "云杉原木";
            case BIRCH_LOG -> "白桦原木";
            case JUNGLE_LOG -> "丛林原木";
            case ACACIA_LOG -> "金合欢原木";
            case DARK_OAK_LOG -> "深色橡木原木";
            case CRIMSON_STEM -> "绯红木柄";
            case WARPED_STEM -> "诡异木柄";
            case STRIPPED_OAK_LOG -> "去皮橡木原木";
            case STRIPPED_SPRUCE_LOG -> "去皮云杉原木";
            case STRIPPED_BIRCH_LOG -> "去皮白桦原木";
            case STRIPPED_JUNGLE_LOG -> "去皮丛林原木";
            case STRIPPED_ACACIA_LOG -> "去皮金合欢原木";
            case STRIPPED_DARK_OAK_LOG -> "去皮深色橡木原木";
            case SAND -> "沙子";
            case RED_SAND -> "红沙";
            case GRAVEL -> "砂砾";
            case COAL_ORE -> "煤矿石";
            case DEEPSLATE_COAL_ORE -> "深层煤矿石";
            case IRON_ORE -> "铁矿石";
            case DEEPSLATE_IRON_ORE -> "深层铁矿石";
            case GOLD_ORE -> "金矿石";
            case DEEPSLATE_GOLD_ORE -> "深层金矿石";
            case DIAMOND_ORE -> "钻石矿石";
            case DEEPSLATE_DIAMOND_ORE -> "深层钻石矿石";
            case EMERALD_ORE -> "绿宝石矿石";
            case DEEPSLATE_EMERALD_ORE -> "深层绿宝石矿石";
            case LAPIS_ORE -> "青金石矿石";
            case DEEPSLATE_LAPIS_ORE -> "深层青金石矿石";
            case REDSTONE_ORE -> "红石矿石";
            case DEEPSLATE_REDSTONE_ORE -> "深层红石矿石";
            case COPPER_ORE -> "铜矿石";
            case DEEPSLATE_COPPER_ORE -> "深层铜矿石";
            case GLASS -> "玻璃";
            case BRICK -> "砖块";
            case BOOKSHELF -> "书架";
            case CHEST -> "箱子";
            case TRAPPED_CHEST -> "陷阱箱";
            case CRAFTING_TABLE -> "工作台";
            case FURNACE -> "熔炉";
            case BLAST_FURNACE -> "高炉";
            case SMOKER -> "烟熏炉";
            case TORCH -> "火把";
            case SOUL_TORCH -> "灵魂火把";
            case LADDER -> "梯子";
            case OAK_DOOR -> "橡木门";
            case SPRUCE_DOOR -> "云杉门";
            case BIRCH_DOOR -> "白桦门";
            case JUNGLE_DOOR -> "丛林门";
            case ACACIA_DOOR -> "金合欢门";
            case DARK_OAK_DOOR -> "深色橡木门";
            case CRIMSON_DOOR -> "绯红木门";
            case WARPED_DOOR -> "诡异木门";
            case OAK_TRAPDOOR -> "橡木活板门";
            case SPRUCE_TRAPDOOR -> "云杉活板门";
            case BIRCH_TRAPDOOR -> "白桦活板门";
            case JUNGLE_TRAPDOOR -> "丛林活板门";
            case ACACIA_TRAPDOOR -> "金合欢活板门";
            case DARK_OAK_TRAPDOOR -> "深色橡木活板门";
            case CRIMSON_TRAPDOOR -> "绯红木活板门";
            case WARPED_TRAPDOOR -> "诡异木活板门";
            case IRON_DOOR -> "铁门";
            case IRON_TRAPDOOR -> "铁活板门";
            case STONE_PRESSURE_PLATE -> "石头压力板";
            case OAK_PRESSURE_PLATE -> "橡木压力板";
            case SPRUCE_PRESSURE_PLATE -> "云杉压力板";
            case BIRCH_PRESSURE_PLATE -> "白桦压力板";
            case JUNGLE_PRESSURE_PLATE -> "丛林压力板";
            case ACACIA_PRESSURE_PLATE -> "金合欢压力板";
            case DARK_OAK_PRESSURE_PLATE -> "深色橡木压力板";
            case CRIMSON_PRESSURE_PLATE -> "绯红木压力板";
            case WARPED_PRESSURE_PLATE -> "诡异木压力板";
            case STONE_BUTTON -> "石头按钮";
            case OAK_BUTTON -> "橡木按钮";
            case SPRUCE_BUTTON -> "云杉按钮";
            case BIRCH_BUTTON -> "白桦按钮";
            case JUNGLE_BUTTON -> "丛林按钮";
            case ACACIA_BUTTON -> "金合欢按钮";
            case DARK_OAK_BUTTON -> "深色橡木按钮";
            case CRIMSON_BUTTON -> "绯红木按钮";
            case WARPED_BUTTON -> "诡异木按钮";
            case BEDROCK -> "基岩";
            case TNT -> "TNT";
            case OBSIDIAN -> "黑曜石";
            case CRYING_OBSIDIAN -> "哭泣的黑曜石";
            case GLOWSTONE -> "荧石";
            case JACK_O_LANTERN -> "南瓜灯";
            case CARVED_PUMPKIN -> "雕刻过的南瓜";
            case PUMPKIN -> "南瓜";
            case MELON -> "西瓜";
            case HAY_BLOCK -> "干草块";
            case WHITE_WOOL -> "白色羊毛";
            case ORANGE_WOOL -> "橙色羊毛";
            case MAGENTA_WOOL -> "品红色羊毛";
            case LIGHT_BLUE_WOOL -> "淡蓝色羊毛";
            case YELLOW_WOOL -> "黄色羊毛";
            case LIME_WOOL -> "黄绿色羊毛";
            case PINK_WOOL -> "粉红色羊毛";
            case GRAY_WOOL -> "灰色羊毛";
            case LIGHT_GRAY_WOOL -> "淡灰色羊毛";
            case CYAN_WOOL -> "青色羊毛";
            case PURPLE_WOOL -> "紫色羊毛";
            case BLUE_WOOL -> "蓝色羊毛";
            case BROWN_WOOL -> "棕色羊毛";
            case GREEN_WOOL -> "绿色羊毛";
            case RED_WOOL -> "红色羊毛";
            case BLACK_WOOL -> "黑色羊毛";
            case SNOW_BLOCK -> "雪块";
            case ICE -> "冰";
            case PACKED_ICE -> "浮冰";
            case BLUE_ICE -> "蓝冰";
            case NETHERRACK -> "下界岩";
            case SOUL_SAND -> "灵魂沙";
            case SOUL_SOIL -> "灵魂土";
            case BASALT -> "玄武岩";
            case BLACKSTONE -> "黑石";
            case GILDED_BLACKSTONE -> "镶金黑石";
            case NETHER_QUARTZ_ORE -> "下界石英矿石";
            case NETHER_GOLD_ORE -> "下界金矿石";
            case ANCIENT_DEBRIS -> "远古残骸";
            case END_STONE -> "末地石";
            case PURPUR_BLOCK -> "紫珀方块";
            case PRISMARINE -> "海晶石";
            case PRISMARINE_BRICKS -> "海晶石砖";
            case DARK_PRISMARINE -> "暗海晶石";
            case SEA_LANTERN -> "海晶灯";
            case MAGMA_BLOCK -> "岩浆块";
            case NETHER_WART_BLOCK -> "下界疣方块";
            case WARPED_WART_BLOCK -> "诡异疣方块";
            case SHROOMLIGHT -> "菌光体";
            case HONEYCOMB_BLOCK -> "蜜脾块";
            case HONEY_BLOCK -> "蜂蜜块";
            case SLIME_BLOCK -> "黏液块";
            case SPONGE -> "海绵";
            case WET_SPONGE -> "湿海绵";
            case TERRACOTTA -> "陶瓦";
            case WHITE_TERRACOTTA -> "白色陶瓦";
            case ORANGE_TERRACOTTA -> "橙色陶瓦";
            case MAGENTA_TERRACOTTA -> "品红色陶瓦";
            case LIGHT_BLUE_TERRACOTTA -> "淡蓝色陶瓦";
            case YELLOW_TERRACOTTA -> "黄色陶瓦";
            case LIME_TERRACOTTA -> "黄绿色陶瓦";
            case PINK_TERRACOTTA -> "粉红色陶瓦";
            case GRAY_TERRACOTTA -> "灰色陶瓦";
            case LIGHT_GRAY_TERRACOTTA -> "淡灰色陶瓦";
            case CYAN_TERRACOTTA -> "青色陶瓦";
            case PURPLE_TERRACOTTA -> "紫色陶瓦";
            case BLUE_TERRACOTTA -> "蓝色陶瓦";
            case BROWN_TERRACOTTA -> "棕色陶瓦";
            case GREEN_TERRACOTTA -> "绿色陶瓦";
            case RED_TERRACOTTA -> "红色陶瓦";
            case BLACK_TERRACOTTA -> "黑色陶瓦";
            case WHITE_CONCRETE -> "白色混凝土";
            case ORANGE_CONCRETE -> "橙色混凝土";
            case MAGENTA_CONCRETE -> "品红色混凝土";
            case LIGHT_BLUE_CONCRETE -> "淡蓝色混凝土";
            case YELLOW_CONCRETE -> "黄色混凝土";
            case LIME_CONCRETE -> "黄绿色混凝土";
            case PINK_CONCRETE -> "粉红色混凝土";
            case GRAY_CONCRETE -> "灰色混凝土";
            case LIGHT_GRAY_CONCRETE -> "淡灰色混凝土";
            case CYAN_CONCRETE -> "青色混凝土";
            case PURPLE_CONCRETE -> "紫色混凝土";
            case BLUE_CONCRETE -> "蓝色混凝土";
            case BROWN_CONCRETE -> "棕色混凝土";
            case GREEN_CONCRETE -> "绿色混凝土";
            case RED_CONCRETE -> "红色混凝土";
            case BLACK_CONCRETE -> "黑色混凝土";
            case WHITE_GLAZED_TERRACOTTA -> "白色带釉陶瓦";
            case ORANGE_GLAZED_TERRACOTTA -> "橙色带釉陶瓦";
            case MAGENTA_GLAZED_TERRACOTTA -> "品红色带釉陶瓦";
            case LIGHT_BLUE_GLAZED_TERRACOTTA -> "淡蓝色带釉陶瓦";
            case YELLOW_GLAZED_TERRACOTTA -> "黄色带釉陶瓦";
            case LIME_GLAZED_TERRACOTTA -> "黄绿色带釉陶瓦";
            case PINK_GLAZED_TERRACOTTA -> "粉红色带釉陶瓦";
            case GRAY_GLAZED_TERRACOTTA -> "灰色带釉陶瓦";
            case LIGHT_GRAY_GLAZED_TERRACOTTA -> "淡灰色带釉陶瓦";
            case CYAN_GLAZED_TERRACOTTA -> "青色带釉陶瓦";
            case PURPLE_GLAZED_TERRACOTTA -> "紫色带釉陶瓦";
            case BLUE_GLAZED_TERRACOTTA -> "蓝色带釉陶瓦";
            case BROWN_GLAZED_TERRACOTTA -> "棕色带釉陶瓦";
            case GREEN_GLAZED_TERRACOTTA -> "绿色带釉陶瓦";
            case RED_GLAZED_TERRACOTTA -> "红色带釉陶瓦";
            case BLACK_GLAZED_TERRACOTTA -> "黑色带釉陶瓦";
            case WHITE_STAINED_GLASS -> "白色染色玻璃";
            case ORANGE_STAINED_GLASS -> "橙色染色玻璃";
            case MAGENTA_STAINED_GLASS -> "品红色染色玻璃";
            case LIGHT_BLUE_STAINED_GLASS -> "淡蓝色染色玻璃";
            case YELLOW_STAINED_GLASS -> "黄色染色玻璃";
            case LIME_STAINED_GLASS -> "黄绿色染色玻璃";
            case PINK_STAINED_GLASS -> "粉红色染色玻璃";
            case GRAY_STAINED_GLASS -> "灰色染色玻璃";
            case LIGHT_GRAY_STAINED_GLASS -> "淡灰色染色玻璃";
            case CYAN_STAINED_GLASS -> "青色染色玻璃";
            case PURPLE_STAINED_GLASS -> "紫色染色玻璃";
            case BLUE_STAINED_GLASS -> "蓝色染色玻璃";
            case BROWN_STAINED_GLASS -> "棕色染色玻璃";
            case GREEN_STAINED_GLASS -> "绿色染色玻璃";
            case RED_STAINED_GLASS -> "红色染色玻璃";
            case BLACK_STAINED_GLASS -> "黑色染色玻璃";
            
            // 玻璃板类
            case WHITE_STAINED_GLASS_PANE -> "白色玻璃板";
            case ORANGE_STAINED_GLASS_PANE -> "橙色玻璃板";
            case MAGENTA_STAINED_GLASS_PANE -> "品红色玻璃板";
            case LIGHT_BLUE_STAINED_GLASS_PANE -> "淡蓝色玻璃板";
            case YELLOW_STAINED_GLASS_PANE -> "黄色玻璃板";
            case LIME_STAINED_GLASS_PANE -> "黄绿色玻璃板";
            case PINK_STAINED_GLASS_PANE -> "粉红色玻璃板";
            case GRAY_STAINED_GLASS_PANE -> "灰色玻璃板";
            case LIGHT_GRAY_STAINED_GLASS_PANE -> "淡灰色玻璃板";
            case CYAN_STAINED_GLASS_PANE -> "青色玻璃板";
            case PURPLE_STAINED_GLASS_PANE -> "紫色玻璃板";
            case BLUE_STAINED_GLASS_PANE -> "蓝色玻璃板";
            case BROWN_STAINED_GLASS_PANE -> "棕色玻璃板";
            case GREEN_STAINED_GLASS_PANE -> "绿色玻璃板";
            case RED_STAINED_GLASS_PANE -> "红色玻璃板";
            case BLACK_STAINED_GLASS_PANE -> "黑色玻璃板";
            case WHITE_CARPET -> "白色地毯";
            case ORANGE_CARPET -> "橙色地毯";
            case MAGENTA_CARPET -> "品红色地毯";
            case LIGHT_BLUE_CARPET -> "淡蓝色地毯";
            case YELLOW_CARPET -> "黄色地毯";
            case LIME_CARPET -> "黄绿色地毯";
            case PINK_CARPET -> "粉红色地毯";
            case GRAY_CARPET -> "灰色地毯";
            case LIGHT_GRAY_CARPET -> "淡灰色地毯";
            case CYAN_CARPET -> "青色地毯";
            case PURPLE_CARPET -> "紫色地毯";
            case BLUE_CARPET -> "蓝色地毯";
            case BROWN_CARPET -> "棕色地毯";
            case GREEN_CARPET -> "绿色地毯";
            case RED_CARPET -> "红色地毯";
            case BLACK_CARPET -> "黑色地毯";
            case WHITE_BED -> "白色床";
            case ORANGE_BED -> "橙色床";
            case MAGENTA_BED -> "品红色床";
            case LIGHT_BLUE_BED -> "淡蓝色床";
            case YELLOW_BED -> "黄色床";
            case LIME_BED -> "黄绿色床";
            case PINK_BED -> "粉红色床";
            case GRAY_BED -> "灰色床";
            case LIGHT_GRAY_BED -> "淡灰色床";
            case CYAN_BED -> "青色床";
            case PURPLE_BED -> "紫色床";
            case BLUE_BED -> "蓝色床";
            case BROWN_BED -> "棕色床";
            case GREEN_BED -> "绿色床";
            case RED_BED -> "红色床";
            case BLACK_BED -> "黑色床";
            case BARREL -> "木桶";
            case SMITHING_TABLE -> "锻造台";
            case FLETCHING_TABLE -> "制箭台";
            case CARTOGRAPHY_TABLE -> "制图台";
            case LOOM -> "织布机";
            case COMPOSTER -> "堆肥桶";
            case BREWING_STAND -> "酿造台";
            case CAULDRON -> "炼药锅";
            case GRINDSTONE -> "砂轮";
            case STONECUTTER -> "切石机";
            case LECTERN -> "讲台";
            case ANVIL -> "铁砧";
            case CHIPPED_ANVIL -> "受损的铁";
            case DAMAGED_ANVIL -> "严重受损的铁砧";
            case ENCHANTING_TABLE -> "附魔台";
            case ENDER_CHEST -> "末影箱";
            case BEACON -> "信标";
            case CONDUIT -> "潮涌核心";
            case DRAGON_HEAD -> "末影龙头颅";
            case PLAYER_HEAD -> "玩家头颅";
            case ZOMBIE_HEAD -> "僵尸头颅";
            case CREEPER_HEAD -> "苦力怕头颅";
            case SKELETON_SKULL -> "骷髅头颅";
            case WITHER_SKELETON_SKULL -> "凋灵骷髅头颅";
            case PIGLIN_HEAD -> "猪灵头颅";
            case NOTE_BLOCK -> "音符盒";
            case JUKEBOX -> "唱片机";
            case DISPENSER -> "发射器";
            case DROPPER -> "投掷器";
            case HOPPER -> "漏斗";
            case REPEATER -> "红石中继器";
            case COMPARATOR -> "红石比较器";
            case OBSERVER -> "侦测器";
            case PISTON -> "活塞";
            case STICKY_PISTON -> "黏性活塞";
            case RAIL -> "铁轨";
            case POWERED_RAIL -> "充能铁轨";
            case DETECTOR_RAIL -> "探测铁轨";
            case ACTIVATOR_RAIL -> "激活铁轨";
            case MINECART -> "矿车";
            case CHEST_MINECART -> "运输矿车";
            case FURNACE_MINECART -> "动力矿车";
            case TNT_MINECART -> "TNT矿车";
            case HOPPER_MINECART -> "漏斗矿车";
            case COMMAND_BLOCK_MINECART -> "命令方块矿车";
            
            // 工具类
            case WOODEN_PICKAXE -> "木镐";
            case STONE_PICKAXE -> "石镐";
            case IRON_PICKAXE -> "铁镐";
            case GOLDEN_PICKAXE -> "金镐";
            case DIAMOND_PICKAXE -> "钻石镐";
            case NETHERITE_PICKAXE -> "下界合金镐";
            case WOODEN_AXE -> "木斧";
            case STONE_AXE -> "石斧";
            case IRON_AXE -> "铁斧";
            case GOLDEN_AXE -> "金斧";
            case DIAMOND_AXE -> "钻石斧";
            case NETHERITE_AXE -> "下界合金斧";
            case WOODEN_SHOVEL -> "木铲";
            case STONE_SHOVEL -> "石铲";
            case IRON_SHOVEL -> "铁铲";
            case GOLDEN_SHOVEL -> "金铲";
            case DIAMOND_SHOVEL -> "钻石铲";
            case NETHERITE_SHOVEL -> "下界合金铲";
            case WOODEN_HOE -> "木锄";
            case STONE_HOE -> "石锄";
            case IRON_HOE -> "铁锄";
            case GOLDEN_HOE -> "金锄";
            case DIAMOND_HOE -> "钻石锄";
            case NETHERITE_HOE -> "下界合金锄";
            case WOODEN_SWORD -> "木剑";
            case STONE_SWORD -> "石剑";
            case IRON_SWORD -> "铁剑";
            case GOLDEN_SWORD -> "金剑";
            case DIAMOND_SWORD -> "钻石剑";
            case NETHERITE_SWORD -> "下界合金剑";
            
            // 盔甲类
            case LEATHER_HELMET -> "皮革头盔";
            case LEATHER_CHESTPLATE -> "皮革胸甲";
            case LEATHER_LEGGINGS -> "皮革护腿";
            case LEATHER_BOOTS -> "皮革靴子";
            case CHAINMAIL_HELMET -> "锁链头盔";
            case CHAINMAIL_CHESTPLATE -> "锁链胸甲";
            case CHAINMAIL_LEGGINGS -> "锁链护腿";
            case CHAINMAIL_BOOTS -> "锁链靴子";
            case IRON_HELMET -> "铁头盔";
            case IRON_CHESTPLATE -> "铁胸甲";
            case IRON_LEGGINGS -> "铁护腿";
            case IRON_BOOTS -> "铁靴子";
            case GOLDEN_HELMET -> "金头盔";
            case GOLDEN_CHESTPLATE -> "金胸甲";
            case GOLDEN_LEGGINGS -> "金护腿";
            case GOLDEN_BOOTS -> "金靴子";
            case DIAMOND_HELMET -> "钻石头盔";
            case DIAMOND_CHESTPLATE -> "钻石胸甲";
            case DIAMOND_LEGGINGS -> "钻石护腿";
            case DIAMOND_BOOTS -> "钻石靴子";
            case NETHERITE_HELMET -> "下界合金头盔";
            case NETHERITE_CHESTPLATE -> "下界合金胸甲";
            case NETHERITE_LEGGINGS -> "下界合金护腿";
            case NETHERITE_BOOTS -> "下界合金靴子";
            
            // 食物类
            case APPLE -> "苹果";
            case BREAD -> "面包";
            case COOKED_PORKCHOP -> "熟猪排";
            case COOKED_BEEF -> "熟牛肉";
            case COOKED_CHICKEN -> "熟鸡肉";
            case COOKED_MUTTON -> "熟羊肉";
            case COOKED_RABBIT -> "熟兔肉";
            case COOKED_COD -> "熟鳕鱼";
            case COOKED_SALMON -> "熟鲑鱼";
            case GOLDEN_APPLE -> "金苹果";
            case ENCHANTED_GOLDEN_APPLE -> "附魔金苹果";
            case CARROT -> "胡萝卜";
            case POTATO -> "马铃薯";
            case BAKED_POTATO -> "烤马铃薯";
            case BEETROOT -> "甜菜根";
            case MELON_SLICE -> "西瓜片";
            case PUMPKIN_PIE -> "南瓜派";
            case COOKIE -> "曲奇";
            case CAKE -> "蛋糕";
            
            // 材料类
            case COAL -> "煤炭";
            case CHARCOAL -> "木炭";
            case IRON_INGOT -> "铁锭";
            case GOLD_INGOT -> "金锭";
            case DIAMOND -> "钻石";
            case EMERALD -> "绿宝石";
            case LAPIS_LAZULI -> "青金石";
            case REDSTONE -> "红石粉";
            case STICK -> "木棍";
            case STRING -> "线";
            case FEATHER -> "羽毛";
            case GUNPOWDER -> "火药";
            case FLINT -> "燧石";
            case BONE -> "骨头";
            case LEATHER -> "皮革";
            case RABBIT_HIDE -> "兔子皮";
            case PAPER -> "纸";
            case BOOK -> "书";
            case SLIME_BALL -> "黏液球";
            case EGG -> "鸡蛋";
            case GLOWSTONE_DUST -> "荧石粉";
            case INK_SAC -> "墨囊";
            case COCOA_BEANS -> "可可豆";
            
            // 药水类
            case POTION -> "药水";
            case SPLASH_POTION -> "喷溅药水";
            case LINGERING_POTION -> "滞留药水";
            
            // 其他
            case BOW -> "弓";
            case CROSSBOW -> "弩";
            case SHIELD -> "盾牌";
            case FISHING_ROD -> "钓鱼竿";
            case FLINT_AND_STEEL -> "打火石";
            case SHEARS -> "剪刀";
            case COMPASS -> "指南针";
            case CLOCK -> "时钟";
            case MAP -> "地图";
            case NAME_TAG -> "命名牌";
            case LEAD -> "栓绳";
            case SADDLE -> "鞍";
            case WATER_BUCKET -> "水桶";
            case LAVA_BUCKET -> "岩浆桶";
            case MILK_BUCKET -> "奶桶";
            case BUCKET -> "桶";
            case OAK_SAPLING -> "橡树苗";
            case SPRUCE_SAPLING -> "云杉树苗";
            case BIRCH_SAPLING -> "白桦树苗";
            case JUNGLE_SAPLING -> "丛林树苗";
            case ACACIA_SAPLING -> "金合欢树苗";
            case DARK_OAK_SAPLING -> "深色橡树苗";
            case OAK_LEAVES -> "橡树树叶";
            case SPRUCE_LEAVES -> "云杉树叶";
            case BIRCH_LEAVES -> "白桦树叶";
            case JUNGLE_LEAVES -> "丛林树叶";
            case ACACIA_LEAVES -> "金合欢树叶";
            case DARK_OAK_LEAVES -> "深色橡树叶";
            
            // 默认：返回英文名称
            default -> material.name().replace('_', ' ').toLowerCase();
        };
    }

    /**
     * 处理格式字符串，替换占位符并应用颜色
     */
    private BaseComponent[] processFormatToComponent(String format, Player player, String message) {
        // 构建完整的消息
        String result = format;
        
        // 处理 [item] 占位符（如果启用）
        if (displayItemEnabled && message.contains("[item]")) {
            String itemDisplay = getHandItemDisplay(player);
            if (itemDisplay != null) {
                message = message.replace("[item]", itemDisplay);
            } else {
                // 如果手里没有物品，移除 [item]
                message = message.replace("[item]", "");
            }
        }
        
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
        
        // 处理颜色变量应用到 %chat% 上
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
        
        // 检查是否包含 %player% 且需要悬浮提示（在替换 %chat% 之前检查）
        boolean needHover = result.contains("%player%") && playerHoverLore != null && !playerHoverLore.isEmpty();
        
        // 最后替换 %chat%（处理没有颜色变量的情况）
        result = result.replace("%chat%", message);
        
        // 在转换 & -> § 之前，提取最后一个传统颜色代码（&a 格式）
        net.md_5.bungee.api.ChatColor playerColor = needHover ? extractLastColorCode(result) : null;
        
        // 转换传统颜色代码 & -> §
        result = ChatColor.translateAlternateColorCodes('&', result);
        
        if (needHover || needTitleHover) {
            return buildComponentWithHover(result, player, playerColor, playerColorGradient, title, titleId, needTitleHover);
        }
        
        // 不需要悬浮提示，直接替换 %player%
        result = result.replace("%player%", player.getName());
        return new BaseComponent[]{new TextComponent(result)};
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
                                                     boolean needTitleHover) {
        // 在替换 %player% 之前，先分割消息
        // 使用 split 并限制为 2，确保只分割第一个 %player%
        int playerIndex = message.indexOf("%player%");
        
        if (playerIndex == -1) {
            // 没有找到 %player%，直接返回
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
        
        // 添加 %player% 后面的文本（包含聊天消息，需要为其添加悬浮事件）
        if (!afterPlayer.isEmpty()) {
            // 将包含 § 格式的字符串转换为 BaseComponent
            BaseComponent[] backComponents = parseLegacyTextWithHexColors(afterPlayer);
            for (BaseComponent component : backComponents) {
                // 为聊天消息部分添加悬浮提示和点击事件
                if (component instanceof TextComponent textComp) {
                    // 清除可能继承的事件
                    textComp.setHoverEvent(null);
                    textComp.setClickEvent(null);
                    
                    // 设置 %chat% 的悬浮提示
                    BaseComponent[] chatHoverComponents = buildHoverComponents(chatHoverLore);
                    if (chatHoverComponents != null && chatHoverComponents.length > 0) {
                        textComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, chatHoverComponents));
                    }
                    
                    // 设置 %chat% 的点击事件
                    if (!chatSuggestCommands.isEmpty() && !chatRunCommands.isEmpty()) {
                        getLogger().warning("[警告] chat 同时配置了 Command 和 RunCommand，只使用 Command（预填）");
                        StringBuilder commandBuilder = new StringBuilder();
                        for (int i = 0; i < chatSuggestCommands.size(); i++) {
                            String command = chatSuggestCommands.get(i).replace("%player%", player.getName());
                            if (!command.startsWith("/")) {
                                command = "/" + command;
                            }
                            commandBuilder.append(command);
                            if (i < chatSuggestCommands.size() - 1) {
                                commandBuilder.append("; ");
                            }
                        }
                        textComp.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandBuilder.toString()));
                    } else if (!chatSuggestCommands.isEmpty()) {
                        StringBuilder commandBuilder = new StringBuilder();
                        for (int i = 0; i < chatSuggestCommands.size(); i++) {
                            String command = chatSuggestCommands.get(i).replace("%player%", player.getName());
                            if (!command.startsWith("/")) {
                                command = "/" + command;
                            }
                            commandBuilder.append(command);
                            if (i < chatSuggestCommands.size() - 1) {
                                commandBuilder.append("; ");
                            }
                        }
                        textComp.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandBuilder.toString()));
                    } else if (!chatRunCommands.isEmpty()) {
                        StringBuilder commandBuilder = new StringBuilder();
                        for (int i = 0; i < chatRunCommands.size(); i++) {
                            String command = chatRunCommands.get(i).replace("%player%", player.getName());
                            if (!command.startsWith("/")) {
                                command = "/" + command;
                            }
                            commandBuilder.append(command);
                            if (i < chatRunCommands.size() - 1) {
                                commandBuilder.append("; ");
                            }
                        }
                        textComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandBuilder.toString()));
                    }
                }
                builder.append(component);
            }
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(components);
        }
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