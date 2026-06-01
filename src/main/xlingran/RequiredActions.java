package xlingran;

import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Required 配置：悬浮 Lore 与点击命令。 */
final class RequiredActions {

    final List<String> hoverLore = new ArrayList<>();
    final List<String> suggestCommands = new ArrayList<>();
    final List<String> runCommands = new ArrayList<>();

    boolean hasHover() {
        return !hoverLore.isEmpty();
    }

    static RequiredActions fromConfigList(List<?> raw) {
        RequiredActions actions = new RequiredActions();
        if (raw == null) {
            return actions;
        }
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    String value = String.valueOf(entry.getValue());
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (key.equalsIgnoreCase("Command")) {
                        actions.suggestCommands.add(value);
                    } else if (key.equalsIgnoreCase("RunCommand")) {
                        actions.runCommands.add(value);
                    }
                }
            } else {
                String line = String.valueOf(item);
                if (line.startsWith("Command:")) {
                    String command = line.substring(8).trim();
                    if (!command.isEmpty()) {
                        actions.suggestCommands.add(command);
                    }
                } else if (line.startsWith("RunCommand:")) {
                    String command = line.substring(11).trim();
                    if (!command.isEmpty()) {
                        actions.runCommands.add(command);
                    }
                } else {
                    actions.hoverLore.add(line);
                }
            }
        }
        return actions;
    }

    ClickEvent createClickEvent(Player player) {
        return createClickEvent(player, null, null);
    }

    ClickEvent createClickEvent(Player player, Logger logger, String scope) {
        if (!suggestCommands.isEmpty() && !runCommands.isEmpty() && logger != null && scope != null) {
            logger.warning("[警告] " + scope + " 同时配置了 Command 和 RunCommand，只使用 Command（预填）");
            logger.warning("[提示] 请只保留其中一种配置");
        }
        if (!suggestCommands.isEmpty()) {
            return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, joinCommands(player, suggestCommands));
        }
        if (!runCommands.isEmpty()) {
            return new ClickEvent(ClickEvent.Action.RUN_COMMAND, joinCommands(player, runCommands));
        }
        return null;
    }

    private static String joinCommands(Player player, List<String> commands) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i).replace("%player%", player.getName());
            if (!command.startsWith("/")) {
                command = "/" + command;
            }
            builder.append(command);
            if (i < commands.size() - 1) {
                builder.append("; ");
            }
        }
        return builder.toString();
    }
}
