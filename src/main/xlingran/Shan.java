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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shan extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, String> chatFormats = new HashMap<>();
    private final Map<String, String> variableColors = new HashMap<>();

    @Override
    public void onEnable() {
        // 保存默认配置文件（如果不存在）
        saveDefaultConfig();
        
        // 从配置文件读取所有聊天格式
        loadChatFormats();
        // 从配置文件读取变量颜色配置
        loadVariableColors();

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
                sender.sendMessage(ChatColor.RED + "你没有权限使用此命令!");
                return true;
            }

            // 检查参数
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + "用法: /xlrchat reload");
                return true;
            }

            // 处理 reload 子命令
            if (args[0].equalsIgnoreCase("reload")) {
                reloadPluginConfig();
                sender.sendMessage(ChatColor.GREEN + "配置已重新加载!");
                sender.sendMessage(ChatColor.AQUA + "已加载 " + chatFormats.size() + " 个聊天格式");
                sender.sendMessage(ChatColor.AQUA + "已加载 " + variableColors.size() + " 个变量颜色配置");
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "未知的子命令: " + args[0]);
                sender.sendMessage(ChatColor.YELLOW + "用法: /xlrchat reload");
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
    }

    private void loadChatFormats() {
        chatFormats.clear();

        ConfigurationSection messageSection = getConfig().getConfigurationSection("Message");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                String format = messageSection.getString(key);
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
            Bukkit.getConsoleSender().sendMessage("§e[DEBUG] 找到 Variable 配置段");
            for (String variable : variableSection.getKeys(false)) {
                String colorConfig = variableSection.getString(variable);
                if (colorConfig != null && !colorConfig.isEmpty()) {
                    // 自动添加 % 包裹，使 color1 变成 %color1%
                    String formattedVariable = "%" + variable + "%";
                    variableColors.put(formattedVariable, colorConfig);
                    Bukkit.getConsoleSender().sendMessage("§e[DEBUG] 已加载: " + formattedVariable + " -> " + colorConfig);
                }
            }
            Bukkit.getConsoleSender().sendMessage("§e[DEBUG] 总共加载了 " + variableColors.size() + " 个变量颜色");
            Bukkit.getConsoleSender().sendMessage("§e[DEBUG] 所有变量键名: " + variableColors.keySet());
        } else {
            Bukkit.getConsoleSender().sendMessage("§c[DEBUG] 没有找到 Variable 配置段！");
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // 遍历所有聊天格式，找到玩家有权限的第一个格式
        for (Map.Entry<String, String> entry : chatFormats.entrySet()) {
            String permission = "xlr.message." + entry.getKey();

            if (player.hasPermission(permission)) {
                String format = entry.getValue();

                // 先替换玩家名称
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
                
                return; // 找到匹配的格式后直接返回
            }
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

        // 替换消息内容
        if (colorVariable != null && variableColors.containsKey(colorVariable)) {
            String coloredMessage = applyGradientColor(message, variableColors.get(colorVariable));
            format = format.replace("%message%", coloredMessage);
        } else {
            format = format.replace("%message%", message);
        }

        return format;
    }

    private String extractColorVariable(String format) {
        // 查找%message%之前的颜色变量
        // 例如：%messageop%%message%中的%messageop%
        int messageIndex = format.indexOf("%message%");
        Bukkit.getConsoleSender().sendMessage("§e[DEBUG] 格式: " + format);
        Bukkit.getConsoleSender().sendMessage("§e[DEBUG] %message% 位置: " + messageIndex);

        if (messageIndex == -1) {
            Bukkit.getConsoleSender().sendMessage("§c[DEBUG] 没有找到 %message%");
            return null;
        }

        // 查找%message%之前的最后一个%...%
        String beforeMessage = format.substring(0, messageIndex);
        Bukkit.getConsoleSender().sendMessage("§e[DEBUG] %message% 前的内容: " + beforeMessage);

        // 查找最后一个完整的变量
        int lastPercent = beforeMessage.lastIndexOf("%");
        Bukkit.getConsoleSender().sendMessage("§e[DEBUG] 最后一个 % 的位置: " + lastPercent);

        if (lastPercent != -1) {
            int nextPercent = beforeMessage.indexOf("%", lastPercent + 1);
            Bukkit.getConsoleSender().sendMessage("§e[DEBUG] 下一个 % 的位置: " + nextPercent);

            if (nextPercent != -1) {
                String variable = beforeMessage.substring(lastPercent, nextPercent + 1);
                Bukkit.getConsoleSender().sendMessage("§e[DEBUG] 提取的变量: " + variable);
                Bukkit.getConsoleSender().sendMessage("§e[DEBUG] variableColors 键名: " + variableColors.keySet());

                // 检查是否是颜色变量（在Variable中定义且不是%player%或%message%）
                if (variableColors.containsKey(variable) &&
                    !variable.equals("%player%") &&
                    !variable.equals("%message%")) {
                    Bukkit.getConsoleSender().sendMessage("§a[DEBUG] 成功找到颜色变量: " + variable);
                    return variable;
                } else {
                    Bukkit.getConsoleSender().sendMessage("§c[DEBUG] 变量不在 variableColors 中，或为 %player%/%message%");
                }
            }
        }

        Bukkit.getConsoleSender().sendMessage("§c[DEBUG] 没有找到颜色变量");
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
}
