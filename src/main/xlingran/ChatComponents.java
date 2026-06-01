package xlingran;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.List;

/** Bungee 聊天组件解析（& / § / 十六进制色）。 */
final class ChatComponents {

    private ChatComponents() {
    }

    static BaseComponent[] parseLegacyTextWithHexColors(String text) {
        List<BaseComponent> components = new ArrayList<>();
        TextComponent current = new TextComponent("");

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == 'x' && i + 13 < text.length()) {
                    try {
                        int r = (Character.digit(text.charAt(i + 3), 16) << 4) | Character.digit(text.charAt(i + 5), 16);
                        int g = (Character.digit(text.charAt(i + 7), 16) << 4) | Character.digit(text.charAt(i + 9), 16);
                        int b = (Character.digit(text.charAt(i + 11), 16) << 4) | Character.digit(text.charAt(i + 13), 16);
                        flush(components, current);
                        current = new TextComponent("");
                        current.setColor(net.md_5.bungee.api.ChatColor.of(String.format("#%02x%02x%02x", r, g, b)));
                        i += 13;
                        continue;
                    } catch (Exception ignored) {
                        // fall through as plain char
                    }
                } else if (Character.isLetterOrDigit(next)) {
                    flush(components, current);
                    current = new TextComponent("");
                    net.md_5.bungee.api.ChatColor bungeeColor = net.md_5.bungee.api.ChatColor.getByChar(next);
                    if (bungeeColor != null) {
                        current.setColor(bungeeColor);
                    }
                    i++;
                    continue;
                }
            }
            current.setText(current.getText() + c);
        }
        flush(components, current);
        return components.toArray(new BaseComponent[0]);
    }

    private static void flush(List<BaseComponent> components, TextComponent current) {
        if (!current.getText().isEmpty()) {
            components.add(current);
        }
    }
}
