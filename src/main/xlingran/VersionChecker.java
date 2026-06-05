package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class VersionChecker {

    private static final String VERSION_URL = "https://plugin.xlingran.com/XLRHopperVersion.txt";
    private static final int TIMEOUT_MS = 5000;

    private VersionChecker() {
    }

    public static void checkOnEnable(JavaPlugin plugin) {
        String current = plugin.getDescription().getVersion();
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.GREEN + "当前版本: "
                        + ChatColor.AQUA + current);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String remote = fetchRemoteVersion();
            Bukkit.getScheduler().runTask(plugin, () -> reportResult(remote, current));
        });
    }

    private static String fetchRemoteVersion() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(VERSION_URL).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "XLRHopper");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    return null;
                }
                return line.trim();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void reportResult(String remote, String current) {
        if (remote == null) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "插件更新检测失败");
            return;
        }
        if (remote.equals(current)) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "当前已是最新版: ");
            return;
        }
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.GREEN + "有新版本: "
                        + ChatColor.AQUA + remote
                        + ChatColor.GREEN + " 请及时更新");
    }
}
