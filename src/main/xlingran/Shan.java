 package xlingran;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Shan extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, String> chatFormats = new LinkedHashMap<>();
    private final Map<String, String> variableColors = new HashMap<>();
    private final Map<String, String> playerTitles = new HashMap<>();
    private final Map<String, String> playerCurrentTitle = new HashMap<>(); // 存储玩家当前穿戴的称号
    
    // GUI 配置
    private FileConfiguration guiConfig;
    
    // 物品功能类
    private ShanItem shanItem;

    @Override
    public void onEnable() {
        // 保存默认配置文件（如果不存在）
        saveDefaultConfig();
        
        // 加载 GUI 配置
        loadGuiConfig();
        
        // 初始化物品功能类
        shanItem = new ShanItem(this);
        
        // 从配置文件读取所有聊天格式
        loadChatFormats();
        // 从配置文件读取变量颜色配置
        loadVariableColors();
        // 从配置文件读取玩家称号配置
        loadPlayerTitles();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 注册命令执行器
        var command = getCommand("xlrchat");
        if (command != null) {
            command.setExecutor(this);
        }

        Bukkit.getConsoleSender().sendMessage("§a[XLRLightweightChat] 插件已启用!");
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage("§c[XLRLightweightChat] 该插件已卸载");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("xlrchat")) {
            // 检查权限
            if (!sender.hasPermission("xlr.chat.reload")) {
                // 从配置读取无权限提示
                String noPermissionMsg = getConfig().getString("Message.NoPermission");
                if (noPermissionMsg != null && !noPermissionMsg.isEmpty()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermissionMsg));
                } else {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
                }
                return true;
            }

            // 处理 cp 子命令（称号仓库）
            if (args[0].equalsIgnoreCase("cp")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行!");
                    return true;
                }
                openTitleShopPage1(player);
                return true;
            }
            
            // 处理 reload 子命令
            if (args[0].equalsIgnoreCase("reload")) {
                reloadPluginConfig();
                
                // 从配置读取 reload 输出消息
                List<String> reloadMessages = getConfig().getStringList("Command.reload");
                if (!reloadMessages.isEmpty()) {
                    for (String msg : reloadMessages) {
                        // 替换占位符
                        String formattedMsg = msg.replace("%chat_format%", String.valueOf(chatFormats.size()))
                                                 .replace("%color_config%", String.valueOf(variableColors.size()));
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', formattedMsg));
                    }
                } else {
                    // 默认输出
                    sender.sendMessage(ChatColor.GREEN + "配置已重新加载!");
                    sender.sendMessage(ChatColor.AQUA + "已加载 " + chatFormats.size() + " 个聊天格式");
                    sender.sendMessage(ChatColor.AQUA + "已加载 " + variableColors.size() + " 个变量颜色配置");
                }
            } else {
                // 从配置读取未知子命令提示
                String unknownSubCmdMsg = getConfig().getString("Message.UnknownSubCmd");
                if (unknownSubCmdMsg != null && !unknownSubCmdMsg.isEmpty()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', unknownSubCmdMsg));
                } else {
                    sender.sendMessage(ChatColor.RED + "未知的子命令: " + args[0]);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 打开称号仓库第一页界面
     */
    private void openTitleShopPage1(Player player) {
        // 从配置读取界面标题
        String pageTitle = guiConfig.getString("Page1.name", "&a称号仓库 第1页");
        Inventory shop = Bukkit.createInventory(null, 54, ChatColor.translateAlternateColorCodes('&', pageTitle));
        
        // 填充黑色玻璃板边框
        ItemStack blackGlass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta blackMeta = blackGlass.getItemMeta();
        if (blackMeta != null) {
            blackMeta.setDisplayName(" ");
            blackGlass.setItemMeta(blackMeta);
        }
        
        // 第1行（0-8）和第6行（45-53）
        for (int i = 0; i < 9; i++) {
            shop.setItem(i, blackGlass);
            shop.setItem(i + 45, blackGlass);
        }
        // 两侧（第2-5行的第1格和第9格）
        for (int row = 1; row < 5; row++) {
            shop.setItem(row * 9, blackGlass); // 左侧
            shop.setItem(row * 9 + 8, blackGlass); // 右侧
        }
        
        // 称号槽位定义（28个格子：第2-5行的中间7格）
        int[] titleSlots = {
            10, 11, 12, 13, 14, 15, 16,  // 第2行
            19, 20, 21, 22, 23, 24, 25,  // 第3行
            28, 29, 30, 31, 32, 33, 34,  // 第4行
            37, 38, 39, 40, 41, 42, 43   // 第5行
        };
        
        // 第6行第5格（索引49）- 根据称号数量决定是否显示下一页按钮
        // 称号槽位总数为 28 个
        // 获取玩家拥有的称号
        List<String> ownedTitles = getPlayerOwnedTitles(player);
        String currentTitle = playerCurrentTitle.get(player.getUniqueId().toString());
        
        // 如果称号数量少于槽位数，显示黑色玻璃板；否则显示下一页按钮
        if (ownedTitles.size() < titleSlots.length) {
            // 称号没满，显示黑色玻璃板
            shop.setItem(49, blackGlass);
        } else {
            // 称号满了，显示品红色玻璃板 - 下一页
            ItemStack magentaGlass = new ItemStack(Material.MAGENTA_STAINED_GLASS_PANE);
            ItemMeta magentaMeta = magentaGlass.getItemMeta();
            if (magentaMeta != null) {
                // 从配置读取下一页按钮名称
                String nextName = guiConfig.getString("Page1.Next.name", "&c下一页");
                magentaMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nextName));
                
                // 从配置读取 Lore
                List<String> nextLore = guiConfig.getStringList("Page1.Next.Lore");
                if (!nextLore.isEmpty()) {
                    List<String> translatedLore = new ArrayList<>();
                    for (String lore : nextLore) {
                        translatedLore.add(ChatColor.translateAlternateColorCodes('&', lore));
                    }
                    magentaMeta.setLore(translatedLore);
                }
                
                magentaGlass.setItemMeta(magentaMeta);
            }
            shop.setItem(49, magentaGlass);
        }
        
        // 填充称号物品
        for (int i = 0; i < Math.min(ownedTitles.size(), titleSlots.length); i++) {
            String titleId = ownedTitles.get(i);
            String titleName = playerTitles.get(titleId);
            
            if (titleName != null) {
                ItemStack nameTag = new ItemStack(Material.NAME_TAG);
                ItemMeta meta = nameTag.getItemMeta();
                
                if (meta != null) {
                    // 处理颜色变量
                    String displayName = processColorVariables(titleName);
                    // 先转换传统颜色代码，再添加 16 进制颜色
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                    
                    // Lore - 从配置读取
                    if (titleId.equals(currentTitle)) {
                        // 已穿戴状态
                        List<String> onLore = guiConfig.getStringList("Page1.OnPlayerTitle.Lore");
                        if (!onLore.isEmpty()) {
                            List<String> translatedLore = new ArrayList<>();
                            for (String lore : onLore) {
                                // 替换 %ChatPrefix% 变量
                                String processedLore = lore.replace("%ChatPrefix%", displayName);
                                translatedLore.add(ChatColor.translateAlternateColorCodes('&', processedLore));
                            }
                            meta.setLore(translatedLore);
                        } else {
                            // 默认 Lore
                            meta.setLore(List.of(ChatColor.YELLOW + "当前穿戴该称号"));
                        }
                        
                        // 添加附魔特效
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    } else {
                        // 未穿戴状态
                        List<String> offLore = guiConfig.getStringList("Page1.OffPlayerTitle.Lore");
                        if (!offLore.isEmpty()) {
                            List<String> translatedLore = new ArrayList<>();
                            for (String lore : offLore) {
                                // 替换 %ChatPrefix% 变量
                                String processedLore = lore.replace("%ChatPrefix%", displayName);
                                translatedLore.add(ChatColor.translateAlternateColorCodes('&', processedLore));
                            }
                            meta.setLore(translatedLore);
                        } else {
                            // 默认 Lore
                            meta.setLore(List.of(ChatColor.GREEN + "点击穿戴该称号"));
                        }
                    }
                }
                
                nameTag.setItemMeta(meta);
                shop.setItem(titleSlots[i], nameTag);
            }
        }
        
        player.openInventory(shop);
    }
    
    /**
     * 打开称号仓库第二页界面
     */
    private void openTitleShopPage2(Player player) {
        // 从配置读取界面标题
        String pageTitle = guiConfig.getString("Page2.name", "&a称号仓库 第2页");
        Inventory shop = Bukkit.createInventory(null, 54, ChatColor.translateAlternateColorCodes('&', pageTitle));
        
        // 填充黑色玻璃板边框
        ItemStack blackGlass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta blackMeta = blackGlass.getItemMeta();
        if (blackMeta != null) {
            blackMeta.setDisplayName(" ");
            blackGlass.setItemMeta(blackMeta);
        }
        
        // 第1行（0-8）和第6行（45-53）
        for (int i = 0; i < 9; i++) {
            shop.setItem(i, blackGlass);
            shop.setItem(i + 45, blackGlass);
        }
        // 两侧（第2-5行的第1格和第9格）
        for (int row = 1; row < 5; row++) {
            shop.setItem(row * 9, blackGlass); // 左侧
            shop.setItem(row * 9 + 8, blackGlass); // 右侧
        }
        
        // 称号槽位定义（28个格子：第2-5行的中间7格）
        int[] titleSlots = {
            10, 11, 12, 13, 14, 15, 16,  // 第2行
            19, 20, 21, 22, 23, 24, 25,  // 第3行
            28, 29, 30, 31, 32, 33, 34,  // 第4行
            37, 38, 39, 40, 41, 42, 43   // 第5行
        };
        
        // 第6行第5格（索引49）- 第二页始终显示上一页按钮
        ItemStack cyanGlass = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta cyanMeta = cyanGlass.getItemMeta();
        if (cyanMeta != null) {
            // 从配置读取上一页按钮名称
            String backName = guiConfig.getString("Page2.Back.name", "&e上一页");
            cyanMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', backName));
            
            // 从配置读取 Lore
            List<String> backLore = guiConfig.getStringList("Page2.Back.Lore");
            if (!backLore.isEmpty()) {
                List<String> translatedLore = new ArrayList<>();
                for (String lore : backLore) {
                    translatedLore.add(ChatColor.translateAlternateColorCodes('&', lore));
                }
                cyanMeta.setLore(translatedLore);
            }
            
            cyanGlass.setItemMeta(cyanMeta);
        }
        shop.setItem(49, cyanGlass);
        
        // 获取玩家拥有的称号
        List<String> ownedTitles = getPlayerOwnedTitles(player);
        String currentTitle = playerCurrentTitle.get(player.getUniqueId().toString());
        
        // 填充第二页的称号物品（跳过前28个，显示第29-56个）
        int startIndex = 28; // 从第29个称号开始
        
        // 确保有称号可以显示
        if (ownedTitles.size() <= startIndex) {
            player.openInventory(shop);
            return;
        }
        
        // 计算第二页需要显示多少称号
        int titlesToShow = Math.min(ownedTitles.size() - startIndex, titleSlots.length);
        
        for (int i = 0; i < titlesToShow; i++) {
            String titleId = ownedTitles.get(startIndex + i);
            String titleName = playerTitles.get(titleId);
            
            if (titleName != null) {
                ItemStack nameTag = new ItemStack(Material.NAME_TAG);
                ItemMeta meta = nameTag.getItemMeta();
                
                if (meta != null) {
                    // 处理颜色变量
                    String displayName = processColorVariables(titleName);
                    // 先转换传统颜色代码，再添加 16 进制颜色
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                    
                    // Lore - 复用 Page1 的配置
                    if (titleId.equals(currentTitle)) {
                        // 已穿戴状态 - 使用 Page1 的配置
                        List<String> onLore = guiConfig.getStringList("Page1.OnPlayerTitle.Lore");
                        if (!onLore.isEmpty()) {
                            List<String> translatedLore = new ArrayList<>();
                            for (String lore : onLore) {
                                // 替换 %ChatPrefix% 变量
                                String processedLore = lore.replace("%ChatPrefix%", displayName);
                                translatedLore.add(ChatColor.translateAlternateColorCodes('&', processedLore));
                            }
                            meta.setLore(translatedLore);
                        } else {
                            // 默认 Lore
                            meta.setLore(List.of(ChatColor.YELLOW + "当前穿戴该称号"));
                        }
                        
                        // 添加附魔特效
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    } else {
                        // 未穿戴状态 - 使用 Page1 的配置
                        List<String> offLore = guiConfig.getStringList("Page1.OffPlayerTitle.Lore");
                        if (!offLore.isEmpty()) {
                            List<String> translatedLore = new ArrayList<>();
                            for (String lore : offLore) {
                                // 替换 %ChatPrefix% 变量
                                String processedLore = lore.replace("%ChatPrefix%", displayName);
                                translatedLore.add(ChatColor.translateAlternateColorCodes('&', processedLore));
                            }
                            meta.setLore(translatedLore);
                        } else {
                            // 默认 Lore
                            meta.setLore(List.of(ChatColor.GREEN + "点击穿戴该称号"));
                        }
                    }
                }
                
                nameTag.setItemMeta(meta);
                shop.setItem(titleSlots[i], nameTag);
            }
        }
        
        player.openInventory(shop);
    }
    
    /**
     * 获取玩家拥有的所有称号 ID
     */
    private List<String> getPlayerOwnedTitles(Player player) {
        List<String> ownedTitles = new ArrayList<>();
        
        // 先将所有称号 ID 按数字排序
        List<String> sortedIds = new ArrayList<>(playerTitles.keySet());
        sortedIds.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return 0;
            }
        });
        
        // 按排序后的顺序检查权限
        for (String id : sortedIds) {
            String permission = "xlr.chat.playertitle." + id;
            if (player.hasPermission(permission)) {
                ownedTitles.add(id);
            }
        }
        
        return ownedTitles;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String invTitle = event.getView().getTitle();
        
        // 从配置读取界面标题
        String page1Title = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("Page1.name", "&a称号仓库 第1页"));
        String page2Title = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("Page2.name", "&a称号仓库 第2页"));
        
        // 检查是否是称号仓库界面（第1页或第2页）
        if (!invTitle.equals(page1Title) && !invTitle.equals(page2Title)) return;
        
        event.setCancelled(true);
        
        // 点击的是第1页的下一页按钮（品红色玻璃板）
        if (invTitle.equals(page1Title) && event.getSlot() == 49) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.MAGENTA_STAINED_GLASS_PANE) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(this, () -> openTitleShopPage2(player), 2L);
                return;
            }
        }
        
        // 点击的是第2页的上一页按钮（青色玻璃板）
        if (invTitle.equals(page2Title) && event.getSlot() == 49) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.CYAN_STAINED_GLASS_PANE) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(this, () -> openTitleShopPage1(player), 2L);
                return;
            }
        }
        
        // 点击的是称号物品
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.NAME_TAG) return;
        
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        
        String displayName = meta.getDisplayName();
        
        // 根据当前页面和点击的槽位找到对应的称号 ID
        String selectedTitleId = null;
        List<String> ownedTitles = getPlayerOwnedTitles(player);
        int[] titleSlots = {
            10, 11, 12, 13, 14, 15, 16,  // 第2行
            19, 20, 21, 22, 23, 24, 25,  // 第3行
            28, 29, 30, 31, 32, 33, 34,  // 第4行
            37, 38, 39, 40, 41, 42, 43   // 第5行
        };
        
        // 查找点击的槽位对应的称号
        for (int i = 0; i < titleSlots.length; i++) {
            if (titleSlots[i] == event.getSlot()) {
                // 找到了对应的槽位
                if (invTitle.equals(page1Title)) {
                    // 第1页：直接索引
                    if (i < ownedTitles.size()) {
                        selectedTitleId = ownedTitles.get(i);
                    }
                } else {
                    // 第2页：从第29个开始
                    int actualIndex = 28 + i;
                    if (actualIndex < ownedTitles.size()) {
                        selectedTitleId = ownedTitles.get(actualIndex);
                    }
                }
                break;
            }
        }
        
        if (selectedTitleId != null) {
            String currentTitle = playerCurrentTitle.get(player.getUniqueId().toString());
            
            if (selectedTitleId.equals(currentTitle)) {
                // 从配置读取重复穿戴提示
                String equippedMsg = guiConfig.getString("Message.Equipped", "&e你已经穿戴着这个称号了!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', equippedMsg));
            } else {
                // 穿戴新称号
                playerCurrentTitle.put(player.getUniqueId().toString(), selectedTitleId);
                
                // 从配置读取穿戴成功提示，替换 %ChatPrefix%
                String equipDoneMsg = guiConfig.getString("Message.EquipDone", "&a成功穿戴称号: %ChatPrefix%");
                String processedMsg = equipDoneMsg.replace("%ChatPrefix%", displayName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', processedMsg));
                
                // 根据当前页面重新打开界面
                player.closeInventory();
                if (invTitle.equals(ChatColor.translateAlternateColorCodes('&', guiConfig.getString("Page1.name", "&a称号仓库 第1页")))) {
                    Bukkit.getScheduler().runTaskLater(this, () -> openTitleShopPage1(player), 2L);
                } else {
                    Bukkit.getScheduler().runTaskLater(this, () -> openTitleShopPage2(player), 2L);
                }
            }
        }
    }

    private void reloadPluginConfig() {
        // 重新加载配置文件
        reloadConfig();
        
        // 重新加载 GUI 配置
        loadGuiConfig();

        // 重新加载聊天格式
        loadChatFormats();

        // 重新加载变量颜色配置
        loadVariableColors();
        
        // 重新加载玩家称号配置
        loadPlayerTitles();
    }
    
    /**
     * 加载 GUI 配置文件
     */
    private void loadGuiConfig() {
        File guiFile = new File(getDataFolder(), "Gui.yml");
        if (!guiFile.exists()) {
            saveResource("Gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
    }

    private void loadChatFormats() {
        chatFormats.clear();

        // 从 Chat 配置段加载（原 Message 改名为 Chat）
        ConfigurationSection chatSection = getConfig().getConfigurationSection("Chat");
        if (chatSection != null) {
            for (String key : chatSection.getKeys(false)) {
                String format = chatSection.getString(key);
                if (format != null && !format.isEmpty()) {
                    chatFormats.put(key, format);
                }
            }
        }
    }

    private void loadVariableColors() {
        variableColors.clear();

        ConfigurationSection variableSection = getConfig().getConfigurationSection("Variable");
        if (variableSection != null) {
            for (String variable : variableSection.getKeys(false)) {
                String colorConfig = variableSection.getString(variable);
                if (colorConfig != null && !colorConfig.isEmpty()) {
                    // 自动添加 % 包裹，使 color1 变成 %color1%
                    String formattedVariable = "%" + variable + "%";
                    variableColors.put(formattedVariable, colorConfig);
                }
            }
        }
    }

    private void loadPlayerTitles() {
        playerTitles.clear();

        ConfigurationSection titleSection = getConfig().getConfigurationSection("PlayerTitle");
        if (titleSection != null) {
            for (String id : titleSection.getKeys(false)) {
                String title = titleSection.getString(id);
                if (title != null && !title.isEmpty()) {
                    playerTitles.put(id, title);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 初始化玩家的当前称号（使用与 getPlayerTitle 相同的逻辑，获取最高等级称号）
        String defaultTitleId = getHighestTitleId(player);
        if (defaultTitleId != null) {
            playerCurrentTitle.put(player.getUniqueId().toString(), defaultTitleId);
        }
    }
    
    /**
     * 获取玩家的最高等级称号 ID（与 getPlayerTitle 逻辑一致）
     */
    private String getHighestTitleId(Player player) {
        // 从最高 ID 开始检查，找到玩家拥有的第一个称号
        List<String> sortedIds = new ArrayList<>(playerTitles.keySet());
        sortedIds.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(b), Integer.parseInt(a)); // 降序排序
            } catch (NumberFormatException e) {
                return 0;
            }
        });
        
        for (String id : sortedIds) {
            String permission = "xlr.chat.playertitle." + id;
            if (player.hasPermission(permission)) {
                return id;
            }
        }
        
        return null;
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // 处理 [item] 占位符
        boolean displayInHand = getConfig().getBoolean("DisplayInHand", true);
        BaseComponent[] itemComponents = null;
        int itemPosition = -1;
        
        if (displayInHand && message.contains("[item]")) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            // 创建带有悬浮提示的物品组件
            itemComponents = shanItem.createItemHoverComponent(itemInHand);
            itemPosition = message.indexOf("[item]");
            message = message.replace("[item]", "__ITEM_PLACEHOLDER__");
        }
        
        String matchedFormat = null;

        // 从上往下遍历所有聊天格式，找到玩家有权限的第一个格式
        // LinkedHashMap 保持插入顺序，直接遍历即可（从配置文件顶部开始）
        for (Map.Entry<String, String> entry : chatFormats.entrySet()) {
            // 权限格式：xlr.chat.配置文件中的键名（如 xlr.chat.default）
            String permission = "xlr.chat." + entry.getKey();

            if (player.hasPermission(permission)) {
                matchedFormat = entry.getValue();
                break; // 找到匹配的格式后立即退出
            }
        }

        // 如果没有找到任何匹配的格式，使用 default 格式（如果存在）
        if (matchedFormat == null && chatFormats.containsKey("default")) {
            matchedFormat = chatFormats.get("default");
        }

        // 如果找到了格式，处理并广播消息
        if (matchedFormat != null) {
            String format = matchedFormat;

            // 先提取颜色变量（在替换其他占位符之前）
            String colorVariable = extractColorVariable(format);
            String colorConfig = null;
            
            if (colorVariable != null && variableColors.containsKey(colorVariable)) {
                colorConfig = variableColors.get(colorVariable);
                // 从格式中移除颜色变量占位符
                format = format.replace(colorVariable, "");
            }

            // 再替换玩家称号（%ChatPrefix%）- 称号已经包含完整的颜色代码
            String playerTitle = getPlayerTitle(player);
            // 在称号后面添加重置符，防止后面的颜色代码影响称号
            format = format.replace("%ChatPrefix%", playerTitle + "§r");

            // 再替换玩家名称
            format = format.replace("%player%", player.getDisplayName());

            // 处理消息内容
            String result;
            if (colorConfig != null && itemComponents != null && itemPosition >= 0) {
                // 有渐变颜色且有物品：分别处理物品前后的文本，保持渐变连续性
                String beforeItem = message.substring(0, itemPosition);
                String afterItem = message.substring(itemPosition + 20); // "__ITEM_PLACEHOLDER__".length() = 20
                
                // 计算总可见字符数（不包括物品）
                int totalVisibleChars = beforeItem.length() + afterItem.length();
                
                // 分别对前后文本应用渐变，但使用连续的渐变进度
                String beforeColored = applyGradientWithRange(beforeItem, colorConfig, 0, beforeItem.length(), totalVisibleChars);
                String afterColored = applyGradientWithRange(afterItem, colorConfig, beforeItem.length(), afterItem.length(), totalVisibleChars);
                
                // 使用 BaseComponent 拼接消息
                List<BaseComponent> componentList = new ArrayList<>();
                
                if (!beforeColored.isEmpty()) {
                    componentList.add(new TextComponent(beforeColored));
                }
                
                componentList.add(itemComponents[0]);
                
                if (!afterColored.isEmpty()) {
                    componentList.add(new TextComponent(afterColored));
                }
                
                BaseComponent[] messageComponents = componentList.toArray(new BaseComponent[0]);
                
                TextComponent fullMessage = new TextComponent(format.replace("%chat%", ""));
                for (BaseComponent comp : messageComponents) {
                    fullMessage.addExtra(comp);
                }
                
                event.setCancelled(true);
                
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.spigot().sendMessage(fullMessage);
                }
                return;
            } else if (colorConfig != null) {
                // 有渐变颜色但没有物品：正常处理
                String coloredMessage = applyGradientColor(message, colorConfig);
                
                // 使用 indexOf 和 substring 来替换 %chat%，避免 replace 方法的潜在问题
                int chatIndex = format.indexOf("%chat%");
                if (chatIndex != -1) {
                    result = format.substring(0, chatIndex) + coloredMessage + format.substring(chatIndex + 6);
                } else {
                    result = format; // 如果没有找到 %chat%，直接使用 format
                }
            } else {
                // 没有渐变颜色，直接替换
                if (itemComponents != null) {
                    // 有物品但没有渐变颜色
                    String beforeItem = message.substring(0, itemPosition);
                    String afterItem = message.substring(itemPosition + 20); // "__ITEM_PLACEHOLDER__".length() = 20
                    
                    // 使用 BaseComponent 拼接
                    List<BaseComponent> componentList = new ArrayList<>();
                    
                    if (!beforeItem.isEmpty()) {
                        componentList.add(new TextComponent(ChatColor.translateAlternateColorCodes('&', beforeItem)));
                    }
                    
                    componentList.add(itemComponents[0]);
                    
                    if (!afterItem.isEmpty()) {
                        componentList.add(new TextComponent(ChatColor.translateAlternateColorCodes('&', afterItem)));
                    }
                    
                    BaseComponent[] messageComponents = componentList.toArray(new BaseComponent[0]);
                    
                    TextComponent fullMessage = new TextComponent(format.replace("%chat%", ""));
                    for (BaseComponent comp : messageComponents) {
                        fullMessage.addExtra(comp);
                    }
                    
                    // 取消默认聊天事件
                    event.setCancelled(true);
                    
                    // 广播消息
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        onlinePlayer.spigot().sendMessage(fullMessage);
                    }
                    return;
                } else {
                    // 使用 indexOf 和 substring 来替换 %chat%
                    int chatIndex = format.indexOf("%chat%");
                    if (chatIndex != -1) {
                        result = format.substring(0, chatIndex) + message + format.substring(chatIndex + 6);
                    } else {
                        result = format;
                    }
                }
            }

            // 取消默认聊天事件
            event.setCancelled(true);
            
            // 直接广播格式化后的消息
            String finalMessage = ChatColor.translateAlternateColorCodes('&', result);
            
            // 使用 BaseComponent 广播消息
            TextComponent messageComponent = new TextComponent(finalMessage);
            Bukkit.spigot().broadcast(messageComponent);
        }
    }

    private String extractColorVariable(String format) {
        // 查找%chat%之前的颜色变量（%message% 已改为 %chat%）
        int chatIndex = format.indexOf("%chat%");

        if (chatIndex == -1) {
            return null;
        }

        // 查找%chat%之前的最后一个%...%
        String beforeChat = format.substring(0, chatIndex);

        // 从后往前查找，找到最后一个完整的%...%变量
        int lastEndPercent = beforeChat.lastIndexOf("%");
        if (lastEndPercent == -1) {
            return null;
        }

        // 从lastEndPercent往前找配对的开始%
        int lastStartPercent = beforeChat.lastIndexOf("%", lastEndPercent - 1);
        if (lastStartPercent == -1) {
            return null;
        }

        String variable = beforeChat.substring(lastStartPercent, lastEndPercent + 1);

        // 检查是否是颜色变量（在Variable中定义且不是%player%或%chat%）
        if (variableColors.containsKey(variable) &&
            !variable.equals("%player%") &&
            !variable.equals("%chat%")) {
            return variable;
        }

        return null;
    }
    
    /**
     * 将 RGB 颜色转换为 Minecraft 16 进制颜色代码格式
     * @param r 红色值 (0-255)
     * @param g 绿色值 (0-255)
     * @param b 蓝色值 (0-255)
     * @return 16 进制颜色代码字符串（§x§R§R§G§G§B§B 格式）
     */
    private String toHexString(int r, int g, int b) {
        return String.format("§x§%c§%c§%c§%c§%c§%c",
            toUpperHexDigit(r >> 4),
            toUpperHexDigit(r),
            toUpperHexDigit(g >> 4),
            toUpperHexDigit(g),
            toUpperHexDigit(b >> 4),
            toUpperHexDigit(b));
    }
    
    private String applyGradientColor(String text, String colorConfig) {
        // 解析颜色配置，支持格式：#起始色-#结束色
        if (colorConfig.contains("-")) {
            String[] colors = colorConfig.split("-");
            if (colors.length == 2) {
                String startColor = colors[0].replace("#", "");
                String endColor = colors[1].replace("#", "");
                return applyGradient(text, startColor, endColor);
            }
        }
        // 如果是单一颜色
        else if (colorConfig.startsWith("#")) {
            String hexColor = colorConfig.replace("#", "");
            int r = Integer.parseInt(hexColor.substring(0, 2), 16);
            int g = Integer.parseInt(hexColor.substring(2, 4), 16);
            int b = Integer.parseInt(hexColor.substring(4, 6), 16);
            return toHexString(r, g, b) + text;
        }

        return text;
    }
    
    /**
     * 计算字符串中的可见字符数（跳过所有颜色代码）
     * @param text 输入文本
     * @return 可见字符数
     */
    private int countVisibleCharacters(String text) {
        int visibleLength = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 跳过 § 颜色代码
            if (c == '§' && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                // 检查是否是 16 进制颜色代码 §x§R§R§G§G§B§B
                if (nextChar == 'x' || nextChar == 'X') {
                    // 16 进制颜色代码占 14 个字符（§x§R§R§G§G§B§B）
                    i += 13; // 跳过剩余的 13 个字符
                    continue;
                }
                // 其他 § 颜色代码占 2 个字符
                i++; // 跳过下一个字符
                continue;
            }
            // 跳过 & 颜色代码
            if (c == '&' && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                if ((nextChar >= '0' && nextChar <= '9') || 
                    (nextChar >= 'a' && nextChar <= 'f') ||
                    (nextChar >= 'A' && nextChar <= 'F') ||
                    nextChar == 'r' || nextChar == 'R' ||
                    nextChar == 'k' || nextChar == 'K' ||
                    nextChar == 'l' || nextChar == 'L' ||
                    nextChar == 'm' || nextChar == 'M' ||
                    nextChar == 'n' || nextChar == 'N' ||
                    nextChar == 'o' || nextChar == 'O') {
                    i++; // 跳过下一个字符
                    continue;
                }
            }
            visibleLength++;
        }
        
        return visibleLength;
    }
    
    private String applyGradientWithRange(String text, String colorConfig, int rangeStart, int textLength, int totalLength) {
        if (text.isEmpty() || totalLength == 0) {
            return text;
        }
        
        // 解析颜色配置
        if (!colorConfig.contains("-")) {
            // 单一颜色，直接应用
            if (colorConfig.startsWith("#")) {
                String hexColor = colorConfig.replace("#", "");
                int r = Integer.parseInt(hexColor.substring(0, 2), 16);
                int g = Integer.parseInt(hexColor.substring(2, 4), 16);
                int b = Integer.parseInt(hexColor.substring(4, 6), 16);
                return toHexString(r, g, b) + text;
            }
            return text;
        }
        
        String[] colors = colorConfig.split("-");
        if (colors.length != 2) {
            return text;
        }
        
        String startHex = colors[0].replace("#", "");
        String endHex = colors[1].replace("#", "");
        
        // 解析起始和结束颜色
        java.awt.Color startColor = parseHexColor(startHex);
        java.awt.Color endColor = parseHexColor(endHex);
        
        StringBuilder result = new StringBuilder();
        int charIndex = 0; // 当前文本中的可见字符索引
        int visibleLength = countVisibleCharacters(text); // 计算可见字符总数
        
        // 防止除零错误
        if (visibleLength == 0) {
            return text;
        }
        
        // 为每个可见字符应用渐变色
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // 跳过 § 颜色代码
            if (c == '§' && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                // 检查是否是 16 进制颜色代码 §x§R§R§G§G§B§B
                if (nextChar == 'x' || nextChar == 'X') {
                    // 16 进制颜色代码占 14 个字符（§x§R§R§G§G§B§B）
                    // 保留完整的 16 进制颜色代码
                    int endIndex = Math.min(i + 14, text.length());
                    for (int j = i; j < endIndex; j++) {
                        result.append(text.charAt(j));
                    }
                    i += 13; // 跳过剩余的 13 个字符
                    continue;
                }
                // 其他 § 颜色代码，保留原有颜色代码，不添加渐变
                result.append(c).append(text.charAt(i + 1));
                i++; // 跳过下一个字符
                continue;
            }
            
            // 跳过 & 颜色代码
            if (c == '&' && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                if ((nextChar >= '0' && nextChar <= '9') || 
                    (nextChar >= 'a' && nextChar <= 'f') ||
                    (nextChar >= 'A' && nextChar <= 'F') ||
                    nextChar == 'r' || nextChar == 'R' ||
                    nextChar == 'k' || nextChar == 'K' ||
                    nextChar == 'l' || nextChar == 'L' ||
                    nextChar == 'm' || nextChar == 'M' ||
                    nextChar == 'n' || nextChar == 'N' ||
                    nextChar == 'o' || nextChar == 'O') {
                    result.append(c).append(nextChar);
                    i++; // 跳过下一个字符
                    continue;
                }
            }
            
            // 计算在整个消息中的全局位置
            int globalIndex = rangeStart + charIndex;
            float ratio = (float) globalIndex / (totalLength - 1);
            
            // 计算渐变颜色
            int r = (int) (startColor.getRed() + (endColor.getRed() - startColor.getRed()) * ratio);
            int g = (int) (startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * ratio);
            int b = (int) (startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * ratio);

            result.append(toHexString(r, g, b)).append(c);
            charIndex++;
        }
        
        return result.toString();
    }
    
    private String applyGradient(String text, String startHex, String endHex) {
        if (text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int length = text.length();
        
        // 防止除零错误
        if (length == 1) {
            // 如果只有一个字符，直接应用起始颜色
            java.awt.Color startColor = parseHexColor(startHex);
            return toHexString(startColor.getRed(), startColor.getGreen(), startColor.getBlue()) + text;
        }

        // 解析起始和结束颜色
        java.awt.Color startColor = parseHexColor(startHex);
        java.awt.Color endColor = parseHexColor(endHex);

        // 为每个字符应用渐变色，但跳过颜色代码
        int charIndex = 0; // 实际可见字符的索引
        int visibleLength = countVisibleCharacters(text); // 计算可见字符总数
        
        // 防止除零错误
        if (visibleLength == 0) {
            return text;
        }
        
        // 为每个可见字符应用渐变色
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // 跳过 § 颜色代码
            if (c == '§' && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                // 检查是否是 16 进制颜色代码 §x§R§R§G§G§B§B
                if (nextChar == 'x' || nextChar == 'X') {
                    // 16 进制颜色代码占 14 个字符（§x§R§R§G§G§B§B）
                    // 保留完整的 16 进制颜色代码
                    int endIndex = Math.min(i + 14, text.length());
                    for (int j = i; j < endIndex; j++) {
                        result.append(text.charAt(j));
                    }
                    i += 13; // 跳过剩余的 13 个字符
                    continue;
                }
                // 其他 § 颜色代码，保留原有颜色代码，不添加渐变
                result.append(c).append(text.charAt(i + 1));
                i++; // 跳过下一个字符
                continue;
            }
            
            // 跳过 & 颜色代码
            if (c == '&' && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                if ((nextChar >= '0' && nextChar <= '9') || 
                    (nextChar >= 'a' && nextChar <= 'f') ||
                    (nextChar >= 'A' && nextChar <= 'F') ||
                    nextChar == 'r' || nextChar == 'R' ||
                    nextChar == 'k' || nextChar == 'K' ||
                    nextChar == 'l' || nextChar == 'L' ||
                    nextChar == 'm' || nextChar == 'M' ||
                    nextChar == 'n' || nextChar == 'N' ||
                    nextChar == 'o' || nextChar == 'O') {
                    // 保留原有颜色代码，不添加渐变
                    result.append(c).append(nextChar);
                    i++; // 跳过下一个字符
                    continue;
                }
            }
            
            // 为可见字符应用渐变
            float ratio = (float) charIndex / (visibleLength - 1);
            
            int r = (int) (startColor.getRed() + (endColor.getRed() - startColor.getRed()) * ratio);
            int g = (int) (startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * ratio);
            int b = (int) (startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * ratio);

            // 手动生成 §x§R§R§G§G§B§B 格式的 16 进制颜色代码
            String hexColor = String.format("§x§%c§%c§%c§%c§%c§%c",
                toUpperHexDigit(r >> 4),
                toUpperHexDigit(r),
                toUpperHexDigit(g >> 4),
                toUpperHexDigit(g),
                toUpperHexDigit(b >> 4),
                toUpperHexDigit(b));

            result.append(hexColor).append(c);
            charIndex++;
        }

        return result.toString();
    }

    private java.awt.Color parseHexColor(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new java.awt.Color(r, g, b);
    }

    /**
     * 将数字转换为单个大写16进制字符（用于Minecraft颜色代码）
     */
    private char toUpperHexDigit(int value) {
        char c = Character.forDigit(value & 0xF, 16);
        return Character.toUpperCase(c);
    }

    private String getPlayerTitle(Player player) {
        String playerId = player.getUniqueId().toString();
        
        // 优先检查玩家当前穿戴的称号
        String currentTitleId = playerCurrentTitle.get(playerId);
        if (currentTitleId != null && playerTitles.containsKey(currentTitleId)) {
            String title = playerTitles.get(currentTitleId);
            // 先处理颜色变量（如 %color2%）- 应用渐变效果
            title = processColorVariables(title);
            // 再转换传统颜色代码（& -> §）
            title = ChatColor.translateAlternateColorCodes('&', title);
            return title;
        }
        
        // 如果没有穿戴称号，从最高 ID (56) 开始检查，找到玩家拥有的最高权限称号
        List<String> sortedIds = new ArrayList<>(playerTitles.keySet());
        sortedIds.sort((a, b) -> {
            try {
                int idA = Integer.parseInt(a);
                int idB = Integer.parseInt(b);
                return Integer.compare(idB, idA); // 降序排序
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        // 遍历排序后的 ID，找到玩家拥有的第一个称号
        for (String id : sortedIds) {
            String permission = "xlr.chat.playertitle." + id;
            if (player.hasPermission(permission)) {
                String title = playerTitles.get(id);
                // 先处理颜色变量（如 %color2%）- 应用渐变效果
                title = processColorVariables(title);
                // 再转换传统颜色代码（& -> §）
                title = ChatColor.translateAlternateColorCodes('&', title);
                return title;
            }
        }

        // 默认称号（ID 1）
        String defaultTitle = playerTitles.get("1");
        if (defaultTitle != null) {
            // 先处理颜色变量
            defaultTitle = processColorVariables(defaultTitle);
            // 再转换传统颜色代码
            defaultTitle = ChatColor.translateAlternateColorCodes('&', defaultTitle);
            return defaultTitle;
        }
        
        return "";
    }

    /**
     * 处理字符串中的颜色变量（如 %color1%、%color2%）
     * 称号等短文本使用起始颜色，与 GUI 保持一致
     */
    private String processColorVariables(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 替换所有已定义的颜色变量
        for (Map.Entry<String, String> entry : variableColors.entrySet()) {
            String variable = entry.getKey(); // 如 %color1%
            String colorConfig = entry.getValue(); // 如 #ff9a9e

            // 如果文本中包含该变量
            if (text.contains(variable)) {
                // 如果是渐变色 - 称号等短文本使用起始颜色，与 GUI 保持一致
                if (colorConfig.contains("-")) {
                    String[] colors = colorConfig.split("-");
                    if (colors.length >= 2) {
                        String startColor = colors[0].replace("#", "");
                        
                        // 称号等短文本使用起始颜色，不应用逐字符渐变
                        int r = Integer.parseInt(startColor.substring(0, 2), 16);
                        int g = Integer.parseInt(startColor.substring(2, 4), 16);
                        int b = Integer.parseInt(startColor.substring(4, 6), 16);
                        text = text.replace(variable, toHexString(r, g, b));
                    }
                }
                // 如果是单一颜色
                else if (colorConfig.startsWith("#")) {
                    String hexColor = colorConfig.replace("#", "");
                    int r = Integer.parseInt(hexColor.substring(0, 2), 16);
                    int g = Integer.parseInt(hexColor.substring(2, 4), 16);
                    int b = Integer.parseInt(hexColor.substring(4, 6), 16);
                    text = text.replace(variable, toHexString(r, g, b));
                }
            }
        }

        return text;
    }
}
