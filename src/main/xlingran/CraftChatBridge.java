package xlingran;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 通过 CraftBukkit {@code CraftChatMessage.fromJSON} 发送含 1.21 components 的 SHOW_ITEM 悬浮。
 */
final class CraftChatBridge {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static volatile Method fromJsonMethod;
    private static volatile Method fromComponentMethod;
    private static volatile Method sendSystemMessageMethod;
    private static volatile boolean resolved;

    private CraftChatBridge() {
    }

    /**
     * 带 1.21 物品悬浮的文本组件；失败时返回 null。
     */
    static BaseComponent textWithItemHover(String legacyText, ItemStack stack) {
        JsonObject itemJson = ItemHoverUtil.resolveItemJsonForHover(stack);
        if (itemJson == null || !itemJson.has("components")) {
            return null;
        }
        for (JsonObject hoverShape : buildHoverShapes(itemJson)) {
            JsonObject root = new JsonObject();
            root.addProperty("text", legacyText);
            root.add("hoverEvent", hoverShape);
            BaseComponent fromCraft = fromCraftJson(root);
            if (fromCraft != null) {
                return fromCraft;
            }
        }
        return null;
    }

    /**
     * 优先用 NMS 直接广播（保留 components）；失败则退回 Bungee sendMessage。
     */
    static void broadcast(BaseComponent[] components) {
        if (components == null || components.length == 0) {
            return;
        }
        Object nms = bungeeToNms(components);
        if (nms != null && broadcastNms(nms)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(components);
        }
    }

    private static JsonObject[] buildHoverShapes(JsonObject itemJson) {
        JsonObject inline = new JsonObject();
        inline.addProperty("action", "show_item");
        for (String key : itemJson.keySet()) {
            inline.add(key, itemJson.get(key));
        }

        JsonArray contentsArray = new JsonArray();
        contentsArray.add(itemJson.deepCopy());
        JsonObject wrapped = new JsonObject();
        wrapped.addProperty("action", "show_item");
        wrapped.add("contents", contentsArray);

        JsonObject contentsObject = new JsonObject();
        contentsObject.addProperty("action", "show_item");
        contentsObject.add("contents", itemJson.deepCopy());

        return new JsonObject[]{inline, wrapped, contentsObject};
    }

    private static BaseComponent fromCraftJson(JsonObject root) {
        resolve();
        if (fromJsonMethod == null) {
            return null;
        }
        try {
            Object nms = fromJsonMethod.invoke(null, root.toString());
            if (nms == null) {
                return null;
            }
            if (fromComponentMethod != null) {
                Object bungee = fromComponentMethod.invoke(null, nms);
                if (bungee instanceof BaseComponent[] arr && arr.length > 0) {
                    return arr[0];
                }
                if (bungee instanceof BaseComponent single) {
                    return single;
                }
            }
            return null;
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] CraftChatMessage.fromJSON 失败", t);
            return null;
        }
    }

    private static Object bungeeToNms(BaseComponent[] components) {
        resolve();
        try {
            Class<?> craftChatMessage = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");
            for (String methodName : new String[]{"fromBungeeMessage", "bungeeToVanilla"}) {
                try {
                    Method method = craftChatMessage.getMethod(methodName, BaseComponent[].class);
                    return method.invoke(null, (Object) components);
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
            if (components.length == 1) {
                for (String methodName : new String[]{"fromBungeeMessage", "bungeeToVanilla"}) {
                    try {
                        Method method = craftChatMessage.getMethod(methodName, BaseComponent.class);
                        return method.invoke(null, components[0]);
                    } catch (NoSuchMethodException ignored) {
                        // try next
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] Bungee→NMS 转换失败", t);
        }
        return null;
    }

    private static boolean broadcastNms(Object nmsComponent) {
        resolve();
        if (sendSystemMessageMethod == null) {
            return false;
        }
        boolean any = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                sendSystemMessageMethod.invoke(handle, nmsComponent, false);
                any = true;
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "[XLRLightweightChat] NMS 发送失败: " + player.getName(), t);
            }
        }
        return any;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (CraftChatBridge.class) {
            if (resolved) {
                return;
            }
            try {
                Class<?> craftChatMessage = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");
                fromJsonMethod = craftChatMessage.getMethod("fromJSON", String.class);
                for (String name : new String[]{"fromComponent", "toBungee", "bungeeFromComponent"}) {
                    try {
                        fromComponentMethod = craftChatMessage.getMethod(name,
                                Class.forName("net.minecraft.network.chat.Component"));
                        break;
                    } catch (NoSuchMethodException ignored) {
                        // next
                    }
                }
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "[XLRLightweightChat] CraftChatMessage 不可用", t);
            }
            try {
                Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                sendSystemMessageMethod = Class.forName("net.minecraft.server.level.ServerPlayer")
                        .getMethod("sendSystemMessage", componentClass, boolean.class);
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "[XLRLightweightChat] sendSystemMessage 不可用", t);
            }
            resolved = true;
        }
    }
}
