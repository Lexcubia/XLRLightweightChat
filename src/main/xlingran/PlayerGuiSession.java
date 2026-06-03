package xlingran;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerGuiSession {

    public enum InputMode {
        NONE,
        DURABILITY,
        ENCHANT_LEVEL,
        BATCH_APPLY
    }

    private final Map<UUID, String> editingTemplate = new java.util.HashMap<>();
    private final Map<UUID, String> chatTemplate = new HashMap<>();
    private final Map<UUID, InputMode> inputMode = new HashMap<>();
    private final Map<UUID, Long> lastClickMillis = new java.util.HashMap<>();
    private final Map<UUID, Enchantment> pendingEnchant = new HashMap<>();
    private final Map<UUID, String> linkingBoxTemplate = new HashMap<>();
    private final Map<UUID, BoxOpenState> openBoxSnapshot = new HashMap<>();

    public record BoxOpenState(String boxName, ItemStack[] snapshot) {
    }

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
        setInputMode(playerId, mode, null);
    }

    public void setInputMode(UUID playerId, InputMode mode, String templateName) {
        if (mode == null || mode == InputMode.NONE) {
            inputMode.remove(playerId);
            chatTemplate.remove(playerId);
            pendingEnchant.remove(playerId);
        } else {
            inputMode.put(playerId, mode);
            if (templateName != null && !templateName.isEmpty()) {
                chatTemplate.put(playerId, templateName);
            }
        }
    }

    public String getChatTemplate(UUID playerId) {
        String bound = chatTemplate.get(playerId);
        if (bound != null && !bound.isEmpty()) {
            return bound;
        }
        return editingTemplate.get(playerId);
    }

    public Enchantment getPendingEnchant(UUID playerId) {
        return pendingEnchant.get(playerId);
    }

    public void setPendingEnchant(UUID playerId, Enchantment enchant) {
        if (enchant == null) {
            pendingEnchant.remove(playerId);
        } else {
            pendingEnchant.put(playerId, enchant);
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
        chatTemplate.remove(playerId);
        pendingEnchant.remove(playerId);
    }

    public void clearAll(UUID playerId) {
        clearInput(playerId);
        editingTemplate.remove(playerId);
        linkingBoxTemplate.remove(playerId);
        openBoxSnapshot.remove(playerId);
    }

    public void setOpenBoxSnapshot(UUID playerId, String boxName, ItemStack[] snapshot) {
        if (boxName == null || boxName.isBlank()) {
            openBoxSnapshot.remove(playerId);
            return;
        }
        ItemStack[] copy = new ItemStack[PlayerBoxManager.BOX_CAPACITY];
        if (snapshot != null) {
            for (int i = 0; i < copy.length && i < snapshot.length; i++) {
                ItemStack stack = snapshot[i];
                copy[i] = stack == null ? null : stack.clone();
            }
        }
        openBoxSnapshot.put(playerId, new BoxOpenState(boxName, copy));
    }

    public BoxOpenState getOpenBoxSnapshot(UUID playerId) {
        return openBoxSnapshot.get(playerId);
    }

    public void clearOpenBoxSnapshot(UUID playerId) {
        openBoxSnapshot.remove(playerId);
    }

    public void setLinkingBoxTemplate(UUID playerId, String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            linkingBoxTemplate.remove(playerId);
        } else {
            linkingBoxTemplate.put(playerId, templateName);
        }
    }

    public String getLinkingBoxTemplate(UUID playerId) {
        return linkingBoxTemplate.get(playerId);
    }

    public void clearLinkingBoxTemplate(UUID playerId) {
        linkingBoxTemplate.remove(playerId);
    }
}
