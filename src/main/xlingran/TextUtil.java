package xlingran;

import org.bukkit.ChatColor;

final class TextUtil {

    private TextUtil() {
    }

    static String stripForMatch(String text) {
        if (text == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
        return stripped.toLowerCase();
    }
}
