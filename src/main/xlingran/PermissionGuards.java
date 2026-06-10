package xlingran;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Function;
import java.util.function.Predicate;

/** Shared permission checks for chat formats and wearable titles. */
final class PermissionGuards {

    private PermissionGuards() {
    }

    static String resolveChatFormat(ConfigurationSection chatSection, Predicate<String> hasPermission) {
        if (chatSection == null) {
            return null;
        }

        for (String formatName : chatSection.getKeys(false)) {
            if (!hasPermission.test("xlr.chat." + formatName)) {
                continue;
            }
            String format = chatSection.getString(formatName);
            if (format != null) {
                return format;
            }
        }

        return null;
    }

    static String resolvePermittedTitle(String title, Function<String, Integer> resolveTitleId,
                                        Predicate<String> hasPermission) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        int titleId = resolveTitleId.apply(title);
        if (titleId <= 0) {
            return null;
        }
        return hasPermission.test("xlr.playertitle." + titleId) ? title : null;
    }
}
