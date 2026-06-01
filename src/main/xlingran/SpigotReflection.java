package xlingran;

import org.bukkit.Bukkit;

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
}
