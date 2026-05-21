package xlingran;

import net.md_5.bungee.api.ChatColor;
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

public class Shan extends JavaPlugin implements Listener {

    private final Map<String, String> chatFormats = new HashMap<>();
    private final Map<String, String> variableColors = new HashMap<>();

    @Override
    public void onEnable() {
        // 从配置文件读取所有聊天格式
        loadChatFormats();
        // 从配置文件读取变量颜色配置
        loadVariableColors();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("插件已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("该插件已卸载");
    }

    private void loadChatFormats() {
        chatFormats.clear();

        ConfigurationSection messageSection = getConfig().getConfigurationSection("Message");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                String format = messageSection.getString(key);
                if (format != null && !format.isEmpty()) {
                    chatFormats.put(key, format);
                    getLogger().info("已加载聊天格式: " + key + " -> " + format);
                }
            }
        }

        getLogger().info("共加载了 " + chatFormats.size() + " 个聊天格式");
    }

    private void loadVariableColors() {
        variableColors.clear();

        ConfigurationSection variableSection = getConfig().getConfigurationSection("Variable");
        if (variableSection != null) {
            for (String variable : variableSection.getKeys(false)) {
                String colorConfig = variableSection.getString(variable);
                if (colorConfig != null && !colorConfig.isEmpty()) {
                    variableColors.put(variable, colorConfig);
                    getLogger().info("已加载变量颜色: " + variable + " -> " + colorConfig);
                }
            }
        }

        getLogger().info("共加载了 " + variableColors.size() + " 个变量颜色配置");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // 遍历所有聊天格式，找到玩家有权限的第一个格式
        for (Map.Entry<String, String> entry : chatFormats.entrySet()) {
            String permission = "xlr.message." + entry.getKey();

            if (player.hasPermission(permission)) {
                String format = entry.getValue()
                    .replace("%player%", player.getDisplayName());

                // 处理消息内容
                String message = event.getMessage();
                if (variableColors.containsKey("%message%")) {
                    message = applyGradientColor(message, variableColors.get("%message%"));
                }

                format = format.replace("%message%", message);
                event.setFormat(format);
                return; // 找到匹配的格式后直接返回
            }
        }
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
