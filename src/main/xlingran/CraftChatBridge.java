package xlingran;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 通过 CraftBukkit {@code CraftChatMessage.fromJSON} 直接发送 NMS 组件（保留 1.21 components）。
 */
final class CraftChatBridge {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static volatile Method fromJsonMethod;
    private static volatile Method sendSystemMessageMethod;
    private static volatile boolean resolved;

    private CraftChatBridge() {
    }

    static JsonObject textNode(String legacyText) {
        JsonObject node = new JsonObject();
        node.addProperty("text", legacyText);
        return node;
    }

    static JsonObject itemTextNode(String legacyText, ItemStack stack) {
        JsonObject itemJson = Item.resolveItemJsonForHover(stack);
        if (itemJson == null || !itemJson.has("components")) {
            LOGGER.warning("[XLRLightweightChat] 无法生成物品 components");
            return textNode(legacyText);
        }
        JsonObject node = new JsonObject();
        node.addProperty("text", legacyText);
        JsonObject hover = new JsonObject();
        hover.addProperty("action", "show_item");
        hover.add("contents", itemJson.deepCopy());
        node.add("hoverEvent", hover);
        return node;
    }

    static boolean broadcastWithExtra(JsonArray extra) {
        if (extra == null || extra.isEmpty()) {
            return false;
        }
        JsonObject root = new JsonObject();
        root.addProperty("text", "");
        root.add("extra", extra);
        return broadcastNmsComponent(parseJsonToNms(root));
    }

    /**
     * 兜底发送：剥离会导致崩溃的 SHOW_ITEM 悬浮后再用 Spigot 发送。
     */
    static void broadcast(BaseComponent[] components) {
        if (components == null || components.length == 0) {
            return;
        }
        Object nms = bungeeToNms(stripUnsafeItemHover(components));
        if (nms != null && broadcastNmsComponent(nms)) {
            return;
        }
        BaseComponent[] safe = stripUnsafeItemHover(components);
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.spigot().sendMessage(safe);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] Spigot 发送聊天失败", t);
            }
        }
    }

    private static BaseComponent[] stripUnsafeItemHover(BaseComponent[] components) {
        for (BaseComponent component : components) {
            stripHoverRecursive(component);
        }
        return components;
    }

    private static void stripHoverRecursive(BaseComponent component) {
        if (component == null) {
            return;
        }
        HoverEvent hover = component.getHoverEvent();
        if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_ITEM) {
            component.setHoverEvent(null);
        }
        for (BaseComponent extra : component.getExtra()) {
            stripHoverRecursive(extra);
        }
    }

    static void broadcastOnMainThread(JavaPlugin plugin, Runnable broadcastTask) {
        if (Bukkit.isPrimaryThread()) {
            broadcastTask.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, broadcastTask);
    }

    private static Object parseJsonToNms(JsonObject root) {
        resolve();
        if (fromJsonMethod == null) {
            return null;
        }
        try {
            return fromJsonMethod.invoke(null, root.toString());
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[XLRLightweightChat] CraftChatMessage.fromJSON 失败: " + t.getMessage(), t);
            return null;
        }
    }

    private static Object bungeeToNms(BaseComponent[] components) {
        resolve();
        if (fromJsonMethod == null) {
            return null;
        }
        try {
            Class<?> craftChatMessage = SpigotReflection.craftClass("util.CraftChatMessage");
            for (String methodName : new String[]{"fromBungeeMessage", "bungeeToVanilla"}) {
                try {
                    Method method = SpigotReflection.resolveMethod(craftChatMessage, methodName, BaseComponent[].class);
                    return method.invoke(null, (Object) components);
                } catch (NoSuchMethodException ignored) {
                    // next
                }
            }
            if (components.length == 1) {
                for (String methodName : new String[]{"fromBungeeMessage", "bungeeToVanilla"}) {
                    try {
                        Method method = SpigotReflection.resolveMethod(craftChatMessage, methodName, BaseComponent.class);
                        return method.invoke(null, components[0]);
                    } catch (NoSuchMethodException ignored) {
                        // next
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] Bungee→NMS 失败", t);
        }
        return null;
    }

    private static boolean broadcastNmsComponent(Object nmsComponent) {
        if (nmsComponent == null) {
            return false;
        }
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
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] NMS 发送失败: " + player.getName(), t);
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
                Class<?> craftChatMessage = SpigotReflection.craftClass("util.CraftChatMessage");
                fromJsonMethod = SpigotReflection.resolveMethod(craftChatMessage, "fromJSON", String.class);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] 无法解析 CraftChatMessage: " + t.getMessage());
            }
            try {
                Class<?> componentClass = SpigotReflection.serverClass("net.minecraft.network.chat.Component");
                Class<?> serverPlayerClass = SpigotReflection.serverClass("net.minecraft.server.level.ServerPlayer");
                sendSystemMessageMethod = SpigotReflection.resolveMethod(serverPlayerClass,
                        "sendSystemMessage", componentClass, boolean.class);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] 无法解析 sendSystemMessage: " + t.getMessage());
            }
            resolved = true;
        }
    }
}
