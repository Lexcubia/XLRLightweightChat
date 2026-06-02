package xlingran;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HopperCommand implements CommandExecutor {

    private final Gui gui;

    public HopperCommand(Gui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("mode")) {
            if (!player.hasPermission("xlrhopper.create.mode")) {
                player.sendMessage("§c你没有权限创建模板");
                return true;
            }
            if (args.length < 3) {
                player.sendMessage("§c用法: /xlrhopper create mode <名称>");
                return true;
            }
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) {
                    nameBuilder.append(' ');
                }
                nameBuilder.append(args[i]);
            }
            String name = nameBuilder.toString().trim();
            Shan plugin = Shan.getInstance();
            if (!plugin.getTemplateManager().createTemplate(player, name)) {
                player.sendMessage("§c模板已存在或名称无效");
                return true;
            }
            plugin.getGui().saveData();
            gui.openTemplateSettings(player, name);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("mode")) {
            if (!player.hasPermission("xlrhopper.mode")) {
                player.sendMessage("§c你没有权限打开模板列表");
                return true;
            }
            gui.openTemplateList(player);
            return true;
        }

        player.sendMessage("§c用法: /xlrhopper <mode|create mode <名称>>");
        return true;
    }
}
