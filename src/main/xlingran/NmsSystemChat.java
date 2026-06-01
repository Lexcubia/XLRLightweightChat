package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 向玩家发送 NMS {@code Component} 聊天（兼容 Spigot/Paper 及无法 forName NMS 类的环境）。
 */
final class NmsSystemChat {

    private static final Logger LOGGER = Bukkit.getLogger();

    private static volatile Method playerSendMethod;
    private static volatile Method playerListBroadcastMethod;
    private static volatile Method connectionSendMethod;
    private static volatile Constructor<?> systemChatPacketConstructor;
    private static volatile boolean deliveryResolved;

    private NmsSystemChat() {
    }

    static boolean broadcast(Object nmsComponent) {
        if (nmsComponent == null) {
            return false;
        }
        resolveDelivery(nmsComponent);
        if (!hasDelivery()) {
            synchronized (NmsSystemChat.class) {
                deliveryResolved = false;
                resolveDelivery(nmsComponent);
            }
        }
        if (playerSendMethod != null) {
            return broadcastViaPlayerMethod(nmsComponent);
        }
        if (playerListBroadcastMethod != null) {
            return broadcastViaPlayerList(nmsComponent);
        }
        if (connectionSendMethod != null && systemChatPacketConstructor != null) {
            return broadcastViaSystemChatPacket(nmsComponent);
        }
        LOGGER.warning("[XLRLightweightChat] 无可用 NMS 聊天发送方式（sendSystemMessage / PlayerList / SystemChatPacket 均失败）");
        return false;
    }

    private static boolean hasDelivery() {
        return playerSendMethod != null
                || playerListBroadcastMethod != null
                || (connectionSendMethod != null && systemChatPacketConstructor != null);
    }

    private static void resolveDelivery(Object nmsComponent) {
        if (deliveryResolved && hasDelivery()) {
            return;
        }
        synchronized (NmsSystemChat.class) {
            if (deliveryResolved && hasDelivery()) {
                return;
            }
            Class<?> serverPlayerClass = SpigotReflection.resolveServerPlayerClass();
            if (serverPlayerClass != null) {
                playerSendMethod = findPlayerSendMethod(serverPlayerClass, nmsComponent);
            }
            if (playerSendMethod == null) {
                playerListBroadcastMethod = findPlayerListBroadcastMethod(nmsComponent);
            }
            if (playerSendMethod == null && playerListBroadcastMethod == null) {
                resolveSystemChatPacket(nmsComponent);
            }
            if (playerSendMethod != null) {
                LOGGER.info("[XLRLightweightChat] NMS 聊天发送: ServerPlayer." + playerSendMethod.getName());
            } else if (playerListBroadcastMethod != null) {
                LOGGER.info("[XLRLightweightChat] NMS 聊天发送: PlayerList." + playerListBroadcastMethod.getName());
            } else if (connectionSendMethod != null) {
                LOGGER.info("[XLRLightweightChat] NMS 聊天发送: connection.send(ClientboundSystemChatPacket)");
            }
            deliveryResolved = hasDelivery();
        }
    }

