package xlingran;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.chat.BaseComponent;
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

    /**
     * 物品段 JSON 节点（含 show_item + components）。
     */
    static JsonObject itemTextNode(String legacyText, ItemStack stack) {
        JsonObject itemJson = ItemHoverUtil.resolveItemJsonForHover(stack);
        if (itemJson == null || !itemJson.has("components")) {
            LOGGER.warning("[XLRLightweightChat] 无法生成物品 components，悬浮可能显示为原版");
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

    /**
     * 用 extra 数组构建完整聊天组件并 NMS 广播；成功返回 true。
     */
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
     * 将已组装的 Bungee 组件转为 NMS 后发送（仍可能丢失 components，仅作兜底）。
     */
    static void broadcast(BaseComponent[] components) {
        if (components == null || components.length == 0) {
            return;
        }
        Object nms = bungeeToNms(components);
        if (nms != null && broadcastNmsComponent(nms)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(components);
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
            LOGGER.warning("[XLRLightweightChat] CraftChatMessage.fromJSON 不可用，无法发送 components 悬浮");
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
        try {
            Class<?> craftChatMessage = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");
            for (String methodName : new String[]{"fromBungeeMessage", "bungeeToVanilla"}) {
                try {
                    Method method = craftChatMessage.getMethod(methodName, BaseComponent[].class);
                    return method.invoke(null, (Object) components);
                } catch (NoSuchMethodException ignored) {
                    // next
                }
            }
            if (components.length == 1) {
                for (String methodName : new String[]{"fromBungeeMessage", "bungeeToVanilla"}) {
                    try {
                        Method method = craftChatMessage.getMethod(methodName, BaseComponent.class);
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
            LOGGER.warning("[XLRLightweightChat] ServerPlayer.sendSystemMessage 不可用");
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
                Class<?> craftChatMessage = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");
                fromJsonMethod = craftChatMessage.getMethod("fromJSON", String.class);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] 无法解析 CraftChatMessage", t);
            }
            try {
                Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                sendSystemMessageMethod = Class.forName("net.minecraft.server.level.ServerPlayer")
                        .getMethod("sendSystemMessage", componentClass, boolean.class);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] 无法解析 sendSystemMessage", t);
            }
            resolved = true;
        }
    }
}
