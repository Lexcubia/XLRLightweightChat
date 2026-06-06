package xlingran;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PermissionGuardsTest {
    @Test
    void chatFormatRequiresMatchingPermissionInsteadOfFallingBackToFirstEntry() {
        ConfigurationSection chat = chatSection("op", "op-format", "default", "default-format");

        assertNull(PermissionGuards.resolveChatFormat(chat, permission -> false));
    }

    @Test
    void chatFormatUsesFirstPermittedFormatByConfiguredPriority() {
        ConfigurationSection chat = chatSection("op", "op-format", "default", "default-format");
        Predicate<String> permissions = Set.of("xlr.chat.op", "xlr.chat.default")::contains;

        assertEquals("op-format", PermissionGuards.resolveChatFormat(chat, permissions));
    }

    @Test
    void chatFormatAllowsDefaultOnlyWhenDefaultPermissionIsGranted() {
        ConfigurationSection chat = chatSection("op", "op-format", "default", "default-format");

        assertEquals(
                "default-format",
                PermissionGuards.resolveChatFormat(chat, "xlr.chat.default"::equals)
        );
    }

    @Test
    void persistedTitleIsHiddenAfterPermissionIsRevoked() {
        Map<Integer, String> titles = configuredTitles();

        assertNull(PermissionGuards.resolvePermittedCurrentTitle(
                "[Admin]",
                titles,
                title -> title,
                permission -> false
        ));
    }

    @Test
    void persistedTitleIsShownWhenCurrentPermissionStillAllowsIt() {
        Map<Integer, String> titles = configuredTitles();

        assertEquals("[Member]", PermissionGuards.resolvePermittedCurrentTitle(
                "[Member]",
                titles,
                title -> title,
                "xlr.playertitle.2"::equals
        ));
    }

    @Test
    void unconfiguredPersistedTitleIsHiddenBecauseNoPermissionCanBeVerified() {
        Map<Integer, String> titles = configuredTitles();

        assertNull(PermissionGuards.resolvePermittedCurrentTitle(
                "[Deleted]",
                titles,
                title -> title,
                permission -> true
        ));
    }

    private static ConfigurationSection chatSection(String... formats) {
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < formats.length; i += 2) {
            config.set("Chat." + formats[i], formats[i + 1]);
        }
        return config.getConfigurationSection("Chat");
    }

    private static Map<Integer, String> configuredTitles() {
        Map<Integer, String> titles = new TreeMap<>();
        titles.put(1, "[Admin]");
        titles.put(2, "[Member]");
        return titles;
    }
}
