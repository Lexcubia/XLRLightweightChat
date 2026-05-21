 package xlingran;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shan extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, String> chatFormats = new LinkedHashMap<>();
    private final Map<String, String> variableColors = new HashMap<>();
    private final Map<String, String> playerTitles = new HashMap<>();
    private final Map<String, String> playerCurrentTitle = new HashMap<>(); // 存储玩家当前穿戴的称号

    @Override
    public void onEnable() {
        // 保存默认配置文件（如果不存在）
        saveDefaultConfig();
        
        // 从配置文件读取所有聊天格式
        loadChatFormats();
        // 从配置文件读取变量颜色配置
        loadVariableColors();
        // 从配置文件读取玩家称号配置
        loadPlayerTitles();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 注册命令执行器
        getCommand("xlrchat").setExecutor(this);

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

            // 检查参数
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + "用法: /xlrchat reload/cp");
                return true;
            }

            // 处理 cp 子命令（称号仓库）
            if (args[0].equalsIgnoreCase("cp")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行!");
                    return true;
                }
                Player player = (Player) sender;
                openTitleShop(player);
                return true;
            }
            
            // 处理 reload 子命令
            if (args[0].equalsIgnoreCase("reload")) {
                reloadPluginConfig();
                
                // 从配置读取 reload 输出消息
                List<String> reloadMessages = getConfig().getStringList("Command.reload");
                if (reloadMessages != null && !reloadMessages.isEmpty()) {
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
                return true;
            } else {
                // 从配置读取未知子命令提示
                String unknownSubCmdMsg = getConfig().getString("Message.UnknownSubCmd");
                if (unknownSubCmdMsg != null && !unknownSubCmdMsg.isEmpty()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', unknownSubCmdMsg));
                } else {
                    sender.sendMessage(ChatColor.RED + "未知的子命令: " + args[0]);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 打开称号仓库界面
     */
    private void openTitleShop(Player player) {
        Inventory shop = Bukkit.createInventory(null, 54, ChatColor.GREEN + "称号仓库");
        
        // 填充黑色玻璃板边框
        ItemStack blackGlass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta blackMeta = blackGlass.getItemMeta();
        blackMeta.setDisplayName(" ");
        blackGlass.setItemMeta(blackMeta);
        
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
        
        // 第6行第5格（索引49）放置品红色玻璃板 - 下一页
        // 第6行从索引 45 开始，第5个格子是 45+4=49
        ItemStack magentaGlass = new ItemStack(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemMeta magentaMeta = magentaGlass.getItemMeta();
        magentaMeta.setDisplayName(ChatColor.RED + "下一页");
        magentaGlass.setItemMeta(magentaMeta);
        shop.setItem(49, magentaGlass);
        
        // 剩余28个格子放置称号（索引：9-17, 18-26, 27-35, 36-38, 39, 41-44）
        // 实际上应该是第2-5行的中间7格，共28格
        int[] titleSlots = {
            10, 11, 12, 13, 14, 15, 16,  // 第2行
            19, 20, 21, 22, 23, 24, 25,  // 第3行
            28, 29, 30, 31, 32, 33, 34,  // 第4行
            37, 38, 39                     // 第5行（前3格）
        };
        
        // 获取玩家拥有的称号
        List<String> ownedTitles = getPlayerOwnedTitles(player);
        String currentTitle = playerCurrentTitle.get(player.getUniqueId().toString());
        
        // 填充称号物品
        for (int i = 0; i < Math.min(ownedTitles.size(), titleSlots.length); i++) {
            String titleId = ownedTitles.get(i);
            String titleName = playerTitles.get(titleId);
            
            if (titleName != null) {
                ItemStack nameTag = new ItemStack(Material.NAME_TAG);
                ItemMeta meta = nameTag.getItemMeta();
                
                // 处理颜色变量
                String displayName = processColorVariables(titleName);
                // 先转换传统颜色代码，再添加 16 进制颜色
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                
                // Lore
                List<String> lore = new ArrayList<>();
                if (titleId.equals(currentTitle)) {
                    lore.add(ChatColor.YELLOW + "当前穿戴该称号");
                    // 添加附魔特效
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add(ChatColor.GREEN + "点击穿戴该称号");
                }
                meta.setLore(lore);
                
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
        
        for (String id : playerTitles.keySet()) {
            String permission = "xlr.chat.playertitle." + id;
            if (player.hasPermission(permission)) {
                ownedTitles.add(id);
            }
        }
        
        // 按 ID 数字排序
        ownedTitles.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return 0;
            }
        });
        
        return ownedTitles;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String invTitle = event.getView().getTitle();
        
        // 检查是否是称号仓库界面
        if (!invTitle.equals(ChatColor.GREEN + "称号仓库")) return;
        
        event.setCancelled(true);
        
        // 点击的是品红色玻璃板（下一页）
        if (event.getSlot() == 49) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "敬请期待更多称号!");
            return;
        }
        
        // 点击的是称号物品
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.NAME_TAG) return;
        
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        
        String displayName = meta.getDisplayName();
        
        // 找到对应的称号 ID
        String selectedTitleId = null;
        for (Map.Entry<String, String> entry : playerTitles.entrySet()) {
            String titleName = processColorVariables(entry.getValue());
            String coloredName = ChatColor.translateAlternateColorCodes('&', titleName);
            if (coloredName.equals(displayName)) {
                selectedTitleId = entry.getKey();
                break;
            }
        }
        
        if (selectedTitleId != null) {
            String currentTitle = playerCurrentTitle.get(player.getUniqueId().toString());
            
            if (selectedTitleId.equals(currentTitle)) {
                player.sendMessage(ChatColor.YELLOW + "你已经穿戴着这个称号了!");
            } else {
                // 穿戴新称号
                playerCurrentTitle.put(player.getUniqueId().toString(), selectedTitleId);
                player.sendMessage(ChatColor.GREEN + "成功穿戴称号: " + displayName);
                
                // 重新打开界面
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(this, () -> openTitleShop(player), 2L);
            }
        }
    }

    private void reloadPluginConfig() {
        // 重新加载配置文件
        reloadConfig();

        // 重新加载聊天格式
        loadChatFormats();

        // 重新加载变量颜色配置
        loadVariableColors();
        
        // 重新加载玩家称号配置
        loadPlayerTitles();
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
        // 初始化玩家的当前称号（使用 getPlayerTitle 获取默认称号）
        String defaultTitleId = getDefaultTitleId(player);
        if (defaultTitleId != null) {
            playerCurrentTitle.put(player.getUniqueId().toString(), defaultTitleId);
        }
    }
    
    /**
     * 获取玩家的默认称号 ID（最低权限的称号）
     */
    private String getDefaultTitleId(Player player) {
        // 从最低 ID 开始检查，找到玩家拥有的第一个称号
        List<String> sortedIds = new ArrayList<>(playerTitles.keySet());
        sortedIds.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
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
        String matchedFormat = null;

        // 从下往上遍历所有聊天格式，找到玩家有权限的第一个格式
        // 将 LinkedHashMap 转换为 ArrayList 以支持逆序遍历
        List<Map.Entry<String, String>> formatList = new ArrayList<>(chatFormats.entrySet());
        
        // 逆序遍历（从配置文件的底部开始）
        for (int i = formatList.size() - 1; i >= 0; i--) {
            Map.Entry<String, String> entry = formatList.get(i);
            // 权限格式：xlr.chat.配置文件中的键名（如 xlr.chat.svip）
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
            format = format.replace("%ChatPrefix%", playerTitle);

            // 再替换玩家名称
            format = format.replace("%player%", player.getDisplayName());

            // 处理消息内容
            String result;
            if (colorConfig != null) {
                // 应用颜色到消息
                String coloredMessage = applyGradientColor(event.getMessage(), colorConfig);
                result = format.replace("%chat%", coloredMessage);
            } else {
                result = format.replace("%chat%", event.getMessage());
            }

            // 取消默认聊天事件
            event.setCancelled(true);
            
            // 直接广播格式化后的消息
            // Spigot 1.16+ 原生支持 16 进制颜色，ChatColor.of() 返回的对象可以直接使用
            // 只需要转换 & 颜色代码即可
            // 注意：必须先转换传统颜色代码，因为 16 进制颜色已经是 §x§... 格式
            String finalMessage = ChatColor.translateAlternateColorCodes('&', result);
            
            Bukkit.broadcastMessage(finalMessage);
        }
    }

    private String processFormat(String format, String message) {
        // 查找格式中所有的颜色变量
        String colorVariable = extractColorVariable(format);

        if (colorVariable != null && variableColors.containsKey(colorVariable)) {
            // 应用颜色到消息
            String coloredMessage = applyGradientColor(message, variableColors.get(colorVariable));
            // 移除颜色变量占位符
            format = format.replace(colorVariable, "");
            // 替换消息内容（%chat% 已改为 %chat%）
            format = format.replace("%chat%", coloredMessage);
        } else {
            format = format.replace("%chat%", message);
        }

        return format;
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
            // 直接使用 ChatColor.of() 并获取其内部表示
            ChatColor color = ChatColor.of(colorConfig);
            // 使用 §x§R§R§G§G§B§B 格式
            return color.toString() + text;
        }

        return text;
    }

    private String applyGradient(String text, String startHex, String endHex) {
        if (text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int length = text.length();

        // 解析起始和结束颜色
        java.awt.Color startColor = parseHexColor(startHex);
        java.awt.Color endColor = parseHexColor(endHex);

        // 为每个字符应用渐变色
        for (int i = 0; i < length; i++) {
            float ratio = (float) i / (length - 1);
            int r = (int) (startColor.getRed() + (endColor.getRed() - startColor.getRed()) * ratio);
            int g = (int) (startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * ratio);
            int b = (int) (startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * ratio);

            String hexColor = String.format("#%02x%02x%02x", r, g, b);
            ChatColor chatColor = ChatColor.of(hexColor);

            // 直接使用 ChatColor 对象，不要使用 toString()
            result.append(chatColor).append(text.charAt(i));
        }

        return result.toString();
    }

    private java.awt.Color parseHexColor(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new java.awt.Color(r, g, b);
    }

    private String getPlayerTitle(Player player) {
        // 从最高 ID (56) 开始检查，找到玩家拥有的最高权限称号
        // 将 ID 按数字大小降序排序
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
                // 处理称号中的颜色变量（如 %color2%）
                title = processColorVariables(title);
                return ChatColor.translateAlternateColorCodes('&', title);
            }
        }

        // 默认称号（ID 1）
        String defaultTitle = playerTitles.get("1");
        if (defaultTitle != null) {
            // 处理称号中的颜色变量
            defaultTitle = processColorVariables(defaultTitle);
            return ChatColor.translateAlternateColorCodes('&', defaultTitle);
        }
        
        return "";
    }

    /**
     * 处理字符串中的颜色变量（如 %color1%、%color2%）
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
                // 如果是渐变色
                if (colorConfig.contains("-")) {
                    String[] colors = colorConfig.split("-");
                    if (colors.length == 2) {
                        String startColor = colors[0].replace("#", "");
                        String endColor = colors[1].replace("#", "");
                        // 暂时用起始颜色替换，转换为 §x 格式
                        String hexColor = "#" + startColor;
                        text = text.replace(variable, net.md_5.bungee.api.ChatColor.of(hexColor).toString());
                    }
                }
                // 如果是单一颜色
                else if (colorConfig.startsWith("#")) {
                    String hexColor = colorConfig.replace("#", "");
                    // 转换为 §x 格式的 16 进制颜色
                    text = text.replace(variable, net.md_5.bungee.api.ChatColor.of("#" + hexColor).toString());
                }
            }
        }

        return text;
    }
}
