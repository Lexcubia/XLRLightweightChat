package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xlingran.gui.UpdateConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HopperCommand implements CommandExecutor, TabCompleter {

    private static final String PLAYER_PLACEHOLDER = "%player%";

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

        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            return handleGive(sender, args);
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
            HopperTemplate template = plugin.getTemplateManager().getTemplate(player.getUniqueId(), name);
            if (template == null) {
                player.sendMessage(plugin.getMessageConfig().message("template-not-found",
                        Map.of("Template", name)));
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

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xlrhopper.give")) {
            sender.sendMessage(plugin.getMessageConfig().message("give-no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageConfig().message("give-usage"));
            return true;
        }
        Player target = resolveTarget(sender, args[1]);
        if (target == null) {
            return true;
        }
        String levelId = args[2].toLowerCase(Locale.ROOT);
        UpdateConfig updateConfig = plugin.getUpdateConfig();
        if (!updateConfig.isValidLevel(levelId)) {
            sender.sendMessage(plugin.getMessageConfig().message("give-unknown-level",
                    Map.of("Level", args[2])));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessageConfig().message("give-invalid-amount"));
                return true;
            }
            if (amount < 1) {
                sender.sendMessage(plugin.getMessageConfig().message("give-invalid-amount"));
                return true;
            }
        }
        ItemStack stack = HopperLevelItems.createLevelHopper(updateConfig, plugin.getHopperKeys(), levelId, amount);
        if (stack == null) {
            sender.sendMessage(plugin.getMessageConfig().message("give-unknown-level",
                    Map.of("Level", args[2])));
            return true;
        }
        HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        for (ItemStack drop : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
        sender.sendMessage(plugin.getMessageConfig().message("give-success", Map.of(
                "Player", target.getName(),
                "Level", levelId,
                "Amount", String.valueOf(amount))));
        return true;
    }

    private Player resolveTarget(CommandSender sender, String raw) {
        if (PLAYER_PLACEHOLDER.equalsIgnoreCase(raw)) {
            if (sender instanceof Player player) {
                return player;
            }
            sender.sendMessage(plugin.getMessageConfig().message("give-console-no-player"));
            return null;
        }
        Player target = Bukkit.getPlayer(raw);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.getMessageConfig().message("give-player-not-found",
                    Map.of("Player", raw)));
            return null;
        }
        return target;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("xlrhopper.admin")) {
                options.add("reload");
            }
            if (sender.hasPermission("xlrhopper.give")) {
                options.add("give");
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
        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("xlrhopper.give")) {
            List<String> targets = new ArrayList<>();
            targets.add(PLAYER_PLACEHOLDER);
            for (Player online : Bukkit.getOnlinePlayers()) {
                targets.add(online.getName());
            }
            return filterPrefix(targets, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("xlrhopper.give")) {
            return filterPrefix(plugin.getUpdateConfig().levelIds(), args[2]);
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
                    new ArrayList<>(plugin.getTemplateManager().getTemplates(player.getUniqueId()).keySet()),
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
