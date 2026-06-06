package xlingran;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

final class PermissionGuards {
    private PermissionGuards() {
    }

    static String resolveChatFormat(ConfigurationSection chatSection, Predicate<String> hasPermission) {
        if (chatSection == null) {
            return null;
        }

        Set<String> formats = chatSection.getKeys(false);
        for (String formatName : formats) {
            String permission = "xlr.chat." + formatName;
            if (hasPermission.test(permission)) {
                return chatSection.getString(formatName);
            }
        }

        return null;
    }

    static String resolvePermittedCurrentTitle(String storedTitle,
                                               Map<Integer, String> configuredTitles,
                                               Function<String, String> normalizeTitle,
                                               Predicate<String> hasPermission) {
        int titleId = resolveTitleId(storedTitle, configuredTitles, normalizeTitle);
        if (titleId <= 0) {
            return null;
        }

        return hasPermission.test("xlr.playertitle." + titleId) ? storedTitle : null;
    }

    static int resolveTitleId(String wornTitle,
                              Map<Integer, String> configuredTitles,
                              Function<String, String> normalizeTitle) {
        if (wornTitle == null || wornTitle.isEmpty() || configuredTitles == null || configuredTitles.isEmpty()) {
            return -1;
        }

        String wornDisplay = normalizeTitle.apply(wornTitle);
        for (Map.Entry<Integer, String> entry : configuredTitles.entrySet()) {
            String configuredDisplay = normalizeTitle.apply(entry.getValue());
            if (configuredDisplay.equals(wornDisplay)) {
                return entry.getKey();
            }
        }

        return -1;
    }
}
