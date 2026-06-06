package xlingran.storage;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xlingran.HopperTemplate;
import xlingran.HopperTemplateManager;
import xlingran.ItemStackUtil;
import xlingran.Shan;
import xlingran.XLRHopperConfig;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class TemplateRepository {

    private static final String LIST_FILTER = "filter";
    private static final String LIST_CRAFT = "auto-craft";
    private static final String LIST_SMELT = "auto-smelt";
    private static final String BYTES_BLOB_PREFIX = "b:";

    private final JavaPlugin plugin;
    private final ShanDatabase database;
    private final Logger logger;
    private final Object saveLock = new Object();
    private BukkitTask flushTask;
    private BukkitTask periodicSaveTask;
    private volatile boolean dirty;
    private volatile boolean dataLoaded;

    public TemplateRepository(JavaPlugin plugin, ShanDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
    }

    public boolean isDataLoaded() {
        return dataLoaded;
    }

    public void loadInto(HopperTemplateManager manager) throws Exception {
        dataLoaded = false;
        logStorageDebug("loadInto 开始");
        manager.clearAll();
        int templateCount = 0;
        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet players = st.executeQuery("SELECT uuid, enabled_template FROM players")) {
            while (players.next()) {
                UUID uuid = UUID.fromString(players.getString("uuid"));
                String enabled = players.getString("enabled_template");
                if (enabled != null && !enabled.isEmpty()) {
                    manager.setEnabledTemplate(uuid, enabled);
                }
            }
        }
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, player_uuid, name, whitelist, auto_destroy, auto_craft_enabled, "
                             + "auto_smelt_enabled, durability_threshold FROM templates")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long templateId = rs.getLong("id");
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String name = rs.getString("name");
                    HopperTemplate template = new HopperTemplate();
                    template.setWhitelist(rs.getInt("whitelist") != 0);
                    template.setAutoDestroy(rs.getInt("auto_destroy") != 0);
                    template.loadAutomationFlags(
                            rs.getInt("auto_craft_enabled") != 0,
                            rs.getInt("auto_smelt_enabled") != 0);
                    int dur = rs.getInt("durability_threshold");
                    if (!rs.wasNull()) {
                        template.setDurabilityThreshold(dur);
                    }
                    int filterCount = loadItemList(conn, templateId, LIST_FILTER, template.getFilterPrototypes(), name);
                    int craftCount = loadItemList(conn, templateId, LIST_CRAFT, template.getAutoCraftTargets(), name);
                    int smeltCount = loadItemList(conn, templateId, LIST_SMELT, template.getAutoSmeltOutputs(), name);
                    loadEnchants(conn, templateId, template);
                    manager.putTemplate(uuid, name, template);
                    templateCount++;
                    logStorageDebug("loadInto 模板=" + name + " player=" + uuid + " filter=" + filterCount
                            + " craft=" + craftCount + " smelt=" + smeltCount);
                }
            }
        }
        dataLoaded = true;
        logStorageDebug("loadInto 完成，共 " + templateCount + " 个模板");
    }

    private int loadItemList(Connection conn, long templateId, String listType,
                             java.util.List<ItemStack> target, String templateName) throws Exception {
        int loaded = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_blob FROM template_item_lists WHERE template_id=? AND list_type=? ORDER BY item_index")) {
            ps.setLong(1, templateId);
            ps.setString(2, listType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack stack = deserializeBlob(rs.getString("item_blob"), templateId, listType, templateName);
                    if (stack != null) {
                        target.add(stack);
                        loaded++;
                    }
                }
            }
        }
        return loaded;
    }

    private void loadEnchants(Connection conn, long templateId, HopperTemplate template) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT enchant_key, min_level FROM template_enchants WHERE template_id=?")) {
            ps.setLong(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Enchantment enchant = resolveEnchantment(rs.getString("enchant_key"));
                    if (enchant != null) {
                        template.setEnchantMinLevel(enchant, rs.getInt("min_level"));
                    }
                }
            }
        }
    }

    public void saveAll(HopperTemplateManager manager) throws Exception {
        synchronized (saveLock) {
            saveAllUnlocked(manager, false);
        }
    }

    private boolean saveAllUnlocked(HopperTemplateManager manager, boolean force) throws Exception {
        if (!force && !dataLoaded) {
            logger.warning("[XLRHopper] 跳过保存：模板数据尚未从 shan.db 加载完成");
            logStorageDebug("跳过 saveAll：数据未加载");
            dirty = true;
            return false;
        }
        logStorageDebug("saveAll 开始" + (force ? "（强制）" : ""));
        int templateCount = 0;
        int filterRows = 0;
        int craftRows = 0;
        int smeltRows = 0;
        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM template_enchants");
                st.executeUpdate("DELETE FROM template_item_lists");
                st.executeUpdate("DELETE FROM templates");
                st.executeUpdate("DELETE FROM players");
            }
            for (UUID uuid : manager.getAllPlayerTemplates().keySet()) {
                String enabled = manager.getEnabledTemplateName(uuid);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO players (uuid, enabled_template) VALUES (?, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, enabled);
                    ps.executeUpdate();
                }
                for (Map.Entry<String, HopperTemplate> entry : manager.getTemplates(uuid).entrySet()) {
                    String templateName = entry.getKey();
                    long id = insertTemplate(conn, uuid, templateName, entry.getValue());
                    int f = saveItemList(conn, id, LIST_FILTER, entry.getValue().getFilterPrototypes());
                    int c = saveItemList(conn, id, LIST_CRAFT, entry.getValue().getAutoCraftTargets());
                    int s = saveItemList(conn, id, LIST_SMELT, entry.getValue().getAutoSmeltOutputs());
                    saveEnchants(conn, id, entry.getValue());
                    filterRows += f;
                    craftRows += c;
                    smeltRows += s;
                    templateCount++;
                    logStorageDebug("saveAll 写入模板=" + templateName + " player=" + uuid
                            + " filter=" + f + " craft=" + c + " smelt=" + s);
                }
            }
            conn.commit();
        }
        logStorageDebug("saveAll 完成，模板数=" + templateCount + " filter行=" + filterRows
                + " craft行=" + craftRows + " smelt行=" + smeltRows);
        return true;
    }

    private long insertTemplate(Connection conn, UUID uuid, String name, HopperTemplate t) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO templates (player_uuid, name, whitelist, auto_destroy, auto_craft_enabled, "
                        + "auto_smelt_enabled, durability_threshold) VALUES (?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, t.isWhitelist() ? 1 : 0);
            ps.setInt(4, t.isAutoDestroy() ? 1 : 0);
            ps.setInt(5, t.isAutoCraftEnabled() ? 1 : 0);
            ps.setInt(6, t.isAutoSmeltEnabled() ? 1 : 0);
            if (t.getDurabilityThreshold() != null) {
                ps.setInt(7, t.getDurabilityThreshold());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new IllegalStateException("insert template failed");
    }

    private int saveItemList(Connection conn, long templateId, String listType,
                             java.util.List<ItemStack> stacks) throws Exception {
        int index = 0;
        for (ItemStack stack : stacks) {
            ItemStack proto = ItemStackUtil.clonePrototype(stack);
            if (proto == null) {
                continue;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO template_item_lists (template_id, list_type, item_index, item_blob) VALUES (?,?,?,?)")) {
                ps.setLong(1, templateId);
                ps.setString(2, listType);
                ps.setInt(3, index++);
                ps.setString(4, serializeBlob(proto));
                ps.executeUpdate();
            }
        }
        return index;
    }

    private void saveEnchants(Connection conn, long templateId, HopperTemplate t) throws Exception {
        for (Map.Entry<Enchantment, Integer> e : t.getEnchantMinLevels().entrySet()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO template_enchants (template_id, enchant_key, min_level) VALUES (?,?,?)")) {
                ps.setLong(1, templateId);
                ps.setString(2, e.getKey().getKey().getKey());
                ps.setInt(3, e.getValue());
                ps.executeUpdate();
            }
        }
    }

    public void markDirty() {
        dirty = true;
        scheduleFlush();
    }

    /** 定期将未落盘的脏数据写入 shan.db（防崩溃/kill 丢失）；须在 loadInto 完成后调用。 */
    public void startPeriodicSave(HopperTemplateManager manager) {
        if (periodicSaveTask != null) {
            periodicSaveTask.cancel();
        }
        periodicSaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!dirty) {
                return;
            }
            synchronized (saveLock) {
                if (!dirty) {
                    return;
                }
                if (!dataLoaded) {
                    logger.warning("[XLRHopper] 定期保存跳过：模板数据尚未加载完成");
                    logStorageDebug("定期保存跳过：数据未加载");
                    return;
                }
                dirty = false;
                try {
                    saveAllUnlocked(manager, false);
                } catch (Exception e) {
                    logger.severe("[XLRHopper] 定期保存 shan.db 失败: " + e.getMessage());
                    dirty = true;
                }
            }
        }, 20L * 60L * 2L, 20L * 60L * 2L);
    }

    private void scheduleFlush() {
        if (flushTask != null) {
            return;
        }
        flushTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            flushTask = null;
            if (!dirty) {
                return;
            }
            synchronized (saveLock) {
                if (!dirty) {
                    return;
                }
                if (!dataLoaded) {
                    logger.warning("[XLRHopper] 异步保存跳过：模板数据尚未加载完成");
                    logStorageDebug("scheduleFlush 跳过：数据未加载");
                    return;
                }
                dirty = false;
                HopperTemplateManager manager = Shan.getInstance().getTemplateManager();
                try {
                    saveAllUnlocked(manager, false);
                } catch (Exception e) {
                    logger.severe("[XLRHopper] 异步保存 shan.db 失败: " + e.getMessage());
                    dirty = true;
                }
            }
        }, 20L);
    }

    public void flushSync(HopperTemplateManager manager) {
        flushSync(manager, false);
    }

    /** @param force 关服等场景强制落盘，绕过 dataLoaded 门禁 */
    public void flushSync(HopperTemplateManager manager, boolean force) {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        synchronized (saveLock) {
            logger.info("[XLRHopper] flushSync 开始 force=" + force + " dataLoaded=" + dataLoaded);
            if (!force && !dataLoaded) {
                logger.warning("[XLRHopper] flushSync 跳过：模板数据尚未加载完成");
                logStorageDebug("flushSync 跳过：数据未加载");
                dirty = true;
                return;
            }
            logStorageDebug("flushSync 开始" + (force ? "（强制）" : ""));
            dirty = false;
            try {
                boolean saved = saveAllUnlocked(manager, force);
                if (saved) {
                    logger.info("[XLRHopper] flushSync 完成，已写入 shan.db");
                } else {
                    logger.warning("[XLRHopper] flushSync 未完成：saveAll 被跳过或失败");
                }
                logStorageDebug("flushSync 完成");
            } catch (Exception e) {
                logger.severe("[XLRHopper] 保存 shan.db 失败: " + e.getMessage());
                dirty = true;
            }
        }
    }

    private void logStorageDebug(String message) {
        XLRHopperConfig config = pluginConfig();
        if (config != null && config.isDebugTemplateStorage()) {
            logger.info("[XLRHopper][存储调试] " + message);
        }
    }

    private static XLRHopperConfig pluginConfig() {
        Shan shan = Shan.getInstance();
        return shan != null ? shan.getPluginConfig() : null;
    }

    private static String serializeBlob(ItemStack stack) {
        byte[] bytes = trySerializeAsBytes(stack);
        if (bytes != null) {
            return BYTES_BLOB_PREFIX + Base64.getEncoder().encodeToString(bytes);
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("item", stack.serialize());
        return Base64.getEncoder().encodeToString(yml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ItemStack deserializeBlob(String blob, long templateId, String listType, String templateName) {
        if (blob == null || blob.isEmpty()) {
            return null;
        }
        if (blob.startsWith(BYTES_BLOB_PREFIX)) {
            try {
                byte[] raw = Base64.getDecoder().decode(blob.substring(BYTES_BLOB_PREFIX.length()));
                ItemStack stack = tryDeserializeBytes(raw);
                if (stack != null) {
                    return ItemStackUtil.clonePrototype(stack);
                }
            } catch (Exception e) {
                logger.warning("[XLRHopper] 反序列化物品失败 (template=" + templateName + ", id=" + templateId
                        + ", list=" + listType + ", format=bytes): " + e.getMessage());
            }
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(blob);
            YamlConfiguration yml = new YamlConfiguration();
            yml.loadFromString(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
            Object map = yml.get("item");
            if (map instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                ItemStack stack = ItemStack.deserialize((Map<String, Object>) m);
                ItemStack proto = ItemStackUtil.clonePrototype(stack);
                if (proto != null && isDebugEnabled()) {
                    logger.info("[XLRHopper][存储调试] 反序列化 legacy 成功 template=" + templateName
                            + " list=" + listType + " material=" + proto.getType());
                }
                return proto;
            }
        } catch (Exception e) {
            logger.warning("[XLRHopper] 反序列化物品失败 (template=" + templateName + ", id=" + templateId
                    + ", list=" + listType + ", format=legacy): " + e.getMessage());
        }
        return null;
    }

    private static boolean isDebugEnabled() {
        XLRHopperConfig config = pluginConfig();
        return config != null && config.isDebugTemplateStorage();
    }

    private static byte[] trySerializeAsBytes(ItemStack stack) {
        try {
            Method method = ItemStack.class.getMethod("serializeAsBytes");
            return (byte[]) method.invoke(stack);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static ItemStack tryDeserializeBytes(byte[] raw) {
        try {
            Method method = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            return (ItemStack) method.invoke(null, raw);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Enchantment resolveEnchantment(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase()));
    }
}
