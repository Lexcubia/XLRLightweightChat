package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动时一次性版本检测与 CDN 鉴权请求；完成后无运行时状态残留。
 */
public final class VersionChecker {

    private static final String CDN_HOST = "https://plugin.xlingran.com";
    private static final String VERSION_URI = "/XLRHopperVersion.txt";
    private static final int TIMEOUT_MS = 5000;

    private static final byte[] AUTH_KEY_OBF = {
            52, 32, 38, 50, 36, 70, 55, 45, 42, 108, 42, 36, 41, 93, 2, 72,
            93, 48, 17, 1, 39, 14, 30, 19, 18, 53, 51, 22, 28, 96, 30, 7
    };
    private static final byte[] AUTH_KEY_MASK = {
            88, 76, 82, 72, 111, 112, 112, 101, 114
    };

    private static final AtomicBoolean CONSUMED = new AtomicBoolean(false);

    private VersionChecker() {
    }

    public static void checkOnEnable(JavaPlugin plugin) {
        if (!CONSUMED.compareAndSet(false, true)) {
            return;
        }
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
        String url = buildAuthenticatedUrl();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
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

    private static String buildAuthenticatedUrl() {
        long timestamp = System.currentTimeMillis() / 1000L;
        String pkey = decodeAuthKey();
        String raw = pkey + VERSION_URI + timestamp;
        String sign = md5Hex(raw);
        return CDN_HOST + VERSION_URI + "?sign=" + sign + "&t=" + timestamp;
    }

    private static String decodeAuthKey() {
        char[] chars = new char[AUTH_KEY_OBF.length];
        for (int i = 0; i < AUTH_KEY_OBF.length; i++) {
            chars[i] = (char) (AUTH_KEY_OBF[i] ^ AUTH_KEY_MASK[i % AUTH_KEY_MASK.length]);
        }
        return new String(chars);
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
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
