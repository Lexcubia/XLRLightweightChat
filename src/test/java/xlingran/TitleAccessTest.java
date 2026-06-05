package xlingran;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TitleAccessTest {

    private static final Map<Integer, String> TITLES = Map.of(
            1, "%color2%[Admin]",
            2, "&b[Member]");

    @Test
    public void rejectsPersistedTitleWhenPermissionWasRevoked() {
        Optional<TitleAccess.ResolvedTitle> resolved = TitleAccess.resolveAuthorizedTitle(
                "%color2%[Admin]",
                TITLES,
                Function.identity(),
                titleId -> false);

        assertTrue(resolved.isEmpty());
    }

    @Test
    public void rejectsPersistedTitleThatNoLongerExistsInConfig() {
        Optional<TitleAccess.ResolvedTitle> resolved = TitleAccess.resolveAuthorizedTitle(
                "&c[Old]",
                TITLES,
                Function.identity(),
                titleId -> true);

        assertTrue(resolved.isEmpty());
    }

    @Test
    public void resolvesProcessedStoredTitleToCanonicalConfiguredTitle() {
        Optional<TitleAccess.ResolvedTitle> resolved = TitleAccess.resolveAuthorizedTitle(
                "[Admin]",
                TITLES,
                title -> title.replace("%color2%", ""),
                titleId -> titleId == 1);

        assertTrue(resolved.isPresent());
        assertEquals(1, resolved.get().id());
        assertEquals("%color2%[Admin]", resolved.get().canonicalTitle());
    }
}
