package xlingran;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PermissionGuardsTest {

    @Test
    void chatFormatDoesNotFallBackToFirstFormatWithoutPermission() {
        Map<String, String> formats = new LinkedHashMap<>();
        formats.put("op", "op-format");
        formats.put("default", "default-format");

        assertNull(PermissionGuards.resolveChatFormat(formats, permission -> false));
    }

    @Test
    void chatFormatUsesFirstFormatWithPermission() {
        Map<String, String> formats = new LinkedHashMap<>();
        formats.put("op", "op-format");
        formats.put("default", "default-format");

        String resolved = PermissionGuards.resolveChatFormat(
                formats, Set.of("xlr.chat.default")::contains);

        assertEquals("default-format", resolved);
    }

    @Test
    void titleIsRemovedWhenPermissionWasRevoked() {
        Map<Integer, String> titles = new LinkedHashMap<>();
        titles.put(1, "[Admin]");

        assertNull(PermissionGuards.resolvePermittedTitle("[Admin]", titles, title -> title, permission -> false));
    }

    @Test
    void titleIsCanonicalizedWhenPermissionStillExists() {
        Map<Integer, String> titles = new LinkedHashMap<>();
        titles.put(1, "&a[Admin]");

        String resolved = PermissionGuards.resolvePermittedTitle(
                "[Admin]", titles, title -> title.replace("&a", ""), Set.of("xlr.playertitle.1")::contains);

        assertEquals("&a[Admin]", resolved);
    }
}
