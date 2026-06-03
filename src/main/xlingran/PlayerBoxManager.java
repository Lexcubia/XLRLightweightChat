package xlingran;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerBoxManager {

    public static final int BOX_CAPACITY = 54;

    private final Map<UUID, Map<String, ItemStack[]>> playerBoxes = new HashMap<>();

    public List<String> getBoxNames(UUID playerId) {
        Map<String, ItemStack[]> boxes = playerBoxes.get(playerId);
        if (boxes == null || boxes.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(boxes.keySet());
    }

    public boolean createBox(UUID playerId, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Map<String, ItemStack[]> boxes = playerBoxes.computeIfAbsent(playerId, k -> new LinkedHashMap<>());
        if (boxes.containsKey(name)) {
            return false;
        }
        boxes.put(name, new ItemStack[BOX_CAPACITY]);
        return true;
    }

    public boolean hasBox(UUID playerId, String name) {
        Map<String, ItemStack[]> boxes = playerBoxes.get(playerId);
        return boxes != null && boxes.containsKey(name);
    }

    public ItemStack[] getBoxContents(UUID playerId, String name) {
        Map<String, ItemStack[]> boxes = playerBoxes.get(playerId);
        if (boxes == null) {
            return null;
        }
        return boxes.get(name);
    }

    public void setBoxContents(UUID playerId, String name, ItemStack[] contents) {
        if (name == null || name.isBlank()) {
            return;
        }
        Map<String, ItemStack[]> boxes = playerBoxes.computeIfAbsent(playerId, k -> new LinkedHashMap<>());
        ItemStack[] copy = new ItemStack[BOX_CAPACITY];
        if (contents != null) {
            for (int i = 0; i < BOX_CAPACITY && i < contents.length; i++) {
                ItemStack stack = contents[i];
                copy[i] = stack == null ? null : stack.clone();
            }
        }
        boxes.put(name, copy);
    }

    public HashMap<Integer, ItemStack> addItem(UUID playerId, String boxName, ItemStack stack) {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        if (stack == null || stack.getType().isAir() || boxName == null) {
            leftover.put(0, stack);
            return leftover;
        }
        ItemStack[] slots = getBoxContents(playerId, boxName);
        if (slots == null) {
            leftover.put(0, stack);
            return leftover;
        }
        ItemStack remaining = stack.clone();
        for (int i = 0; i < BOX_CAPACITY && remaining.getAmount() > 0; i++) {
            ItemStack inSlot = slots[i];
            if (inSlot == null || inSlot.getType().isAir()) {
                slots[i] = remaining;
                return leftover;
            }
            if (inSlot.isSimilar(remaining)) {
                int max = inSlot.getMaxStackSize();
                int space = max - inSlot.getAmount();
                if (space > 0) {
                    int move = Math.min(space, remaining.getAmount());
                    inSlot.setAmount(inSlot.getAmount() + move);
                    remaining.setAmount(remaining.getAmount() - move);
                }
            }
        }
        if (remaining.getAmount() > 0) {
            leftover.put(0, remaining);
        }
        return leftover;
    }

    Map<UUID, Map<String, ItemStack[]>> getAllPlayerBoxes() {
        return playerBoxes;
    }

    void putPlayerBoxes(UUID playerId, Map<String, ItemStack[]> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            playerBoxes.remove(playerId);
            return;
        }
        playerBoxes.put(playerId, new LinkedHashMap<>(boxes));
    }
}
