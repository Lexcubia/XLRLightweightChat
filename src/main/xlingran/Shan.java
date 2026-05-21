package xlingran;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shan extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, String> chatFormats = new HashMap<>();
    private final Map<String, String> variableColors = new HashMap<>();
    private final Map<String, String> playerTitles = new HashMap<>();

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
                // 从配置读取无参数提示
                String noArgsMsg = getConfig().getString("Message.NoArgs");
                if (noArgsMsg != null && !noArgsMsg.isEmpty()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noArgsMsg));
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "用法: /xlrchat reload");
                }
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
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String matchedFormat = null;

        // 遍历所有聊天格式，找到玩家有权限的第一个格式
        for (Map.Entry<String, String> entry : chatFormats.entrySet()) {
            // 权限格式：xlr.chat.配置文件中的键名（如 xlr.chat.op）
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

            // 先替换玩家称号（%ChatPrefix%）
            String playerTitle = getPlayerTitle(player);
            format = format.replace("%ChatPrefix%", playerTitle);

            // 再替换玩家名称
            format = format.replace("%player%", player.getDisplayName());

            // 处理自定义变量和消息
            String result = processFormat(format, event.getMessage());

            // 取消默认聊天事件
            event.setCancelled(true);
            
            // 直接广播格式化后的消息
            // Spigot 1.16+ 原生支持 16 进制颜色，ChatColor.of() 返回的对象可以直接使用
            // 只需要转换 & 颜色代码即可
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
        }

        // 替换消息内容（%message% 已改为 %chat%）
        if (colorVariable != null && variableColors.containsKey(colorVariable)) {
            String coloredMessage = applyGradientColor(message, variableColors.get(colorVariable));
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
            String hexColor = colorConfig.replace("#", "");
            ChatColor color = ChatColor.of("#" + hexColor);
            return color + text;
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
        // 从最高 ID 开始检查，找到玩家拥有的最高权限称号
        // 将 ID 按数字大小降序排序
        List<String> sortedIds = new ArrayList<>(playerTitles.keySet());
        sortedIds.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(b), Integer.parseInt(a));
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        // 遍历排序后的 ID，找到玩家拥有的第一个称号
        for (String id : sortedIds) {
            String permission = "xlr.chat.playertitle." + id;
            if (player.hasPermission(permission)) {
                String title = playerTitles.get(id);
                return ChatColor.translateAlternateColorCodes('&', title);
            }
        }

        // 默认称号（ID 1）
        String defaultTitle = playerTitles.get("1");
        if (defaultTitle != null) {
            return ChatColor.translateAlternateColorCodes('&', defaultTitle);
        }
        
        return "";
    }
}