    private static boolean broadcastViaPlayerMethod(Object nmsComponent) {
        boolean any = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                invokeVoid(playerSendMethod, handle, nmsComponent);
                any = true;
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] NMS 玩家发送失败: " + player.getName(), t);
            }
        }
        return any;
    }

    private static boolean broadcastViaPlayerList(Object nmsComponent) {
        try {
            Object craftServer = Bukkit.getServer();
            Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object playerList = minecraftServer.getClass().getMethod("getPlayerList").invoke(minecraftServer);
            playerListBroadcastMethod.invoke(playerList, nmsComponent, false);
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[XLRLightweightChat] PlayerList 广播失败", t);
            return false;
        }
    }

    private static boolean broadcastViaSystemChatPacket(Object nmsComponent) {
        boolean any = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                Object connection = resolveConnection(handle);
                if (connection == null) {
                    continue;
                }
                Object packet = systemChatPacketConstructor.newInstance(nmsComponent, false);
                connectionSendMethod.invoke(connection, packet);
                any = true;
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] SystemChatPacket 发送失败: " + player.getName(), t);
            }
        }
        return any;
    }

    private static void resolveSystemChatPacket(Object nmsComponent) {
        ClassLoader loader = nmsComponent.getClass().getClassLoader();
        String[] packetNames = {
                "net.minecraft.network.protocol.game.ClientboundSystemChatPacket",
                "net.minecraft.network.packet.s2c.play.GameMessageS2CPacket",
        };
        Class<?> packetClass = null;
        for (String name : packetNames) {
            try {
                packetClass = Class.forName(name, true, loader);
                break;
            } catch (ClassNotFoundException ignored) {
                // next
            }
        }
        if (packetClass == null) {
            return;
        }
        String componentClassName = nmsComponent.getClass().getName();
        for (Constructor<?> ctor : packetClass.getConstructors()) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length != 2 || types[1] != boolean.class) {
                continue;
            }
            if (types[0].isInstance(nmsComponent) || types[0].getName().equals(componentClassName)
                    || types[0].getName().endsWith(".Component")) {
                systemChatPacketConstructor = ctor;
                ctor.setAccessible(true);
                break;
            }
        }
        if (systemChatPacketConstructor == null) {
            return;
        }
        Player probe = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (probe == null) {
            return;
        }
        try {
            Object handle = probe.getClass().getMethod("getHandle").invoke(probe);
            Object connection = resolveConnection(handle);
            if (connection == null) {
                return;
            }
            for (Method method : connection.getClass().getMethods()) {
                if (!"send".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(packetClass) || param.getName().endsWith("Packet")) {
                    method.setAccessible(true);
                    connectionSendMethod = method;
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] 解析 connection.send 失败", t);
        }
    }

    private static Object resolveConnection(Object serverPlayerHandle) throws ReflectiveOperationException {
        try {
            Field field = serverPlayerHandle.getClass().getField("connection");
            return field.get(serverPlayerHandle);
        } catch (NoSuchFieldException ignored) {
            // scan
        }
        for (Field field : serverPlayerHandle.getClass().getFields()) {
            if ("connection".equals(field.getName())
                    || field.getType().getSimpleName().contains("PacketListener")
                    || field.getType().getSimpleName().contains("Connection")) {
                Object value = field.get(serverPlayerHandle);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Method findPlayerSendMethod(Class<?> serverPlayerClass, Object nmsComponent) {
        String componentName = nmsComponent.getClass().getName();
        Method namedMatch = null;
        for (Class<?> type = serverPlayerClass; type != null; type = type.getSuperclass()) {
            for (Method method : type.getMethods()) {
                if (method.getReturnType() != void.class) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length < 1 || params.length > 3) {
                    continue;
                }
                if (!acceptsChatComponent(params[0], nmsComponent, componentName)) {
                    continue;
                }
                if (params.length >= 2 && params[1] != boolean.class) {
                    continue;
                }
                if (params.length >= 3 && params[2] != boolean.class) {
                    continue;
                }
                String methodName = method.getName();
                if ("sendSystemMessage".equals(methodName)) {
                    method.setAccessible(true);
                    return method;
                }
                if (methodName.contains("SystemMessage") && namedMatch == null) {
                    namedMatch = method;
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (!"sendSystemMessage".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length < 1 || params.length > 3) {
                    continue;
                }
                if (!acceptsChatComponent(params[0], nmsComponent, componentName)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        if (namedMatch != null) {
            namedMatch.setAccessible(true);
        }
        return namedMatch;
    }

    private static Method findPlayerListBroadcastMethod(Object nmsComponent) {
        try {
            Object craftServer = Bukkit.getServer();
            Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object playerList = minecraftServer.getClass().getMethod("getPlayerList").invoke(minecraftServer);
            String componentName = nmsComponent.getClass().getName();
            for (Method method : playerList.getClass().getMethods()) {
                if (!method.getName().equals("broadcastSystemMessage")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2 || params[1] != boolean.class) {
                    continue;
                }
                if (!acceptsChatComponent(params[0], nmsComponent, componentName)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] PlayerList.broadcastSystemMessage 不可用", t);
        }
        return null;
    }

    private static boolean acceptsChatComponent(Class<?> paramType, Object nmsComponent, String componentName) {
        if (paramType.isInstance(nmsComponent)) {
            return true;
        }
        if (paramType.getName().equals(componentName)) {
            return true;
        }
        return paramType.isInterface()
                && paramType.getName().endsWith(".Component")
                && componentName.contains("network.chat");
    }

    private static void invokeVoid(Method method, Object target, Object nmsComponent) throws ReflectiveOperationException {
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 1) {
            method.invoke(target, nmsComponent);
        } else if (params.length == 2) {
            method.invoke(target, nmsComponent, false);
        } else if (params.length == 3) {
            method.invoke(target, nmsComponent, null, false);
        } else {
            throw new IllegalStateException("Unexpected send method arity: " + params.length);
        }
    }
}
