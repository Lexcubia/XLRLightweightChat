package xlingran;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PermissionGuardsTest {

    @Test
    void chatFormatDoesNotFallBackToFirstConfiguredFormatWithoutPermission() {
        ConfigurationSection chatSection = chatSection();

        String format = PermissionGuards.resolveChatFormat(chatSection, deniedPermissions());

        assertNull(format);
    }

    @Test
    void chatFormatUsesDefaultOnlyWhenDefaultPermissionIsGranted() {
        ConfigurationSection chatSection = chatSection();
        Predicate<String> permissions = Set.of("xlr.chat.default")::contains;

        String format = PermissionGuards.resolveChatFormat(chatSection, permissions);

        assertEquals("%player%: %chat%", format);
    }

    @Test
    void chatFormatKeepsConfiguredPriorityForGrantedPermissions() {
        ConfigurationSection chatSection = chatSection();
        Predicate<String> permissions = Set.of("xlr.chat.op", "xlr.chat.default")::contains;

        String format = PermissionGuards.resolveChatFormat(chatSection, permissions);

        assertEquals("[op] %player%: %chat%", format);
    }

    @Test
    void titleIsRemovedWhenItsPermissionWasRevoked() {
        String title = PermissionGuards.resolvePermittedTitle("[Admin]", ignored -> 1, deniedPermissions());

        assertNull(title);
    }

    @Test
    void titleIsKeptWhenItsPermissionIsStillGranted() {
        String title = PermissionGuards.resolvePermittedTitle("[Admin]", ignored -> 1,
                Set.of("xlr.playertitle.1")::contains);

        assertEquals("[Admin]", title);
    }

    @Test
    void unknownStoredTitleIsNotPermitted() {
        String title = PermissionGuards.resolvePermittedTitle("[Old]", ignored -> -1,
                Set.of("xlr.playertitle.1")::contains);

        assertNull(title);
    }

    private static ConfigurationSection chatSection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Chat.op", "[op] %player%: %chat%");
        config.set("Chat.default", "%player%: %chat%");
        return config.getConfigurationSection("Chat");
    }

    private static Predicate<String> deniedPermissions() {
        return ignored -> false;
    }
}
