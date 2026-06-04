package xlingran.gui;

import org.bukkit.ChatColor;

import java.util.Map;

public final class TextPlaceholders {

    private TextPlaceholders() {
    }

    public static String color(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String apply(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        String out = template;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                out = out.replace("%" + e.getKey() + "%", e.getValue() != null ? e.getValue() : "");
            }
        }
        return color(out);
    }
}
