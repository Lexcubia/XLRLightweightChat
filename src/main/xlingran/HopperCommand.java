package xlingran;

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
    private final Shan plugin;

    public HopperCommand(Gui gui, Shan plugin) {
        this.gui = gui;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xlrhopper.admin")) {
                sender.sendMessage(plugin.getMessageConfig().message("reload-no-permission"));
                return true;
            }
            plugin.reload(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageConfig().message("command-players-only"));
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("mode")) {
            if (!player.hasPermission("xlrhopper.create.mode")) {
                player.sendMessage(plugin.getMessageConfig().message("no-permission-create-mode"));
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(plugin.getMessageConfig().message("usage-create-mode"));
                return true;
            }
            String name = joinArgs(args, 2);
            Shan plugin = Shan.getInstance();
            if (!plugin.getTemplateManager().createTemplate(player, name)) {
                player.sendMessage(plugin.getMessageConfig().message("template-create-fail"));
                return true;
            }
            plugin.getGui().saveData();
            gui.openTemplateSettings(player, name);
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("edit") && args[1].equalsIgnoreCase("mode")) {
            if (!player.hasPermission("xlrhopper.edit.mode")) {
                player.sendMessage(plugin.getMessageConfig().message("no-permission-edit-mode"));
                return true;
            }
            String name = joinArgs(args, 2);
            HopperTemplate template = Shan.getInstance().getTemplateManager().getTemplate(player.getUniqueId(), name);
            if (template == null) {
                player.sendMessage(plugin.getMessageConfig().message("template-not-found",
                        java.util.Map.of("Template", name)));
                return true;
            }
            gui.openTemplateSettings(player, name);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("mode")) {
            if (!player.hasPermission("xlrhopper.mode")) {
                player.sendMessage(plugin.getMessageConfig().message("no-permission-mode"));
                return true;
            }
            gui.openTemplateList(player);
            return true;
        }

        player.sendMessage(plugin.getMessageConfig().message("usage-command"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("xlrhopper.admin")) {
                options.add("reload");
            }
            if (sender instanceof Player player) {
            if (player.hasPermission("xlrhopper.mode")) {
                options.add("mode");
            }
            if (player.hasPermission("xlrhopper.create.mode")) {
                options.add("create");
            }
            if (player.hasPermission("xlrhopper.edit.mode")) {
                options.add("edit");
            }
            }
            return filterPrefix(options, args[0]);
        }
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                if (player.hasPermission("xlrhopper.create.mode")) {
                    return filterPrefix(List.of("mode"), args[1]);
                }
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
