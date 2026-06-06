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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
    private volatile boolean loadHealthy = true;

    public TemplateRepository(JavaPlugin plugin, ShanDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
    }

    public boolean isDataLoaded() {
        return dataLoaded;
    }

    public boolean isLoadHealthy() {
        return loadHealthy;
    }

    public record PendingEnchant(String key, int level) {
    }

    public record PendingTemplate(
            long id,
            UUID playerUuid,
            String name,
            boolean whitelist,
            boolean autoDestroy,
            boolean autoCraftEnabled,
            boolean autoSmeltEnabled,
            Integer durabilityThreshold,
            List<String> filterBlobs,
            List<String> craftBlobs,
            List<String> smeltBlobs,
            List<PendingEnchant> enchants) {
    }

    public record PendingPlayer(UUID uuid, String enabledTemplate) {
    }

    public record LoadSnapshot(List<PendingTemplate> templates, List<PendingPlayer> players) {
    }

    /** 异步线程读取 shan.db 原始行（不反序列化 ItemStack）。 */
    public LoadSnapshot captureSnapshot() throws Exception {
        List<PendingTemplate> templates = new ArrayList<>();
        List<PendingPlayer> players = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, player_uuid, name, whitelist, auto_destroy, auto_craft_enabled, "
                             + "auto_smelt_enabled, durability_threshold FROM templates")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long templateId = rs.getLong("id");
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String name = rs.getString("name");
                    Integer dur = null;
                    int durVal = rs.getInt("durability_threshold");
                    if (!rs.wasNull()) {
                        dur = durVal;
                    }
                    templates.add(new PendingTemplate(
                            templateId,
                            uuid,
                            name,
                            rs.getInt("whitelist") != 0,
                            rs.getInt("auto_destroy") != 0,
                            rs.getInt("auto_craft_enabled") != 0,
                            rs.getInt("auto_smelt_enabled") != 0,
                            dur,
                            readBlobList(conn, templateId, LIST_FILTER),
                            readBlobList(conn, templateId, LIST_CRAFT),
                            readBlobList(conn, templateId, LIST_SMELT),
                            readEnchantList(conn, templateId)));
                }
            }
        }
        try (Connection conn = database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, enabled_template FROM players")) {
            while (rs.next()) {
                players.add(new PendingPlayer(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("enabled_template")));
            }
        }
        return new LoadSnapshot(templates, players);
    }

    /** 须在主线程调用：反序列化物品并写入内存。 */
    public void applySnapshot(HopperTemplateManager manager, LoadSnapshot snapshot) throws Exception {
        cancelPendingFlush();
        synchronized (saveLock) {
            dataLoaded = false;
            loadHealthy = true;
            dirty = false;
            logStorageDebug("loadInto 开始");
            manager.clearAll();
            int templateCount = 0;
            for (PendingTemplate pending : snapshot.templates()) {
                HopperTemplate template = new HopperTemplate();
                template.setWhitelist(pending.whitelist());
                template.setAutoDestroy(pending.autoDestroy());
                template.loadAutomationFlags(pending.autoCraftEnabled(), pending.autoSmeltEnabled());
                if (pending.durabilityThreshold() != null) {
                    template.setDurabilityThreshold(pending.durabilityThreshold());
                }
                LoadListResult filterResult = applyBlobList(pending.filterBlobs(), template.getFilterPrototypes(),
                        pending.name(), pending.id(), LIST_FILTER);
                LoadListResult craftResult = applyBlobList(pending.craftBlobs(), template.getAutoCraftTargets(),
                        pending.name(), pending.id(), LIST_CRAFT);
                LoadListResult smeltResult = applyBlobList(pending.smeltBlobs(), template.getAutoSmeltOutputs(),
                        pending.name(), pending.id(), LIST_SMELT);
                for (PendingEnchant enchant : pending.enchants()) {
                    Enchantment resolved = resolveEnchantment(enchant.key());
                    if (resolved != null) {
                        template.setEnchantMinLevel(resolved, enchant.level());
                    }
                }
                manager.putTemplate(pending.playerUuid(), pending.name(), template);
                templateCount++;
                if (filterResult.failed() > 0 || craftResult.failed() > 0 || smeltResult.failed() > 0) {
                    loadHealthy = false;
                }
                logStorageTrace("loadInto 模板=" + pending.name() + " player=" + pending.playerUuid()
                        + " filter=" + filterResult.loaded() + "/" + filterResult.dbRows()
                        + " craft=" + craftResult.loaded() + "/" + craftResult.dbRows()
                        + " smelt=" + smeltResult.loaded() + "/" + smeltResult.dbRows());
                logStorageDebug("loadInto 模板=" + pending.name() + " player=" + pending.playerUuid()
                        + " filter=" + filterResult.loaded() + " craft=" + craftResult.loaded()
                        + " smelt=" + smeltResult.loaded());
            }
            for (PendingPlayer player : snapshot.players()) {
                if (player.enabledTemplate() != null && !player.enabledTemplate().isEmpty()) {
                    manager.setEnabledTemplate(player.uuid(), player.enabledTemplate());
                }
            }
            dataLoaded = true;
            logStorageTrace("loadInto 完成，共 " + templateCount + " 个模板 loadHealthy=" + loadHealthy);
            logStorageDebug("loadInto 完成，共 " + templateCount + " 个模板");
        }
    }

    public void loadInto(HopperTemplateManager manager) throws Exception {
        applySnapshot(manager, captureSnapshot());
    }

    /**
     * 打开存储 GUI 前，若内存列表为空则从 shan.db 补载（须在主线程调用）。
     *
     * @return 补载成功的物品条数
     */
    public int reloadItemListIfEmpty(UUID playerUuid, String templateName, String listType,
                                     List<ItemStack> target) {
        if (!dataLoaded || !target.isEmpty()) {
            return 0;
        }
        synchronized (saveLock) {
            try (Connection conn = database.getConnection()) {
                Long templateId = findTemplateId(conn, playerUuid, templateName);
                if (templateId == null) {
                    logStorageTrace("DB 补载跳过：模板不存在 template=" + templateName + " player=" + playerUuid);
                    return 0;
                }
                LoadListResult result = loadItemList(conn, templateId, listType, target, templateName);
                if (result.loaded() > 0) {
                    logStorageTrace("DB 补载 template=" + templateName + " list=" + listType
                            + " loaded=" + result.loaded() + "/" + result.dbRows());
                } else if (result.dbRows() > 0) {
                    loadHealthy = false;
                }
                return result.loaded();
            } catch (Exception e) {
                logger.warning("[XLRHopper][存储] DB 补载失败 template=" + templateName + " list=" + listType
                        + ": " + e.getMessage());
                return 0;
            }
        }
    }

    private List<String> readBlobList(Connection conn, long templateId, String listType) throws Exception {
        List<String> blobs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_blob FROM template_item_lists WHERE template_id=? AND list_type=? ORDER BY item_index")) {
            ps.setLong(1, templateId);
            ps.setString(2, listType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    blobs.add(rs.getString("item_blob"));
                }
            }
        }
        return blobs;
    }

    private List<PendingEnchant> readEnchantList(Connection conn, long templateId) throws Exception {
        List<PendingEnchant> enchants = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT enchant_key, min_level FROM template_enchants WHERE template_id=?")) {
            ps.setLong(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    enchants.add(new PendingEnchant(rs.getString("enchant_key"), rs.getInt("min_level")));
                }
            }
        }
        return enchants;
    }

    private LoadListResult applyBlobList(List<String> blobs, List<ItemStack> target, String templateName,
                                         long templateId, String listType) {
        int loaded = 0;
        for (String blob : blobs) {
            ItemStack stack = deserializeBlob(blob, templateId, listType, templateName);
            if (stack != null) {
                target.add(stack);
                loaded++;
            }
        }
        int dbRows = blobs.size();
        if (dbRows > loaded) {
            logger.warning("[XLRHopper][存储] loadInto 反序列化失败 template=" + templateName + " list=" + listType
                    + " db行=" + dbRows + " 成功=" + loaded + " 失败=" + (dbRows - loaded));
        }
        return new LoadListResult(loaded, dbRows);
    }

    private record LoadListResult(int loaded, int dbRows) {
        int failed() {
            return dbRows - loaded;
        }
    }

    private LoadListResult loadItemList(Connection conn, long templateId, String listType,
                                        List<ItemStack> target, String templateName) throws Exception {
        int loaded = 0;
        int dbRows = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_blob FROM template_item_lists WHERE template_id=? AND list_type=? ORDER BY item_index")) {
            ps.setLong(1, templateId);
            ps.setString(2, listType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dbRows++;
                    ItemStack stack = deserializeBlob(rs.getString("item_blob"), templateId, listType, templateName);
                    if (stack != null) {
                        target.add(stack);
                        loaded++;
                    }
                }
            }
        }
        if (dbRows > loaded) {
            logger.warning("[XLRHopper][存储] loadInto 反序列化失败 template=" + templateName + " list=" + listType
                    + " db行=" + dbRows + " 成功=" + loaded + " 失败=" + (dbRows - loaded));
        }
        return new LoadListResult(loaded, dbRows);
    }

    private Long findTemplateId(Connection conn, UUID playerUuid, String templateName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM templates WHERE player_uuid=? AND name=?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, templateName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return null;
    }

    public void saveAll(HopperTemplateManager manager) throws Exception {
        synchronized (saveLock) {
            saveAllUnlocked(manager, false, false);
        }
    }

    private boolean saveAllUnlocked(HopperTemplateManager manager, boolean force, boolean allowEmptyItemLists)
            throws Exception {
        if (!force && !dataLoaded) {
            logger.warning("[XLRHopper] 跳过保存：模板数据尚未从 shan.db 加载完成");
            logStorageDebug("跳过 saveAll：数据未加载");
            dirty = true;
            return false;
        }
        if (!force && !loadHealthy) {
            logger.warning("[XLRHopper] 跳过保存：模板数据加载不完整（存在反序列化失败）");
            logStorageDebug("跳过 saveAll：loadHealthy=false");
            dirty = true;
            return false;
        }
        logStorageDebug("saveAll 开始" + (force ? "（强制）" : ""));
        int templateCount = 0;
        int filterRows = 0;
        int craftRows = 0;
        int smeltRows = 0;
        try (Connection conn = database.getConnection()) {
            if (!force && !allowEmptyItemLists) {
                int dbItems = countItemListRows(conn);
                int memItems = countMemoryItemRows(manager);
                if (dbItems > 0 && memItems == 0) {
                    logger.warning("[XLRHopper] 跳过保存：内存物品列表为空但 shan.db 含 " + dbItems + " 行物品");
                    logStorageDebug("跳过 saveAll：内存空但 DB 有物品");
                    dirty = true;
                    return false;
                }
            }
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
            logStorageTrace("saveAll 校验 DB 物品行=" + countItemListRows(conn)
                    + " smelt=" + countListTypeRows(conn, LIST_SMELT));
        }
        logStorageDebug("saveAll 完成，模板数=" + templateCount + " filter行=" + filterRows
                + " craft行=" + craftRows + " smelt行=" + smeltRows);
        return true;
    }

    private int countItemListRows(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM template_item_lists")) {
            return rs.next() ? rs.getInt("c") : 0;
        }
    }

    private int countListTypeRows(Connection conn, String listType) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) AS c FROM template_item_lists WHERE list_type=?")) {
            ps.setString(1, listType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("c") : 0;
            }
        }
    }

    private int countMemoryItemRows(HopperTemplateManager manager) {
        int total = 0;
        for (Map.Entry<UUID, Map<String, HopperTemplate>> playerEntry : manager.getAllPlayerTemplates().entrySet()) {
            for (HopperTemplate template : playerEntry.getValue().values()) {
                total += template.getFilterPrototypes().size();
                total += template.getAutoCraftTargets().size();
                total += template.getAutoSmeltOutputs().size();
            }
        }
        return total;
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
                             List<ItemStack> stacks) throws Exception {
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
                if (!loadHealthy) {
                    logger.warning("[XLRHopper] 定期保存跳过：模板数据加载不完整");
                    logStorageDebug("定期保存跳过：loadHealthy=false");
                    return;
                }
                dirty = false;
                try {
                    saveAllUnlocked(manager, false, false);
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
                if (!loadHealthy) {
                    logger.warning("[XLRHopper] 异步保存跳过：模板数据加载不完整");
                    logStorageDebug("scheduleFlush 跳过：loadHealthy=false");
                    return;
                }
                dirty = false;
                HopperTemplateManager manager = Shan.getInstance().getTemplateManager();
                try {
                    saveAllUnlocked(manager, false, false);
                } catch (Exception e) {
                    logger.severe("[XLRHopper] 异步保存 shan.db 失败: " + e.getMessage());
                    dirty = true;
                }
            }
        }, 20L);
    }

    private void cancelPendingFlush() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    public void flushSync(HopperTemplateManager manager) {
        flushSync(manager, false);
    }

    /** @param force 关服等场景强制落盘，绕过 dataLoaded 门禁 */
    public void flushSync(HopperTemplateManager manager, boolean force) {
        cancelPendingFlush();
        synchronized (saveLock) {
            logger.info("[XLRHopper] flushSync 开始 force=" + force + " dataLoaded=" + dataLoaded
                    + " loadHealthy=" + loadHealthy);
            if (!force && !dataLoaded) {
                logger.warning("[XLRHopper] flushSync 跳过：模板数据尚未加载完成");
                logStorageDebug("flushSync 跳过：数据未加载");
                dirty = true;
                return;
            }
            logStorageDebug("flushSync 开始" + (force ? "（强制）" : ""));
            dirty = false;
            try {
                boolean saved = saveAllUnlocked(manager, force, true);
                if (saved) {
                    if (!force) {
                        loadHealthy = true;
                    }
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

    private void logStorageTrace(String message) {
        logger.info("[XLRHopper][存储] " + message);
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
        return Base64.getEncoder().encodeToString(
                yml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ItemStack deserializeBlob(String blob, long templateId, String listType, String templateName) {
        if (blob == null || blob.isEmpty()) {
            return null;
        }
        if (blob.startsWith(BYTES_BLOB_PREFIX)) {
            try {
                byte[] raw = Base64.getDecoder().decode(blob.substring(BYTES_BLOB_PREFIX.length()));
                ItemStack stack = tryDeserializeBytes(raw, templateName, templateId, listType);
                if (stack != null) {
                    return ItemStackUtil.clonePrototype(stack);
                }
            } catch (Exception e) {
                logger.warning("[XLRHopper] 反序列化物品失败 (template=" + templateName + ", id=" + templateId
                        + ", list=" + listType + ", format=bytes): " + e.getMessage());
            }
            return null;
        }
        return deserializeLegacyYaml(blob, templateId, listType, templateName);
    }

    private ItemStack deserializeLegacyYaml(String blob, long templateId, String listType, String templateName) {
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
            logger.warning("[XLRHopper] 反序列化物品失败 (template=" + templateName + ", id=" + templateId
                    + ", list=" + listType + ", format=legacy): item 节点缺失或类型错误");
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

    private ItemStack tryDeserializeBytes(byte[] raw, String templateName, long templateId, String listType) {
        try {
            Method method = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            ItemStack stack = (ItemStack) method.invoke(null, raw);
            if (stack != null) {
                return stack;
            }
            logger.warning("[XLRHopper] 反序列化物品失败 (template=" + templateName + ", id=" + templateId
                    + ", list=" + listType + ", format=bytes): deserializeBytes 返回 null");
        } catch (ReflectiveOperationException e) {
            logger.warning("[XLRHopper] 反序列化物品失败 (template=" + templateName + ", id=" + templateId
                    + ", list=" + listType + ", format=bytes): " + e.getMessage());
        }
        return null;
    }

    private static Enchantment resolveEnchantment(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase()));
    }
}
