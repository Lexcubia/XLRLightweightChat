package xlingran;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerGuiSession {

    public enum InputMode {
        NONE,
        TITLE,
        LORE
    }

    private final Map<UUID, String> editingTemplate = new java.util.HashMap<>();
    private final Map<UUID, InputMode> inputMode = new HashMap<>();
    private final Map<UUID, Long> lastClickMillis = new java.util.HashMap<>();

    public String getEditingTemplate(UUID playerId) {
        return editingTemplate.get(playerId);
    }

    public void setEditingTemplate(UUID playerId, String templateName) {
        if (templateName == null) {
            editingTemplate.remove(playerId);
        } else {
            editingTemplate.put(playerId, templateName);
        }
    }

    public InputMode getInputMode(UUID playerId) {
        return inputMode.getOrDefault(playerId, InputMode.NONE);
    }

    public void setInputMode(UUID playerId, InputMode mode) {
        if (mode == null || mode == InputMode.NONE) {
            inputMode.remove(playerId);
        } else {
            inputMode.put(playerId, mode);
        }
    }

    public boolean tryClickCooldown(UUID playerId, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = lastClickMillis.get(playerId);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        lastClickMillis.put(playerId, now);
        return true;
    }

    public void clearInput(UUID playerId) {
        inputMode.remove(playerId);
    }
}
