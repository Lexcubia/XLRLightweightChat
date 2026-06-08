package xlingran;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Permission-sensitive resolution helpers kept free of Bukkit types for focused validation.
 */
final class PermissionGuards {

    private PermissionGuards() {
    }

    static String resolveChatFormat(Map<String, String> chatFormats, Predicate<String> hasPermission) {
        if (chatFormats == null || chatFormats.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : chatFormats.entrySet()) {
            String formatName = entry.getKey();
            String format = entry.getValue();
            if (format != null && hasPermission.test("xlr.chat." + formatName)) {
                return format;
            }
        }
        return null;
    }

    static String resolvePermittedTitle(String wornTitle, Map<Integer, String> playerTitles,
                                        Function<String, String> displayResolver,
                                        Predicate<String> hasPermission) {
        int titleId = resolveTitleId(wornTitle, playerTitles, displayResolver);
        if (titleId <= 0 || !hasPermission.test("xlr.playertitle." + titleId)) {
            return null;
        }
        return playerTitles.get(titleId);
    }

    static int resolveTitleId(String wornTitle, Map<Integer, String> playerTitles,
                              Function<String, String> displayResolver) {
        if (wornTitle == null || wornTitle.isEmpty() || playerTitles == null || playerTitles.isEmpty()) {
            return -1;
        }
        String wornDisplay = displayResolver.apply(wornTitle);
        for (Map.Entry<Integer, String> entry : playerTitles.entrySet()) {
            if (displayResolver.apply(entry.getValue()).equals(wornDisplay)) {
                return entry.getKey();
            }
        }
        return -1;
    }
}
