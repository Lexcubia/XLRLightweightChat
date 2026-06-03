package xlingran;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class HopperTemplateManager {

    private final Map<UUID, Map<String, HopperTemplate>> playerTemplates = new HashMap<>();
    private final Map<UUID, String> enabledTemplate = new HashMap<>();

    public Map<String, HopperTemplate> getTemplates(UUID playerId) {
        return playerTemplates.computeIfAbsent(playerId, k -> new LinkedHashMap<>());
    }

    public HopperTemplate getTemplate(UUID playerId, String name) {
        return getTemplates(playerId).get(name);
    }

    public void putTemplate(UUID playerId, String name, HopperTemplate template) {
        getTemplates(playerId).put(name, template);
    }

    public boolean createTemplate(Player player, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Map<String, HopperTemplate> map = getTemplates(player.getUniqueId());
        if (map.containsKey(name)) {
            return false;
        }
        map.put(name, new HopperTemplate());
        setEnabledTemplate(player.getUniqueId(), name);
        return true;
    }

    public String getEnabledTemplateName(UUID playerId) {
        return enabledTemplate.get(playerId);
    }

    public HopperTemplate getEnabledTemplate(UUID playerId) {
        String name = enabledTemplate.get(playerId);
        if (name == null) {
            return null;
        }
        return getTemplate(playerId, name);
    }

    public void setEnabledTemplate(UUID playerId, String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            enabledTemplate.remove(playerId);
            return;
        }
        if (!getTemplates(playerId).containsKey(templateName)) {
            return;
        }
        enabledTemplate.put(playerId, templateName);
    }

    public void toggleTemplateEnabled(Player player, String templateName) {
        UUID id = player.getUniqueId();
        if (!getTemplates(id).containsKey(templateName)) {
            return;
        }
        String current = enabledTemplate.get(id);
        if (templateName.equals(current)) {
            enabledTemplate.remove(id);
        } else {
            enabledTemplate.put(id, templateName);
        }
    }

    public boolean isTemplateEnabled(UUID playerId, String templateName) {
        return templateName != null && templateName.equals(enabledTemplate.get(playerId));
    }

    public Map<UUID, Map<String, HopperTemplate>> getAllPlayerTemplates() {
        return Collections.unmodifiableMap(playerTemplates);
    }

    public void clearAll() {
        playerTemplates.clear();
        enabledTemplate.clear();
    }
}
