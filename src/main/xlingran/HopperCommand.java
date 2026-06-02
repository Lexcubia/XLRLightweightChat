package xlingran;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HopperCommand implements CommandExecutor, TabCompleter {

    private final Gui gui;

    public HopperCommand(Gui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("mode")) {
            if (!player.hasPermission("xlrhopper.create.mode")) {
                player.sendMessage(ChatColor.RED + "你没有权限创建模板");
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "用法: /xlrhopper create mode <名称>");
                return true;
            }
            String name = joinArgs(args, 2);
            Shan plugin = Shan.getInstance();
            if (!plugin.getTemplateManager().createTemplate(player, name)) {
                player.sendMessage(ChatColor.RED + "模板已存在或名称无效");
                return true;
            }
            plugin.getGui().saveData();
            gui.openTemplateSettings(player, name);
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("edit") && args[1].equalsIgnoreCase("mode")) {
            if (!player.hasPermission("xlrhopper.edit.mode")) {
                player.sendMessage(ChatColor.RED + "你没有权限编辑模板");
                return true;
            }
            String name = joinArgs(args, 2);
            HopperTemplate template = Shan.getInstance().getTemplateManager().getTemplate(player.getUniqueId(), name);
            if (template == null) {
                player.sendMessage(ChatColor.RED + "模板不存在: " + name);
                return true;
            }
            gui.openTemplateSettings(player, name);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("mode")) {
            if (!player.hasPermission("xlrhopper.mode")) {
                player.sendMessage(ChatColor.RED + "你没有权限打开模板列表");
                return true;
            }
            gui.openTemplateList(player);
            return true;
        }

        player.sendMessage(ChatColor.RED + "用法: /xlrhopper <mode|create mode <名称>|edit mode <名称>>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (player.hasPermission("xlrhopper.mode")) {
                options.add("mode");
            }
            if (player.hasPermission("xlrhopper.create.mode")) {
                options.add("create");
            }
            if (player.hasPermission("xlrhopper.edit.mode")) {
                options.add("edit");
            }
            return filterPrefix(options, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create") && player.hasPermission("xlrhopper.create.mode")) {
                return filterPrefix(List.of("mode"), args[1]);
            }
            if (args[0].equalsIgnoreCase("edit") && player.hasPermission("xlrhopper.edit.mode")) {
                return filterPrefix(List.of("mode"), args[1]);
            }
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("edit") && args[1].equalsIgnoreCase("mode")
                && player.hasPermission("xlrhopper.edit.mode")) {
            return filterPrefix(
                    new ArrayList<>(Shan.getInstance().getTemplateManager().getTemplates(player.getUniqueId()).keySet()),
                    args[args.length - 1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return options;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                nameBuilder.append(' ');
            }
            nameBuilder.append(args[i]);
        }
        return nameBuilder.toString().trim();
    }
}
