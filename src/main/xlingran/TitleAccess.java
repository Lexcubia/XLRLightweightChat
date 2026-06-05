package xlingran;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Validates persisted title selections against the current title config and
 * permission state before they are shown in chat.
 */
final class TitleAccess {

    private TitleAccess() {
    }

    static Optional<ResolvedTitle> resolveAuthorizedTitle(String wornTitle,
                                                          Map<Integer, String> configuredTitles,
                                                          Function<String, String> displayNormalizer,
                                                          IntPredicate hasTitlePermission) {
        int titleId = resolveTitleId(wornTitle, configuredTitles, displayNormalizer);
        if (titleId <= 0 || !hasTitlePermission.test(titleId)) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedTitle(titleId, configuredTitles.get(titleId)));
    }

    static int resolveTitleId(String wornTitle,
                              Map<Integer, String> configuredTitles,
                              Function<String, String> displayNormalizer) {
        if (wornTitle == null || wornTitle.isEmpty()
                || configuredTitles == null || configuredTitles.isEmpty()) {
            return -1;
        }

        String wornDisplay = displayNormalizer.apply(wornTitle);
        for (Map.Entry<Integer, String> entry : configuredTitles.entrySet()) {
            if (Objects.equals(displayNormalizer.apply(entry.getValue()), wornDisplay)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    record ResolvedTitle(int id, String canonicalTitle) {
    }
}
