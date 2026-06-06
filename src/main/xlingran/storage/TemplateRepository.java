package xlingran.storage;

import org.bukkit.Material;
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

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class TemplateRepository {

    private static final String LIST_FILTER = "filter";
    private static final String LIST_CRAFT = "auto-craft";
    private static final String LIST_SMELT = "auto-smelt";

    private final JavaPlugin plugin;
    private final ShanDatabase database;
    private final Logger logger;
    private BukkitTask flushTask;
    private volatile boolean dirty;

    public TemplateRepository(JavaPlugin plugin, ShanDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
    }

    public void loadInto(HopperTemplateManager manager) throws Exception {
        manager.clearAll();
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
                    loadItemList(conn, templateId, LIST_FILTER, template.getFilterPrototypes());
                    loadItemList(conn, templateId, LIST_CRAFT, template.getAutoCraftTargets());
                    loadItemList(conn, templateId, LIST_SMELT, template.getAutoSmeltOutputs());
                    loadEnchants(conn, templateId, template);
                    manager.putTemplate(uuid, name, template);
                }
            }
        }
    }

    private void loadItemList(Connection conn, long templateId, String listType, List<ItemStack> target) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_blob FROM template_item_lists WHERE template_id=? AND list_type=? ORDER BY item_index")) {
            ps.setLong(1, templateId);
            ps.setString(2, listType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack stack = deserializeBlob(rs.getString("item_blob"));
                    if (stack != null) {
                        target.add(stack);
                    }
                }
            }
        }
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
                    long id = insertTemplate(conn, uuid, entry.getKey(), entry.getValue());
                    saveItemList(conn, id, LIST_FILTER, entry.getValue().getFilterPrototypes());
                    saveItemList(conn, id, LIST_CRAFT, entry.getValue().getAutoCraftTargets());
                    saveItemList(conn, id, LIST_SMELT, entry.getValue().getAutoSmeltOutputs());
                    saveEnchants(conn, id, entry.getValue());
                }
            }
            conn.commit();
        }
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

    private void saveItemList(Connection conn, long templateId, String listType, List<ItemStack> stacks) throws Exception {
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

    /** 定期将未落盘的脏数据写入 shan.db（防崩溃/kill 丢失）。 */
    public void startPeriodicSave(HopperTemplateManager manager) {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!dirty) {
                return;
            }
            dirty = false;
            try {
                saveAll(manager);
            } catch (Exception e) {
                logger.severe("[XLRHopper] 定期保存 shan.db 失败: " + e.getMessage());
                dirty = true;
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
            dirty = false;
            HopperTemplateManager manager = xlingran.Shan.getInstance().getTemplateManager();
            try {
                saveAll(manager);
            } catch (Exception e) {
                logger.severe("[XLRHopper] 异步保存 shan.db 失败: " + e.getMessage());
                dirty = true;
            }
        }, 20L);
    }

    public void flushSync(HopperTemplateManager manager) {
        dirty = false;
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        try {
            saveAll(manager);
        } catch (Exception e) {
            logger.severe("[XLRHopper] 保存 shan.db 失败: " + e.getMessage());
        }
    }

    private static String serializeBlob(ItemStack stack) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("item", stack.serialize());
        return Base64.getEncoder().encodeToString(yml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ItemStack deserializeBlob(String blob) {
        if (blob == null || blob.isEmpty()) {
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
                return ItemStackUtil.clonePrototype(stack);
            }
        } catch (Exception ignored) {
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
