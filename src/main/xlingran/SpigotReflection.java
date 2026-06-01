package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 通过服务端 ClassLoader 加载 CraftBukkit / NMS 类（插件 ClassLoader 无法直接 forName）。
 */
final class SpigotReflection {

    private static volatile String craftPackage;
    private static volatile ClassLoader serverLoader;

    private SpigotReflection() {
    }

    static ClassLoader serverClassLoader() {
        if (serverLoader == null) {
            serverLoader = Bukkit.getServer().getClass().getClassLoader();
        }
        return serverLoader;
    }

    static String craftPackage() {
        if (craftPackage == null) {
            craftPackage = Bukkit.getServer().getClass().getPackage().getName();
        }
        return craftPackage;
    }

    static Class<?> serverClass(String binaryName) throws ClassNotFoundException {
        return Class.forName(binaryName, true, serverClassLoader());
    }

    static Class<?> craftClass(String simpleName) throws ClassNotFoundException {
        return serverClass(craftPackage() + "." + simpleName);
    }

    static Method resolveMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = clazz.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    /**
     * 在类及其父类中查找 {@code sendSystemMessage}，匹配 NMS {@link net.minecraft.network.chat.Component}。
     */
    /**
     * 从 CraftPlayer#getHandle 推断 NMS 玩家类（避免 forName ServerPlayer 在部分服务端失败）。
     */
    static Class<?> resolveServerPlayerClass() {
        try {
            Class<?> craftPlayer = craftClass("entity.CraftPlayer");
            Method getHandle = craftPlayer.getMethod("getHandle");
            Class<?> returnType = getHandle.getReturnType();
            if (returnType != null && returnType != void.class) {
                return returnType;
            }
        } catch (Throwable ignored) {
            // try live handle
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                if (handle != null) {
                    return handle.getClass();
                }
            } catch (Throwable ignored) {
                // next
            }
        }
        return null;
    }

    static Method findSendSystemMessage(Class<?> serverPlayerClass, Class<?> componentClass) {
        if (serverPlayerClass == null || componentClass == null) {
            return null;
        }
        for (Class<?> type = serverPlayerClass; type != null; type = type.getSuperclass()) {
            for (Method method : type.getMethods()) {
                if (!"sendSystemMessage".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length < 1 || params.length > 2) {
                    continue;
                }
                if (!isComponentParameter(params[0], componentClass)) {
                    continue;
                }
                if (params.length == 2 && params[1] != boolean.class) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    /** 用已编码的 NMS 组件实例匹配 {@code sendSystemMessage}（避免 ClassLoader 类型不一致）。 */
    static Method findSendSystemMessageForInstance(Class<?> serverPlayerClass, Object nmsComponent) {
        if (serverPlayerClass == null || nmsComponent == null) {
            return null;
        }
        for (Class<?> type = serverPlayerClass; type != null; type = type.getSuperclass()) {
            for (Method method : type.getMethods()) {
                if (!"sendSystemMessage".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length < 1 || params.length > 2) {
                    continue;
                }
                if (!params[0].isInstance(nmsComponent)) {
                    continue;
                }
                if (params.length == 2 && params[1] != boolean.class) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        return findSendSystemMessage(serverPlayerClass, nmsComponent.getClass());
    }

    private static boolean isComponentParameter(Class<?> paramType, Class<?> componentClass) {
        if (paramType.isAssignableFrom(componentClass)) {
            return true;
        }
        return paramType.getName().equals(componentClass.getName());
    }
}
