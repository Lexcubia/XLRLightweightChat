package xlingran;

import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Content;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * 聊天物品悬浮（SHOW_ITEM），兼容 Spigot/Paper 1.21。
 */
public final class ItemHoverUtil {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static volatile boolean legacyWarnLogged;
    private static volatile Method serializeItemAsJsonMethod;
    private static volatile boolean serializeMethodResolved;

    private ItemHoverUtil() {
    }

    public static HoverEvent createShowItemHover(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Content content = buildItemContent(stack.clone());
        if (content == null) {
            return null;
        }
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, content);
    }

    private static Content buildItemContent(ItemStack stack) {
        Content fromJson = tryBuildFromSerializedJson(stack);
        if (fromJson != null) {
            return fromJson;
        }
        return buildFromItemMeta(stack);
    }

    private static Content tryBuildFromSerializedJson(ItemStack stack) {
        Method method = resolveSerializeItemAsJson();
        if (method == null) {
            return null;
        }
        try {
            String json = (String) method.invoke(Bukkit.getUnsafe(), stack);
            if (json == null || json.isBlank()) {
                return null;
            }
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String id = root.has("id") ? root.get("id").getAsString() : stack.getType().getKey().toString();
            int count = root.has("count") ? root.get("count").getAsInt() : stack.getAmount();
            ItemTag tag = null;
            if (root.has("components")) {
                tag = ItemTag.ofNbt(root.get("components").toString());
            } else if (root.has("tag")) {
                tag = ItemTag.ofNbt(root.get("tag").toString());
            }
            return new net.md_5.bungee.api.chat.hover.content.Item(id, count, tag);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.fine("[XLRLightweightChat] serializeItemAsJson 不可用: " + e.getMessage());
            return null;
        }
    }

    private static Method resolveSerializeItemAsJson() {
        if (!serializeMethodResolved) {
            synchronized (ItemHoverUtil.class) {
                if (!serializeMethodResolved) {
                    try {
                        serializeItemAsJsonMethod = Bukkit.getUnsafe().getClass()
                                .getMethod("serializeItemAsJson", ItemStack.class);
                    } catch (NoSuchMethodException ignored) {
                        serializeItemAsJsonMethod = null;
                    }
                    serializeMethodResolved = true;
                }
            }
        }
        return serializeItemAsJsonMethod;
    }

    private static net.md_5.bungee.api.chat.hover.content.Item buildFromItemMeta(ItemStack stack) {
        if (!legacyWarnLogged) {
            legacyWarnLogged = true;
            LOGGER.info("[XLRLightweightChat] 物品悬浮使用 ItemMeta 数据；Paper 可自动使用 serializeItemAsJson。");
        }
        String id = stack.getType().getKey().toString();
        int count = stack.getAmount();
        ItemTag tag = null;
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String data = meta.getAsString();
                if (data != null && !data.isEmpty()) {
                    tag = ItemTag.ofNbt(data);
                }
            }
        }
        return new net.md_5.bungee.api.chat.hover.content.Item(id, count, tag);
    }
}
