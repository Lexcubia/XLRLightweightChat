package xlingran;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ItemStackUtil {

    private ItemStackUtil() {
    }

    static ItemStack clonePrototype(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }

    static List<Map<String, Object>> serializeList(List<ItemStack> stacks) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (stacks == null) {
            return out;
        }
        for (ItemStack stack : stacks) {
            ItemStack proto = clonePrototype(stack);
            if (proto != null) {
                out.add(proto.serialize());
            }
        }
        return out;
    }

    static List<ItemStack> deserializeList(List<?> raw) {
        List<ItemStack> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Object entry : raw) {
            if (entry instanceof Map<?, ?> map) {
                try {
                    @SuppressWarnings("unchecked")
                    ItemStack stack = ItemStack.deserialize((Map<String, Object>) map);
                    ItemStack proto = clonePrototype(stack);
                    if (proto != null) {
                        out.add(proto);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }
}
