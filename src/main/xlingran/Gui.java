package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import xlingran.gui.GuiType;
import xlingran.gui.XlrGuiHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Gui implements Listener {

    private static final int TEMPLATE_LIST_SIZE = 27;
    private static final int SETTINGS_SLOT_NAME = 10;
    private static final int SETTINGS_SLOT_LORE = 12;
    private static final int SETTINGS_SLOT_ITEMS = 14;
    private static final int SETTINGS_SLOT_MODE = 16;

    private final Shan plugin;
    private final HopperTemplateManager templateManager;
    private final PlayerGuiSession sessions;
    private final DataStore dataStore;

    public Gui(Shan plugin, HopperTemplateManager templateManager, PlayerGuiSession sessions, DataStore dataStore) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.sessions = sessions;
        this.dataStore = dataStore;
    }

    public void saveData() {
        dataStore.save(templateManager);
    }

    public void openTemplateList(Player player) {
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.TEMPLATE_LIST), 27,
                color("&6漏斗模板"));
        bindHolder(inv, GuiType.TEMPLATE_LIST);

        List<String> names = new ArrayList<>(templateManager.getTemplates(player.getUniqueId()).keySet());
        for (int i = 0; i < names.size() && i < TEMPLATE_LIST_SIZE; i++) {
            inv.setItem(i, createTemplateListItem(player, names.get(i)));
        }
        player.openInventory(inv);
    }

    public void openTemplateSettings(Player player, String templateName) {
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.TEMPLATE_SETTINGS), 27,
                color("&6模板设置"));
        bindHolder(inv, GuiType.TEMPLATE_SETTINGS);

        ItemStack glass = blackGlass();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            player.openInventory(inv);
            return;
        }

        inv.setItem(SETTINGS_SLOT_NAME, button(Material.GRASS_BLOCK, "&e名称过滤",
                List.of("&a将过滤你输入的名称", "&a左键点击查看过滤条件", "&a右键点击新增过滤条件")));
        inv.setItem(SETTINGS_SLOT_LORE, button(Material.BARRIER, "&eLore过滤",
                List.of("&a将过滤你输入的Lore", "&a左键点击查看过滤条件", "&a右键点击新增过滤条件")));
        inv.setItem(SETTINGS_SLOT_ITEMS, button(Material.CHEST, "&e过滤物品",
                List.of("&a将过滤你添加的物品")));
        inv.setItem(SETTINGS_SLOT_MODE, modeButton(template));

        player.openInventory(inv);
    }

    public void openFilterItems(Player player, String templateName) {
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.FILTER_ITEMS), 54,
                color("&6过滤的物品"));
        bindHolder(inv, GuiType.FILTER_ITEMS);

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template != null) {
            int slot = 0;
            for (Material mat : template.getMaterials()) {
                if (slot >= 54) {
                    break;
                }
                inv.setItem(slot++, new ItemStack(mat, 1));
            }
        }
        player.openInventory(inv);
    }

    public void openFilterTitles(Player player, String templateName) {
        openRuleList(player, templateName, GuiType.FILTER_TITLES, "&6过滤的名称",
                templateManager.getTemplate(player.getUniqueId(), templateName) != null
                        ? templateManager.getTemplate(player.getUniqueId(), templateName).getTitleRules()
                        : List.of());
    }

    public void openFilterLores(Player player, String templateName) {
        openRuleList(player, templateName, GuiType.FILTER_LORES, "&6过滤的描述",
                templateManager.getTemplate(player.getUniqueId(), templateName) != null
                        ? templateManager.getTemplate(player.getUniqueId(), templateName).getLoreRules()
                        : List.of());
    }

    private void openRuleList(Player player, String templateName, GuiType type, String title, List<String> rules) {
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(type), 54, color(title));
        bindHolder(inv, type);
        for (int i = 0; i < rules.size() && i < 54; i++) {
            inv.setItem(i, ruleNameTag(rules.get(i)));
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        XlrGuiHolder holder = XlrGuiHolder.from(top);
        if (holder == null) {
            return;
        }

        if (holder.getType() == GuiType.FILTER_ITEMS) {
            return;
        }

        event.setCancelled(true);
        if (GuiClickGuard.shouldIgnoreClick(sessions, player)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            GuiClickGuard.blockItemManipulation(event);
            return;
        }

        int slot = event.getSlot();
        UUID playerId = player.getUniqueId();

        switch (holder.getType()) {
            case TEMPLATE_LIST -> handleTemplateListClick(player, slot, event.getClick());
            case TEMPLATE_SETTINGS -> handleSettingsClick(player, slot, event.getClick());
            case FILTER_TITLES -> handleRuleListClick(player, slot, event.getClick(), true);
            case FILTER_LORES -> handleRuleListClick(player, slot, event.getClick(), false);
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        XlrGuiHolder holder = XlrGuiHolder.from(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (holder.getType() != GuiType.FILTER_ITEMS) {
            GuiClickGuard.cancelDrag(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        XlrGuiHolder holder = XlrGuiHolder.from(event.getInventory());
        if (holder == null) {
            return;
        }
        if (holder.getType() == GuiType.FILTER_ITEMS) {
            String templateName = sessions.getEditingTemplate(player.getUniqueId());
            if (templateName != null) {
                processFilterItemsClose(player, event.getInventory(), templateName);
                saveData();
            }
            return;
        }
        if (holder.getType() == GuiType.TEMPLATE_SETTINGS) {
            saveData();
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerGuiSession.InputMode mode = sessions.getInputMode(player.getUniqueId());
        if (mode == PlayerGuiSession.InputMode.NONE) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        String templateName = sessions.getEditingTemplate(player.getUniqueId());
        if (templateName == null) {
            sessions.clearInput(player.getUniqueId());
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
            if (template == null) {
                sessions.clearInput(player.getUniqueId());
                return;
            }
            if (mode == PlayerGuiSession.InputMode.TITLE) {
                template.addTitleRule(message);
            } else if (mode == PlayerGuiSession.InputMode.LORE) {
                template.addLoreRule(message);
            }
            sessions.clearInput(player.getUniqueId());
            saveData();
            player.sendMessage(color("&a已添加过滤规则"));
            openTemplateSettings(player, templateName);
        });
    }

    private void handleTemplateListClick(Player player, int slot, ClickType click) {
        if (slot < 0 || slot >= TEMPLATE_LIST_SIZE) {
            return;
        }
        List<String> names = new ArrayList<>(templateManager.getTemplates(player.getUniqueId()).keySet());
        if (slot >= names.size()) {
            return;
        }
        String templateName = names.get(slot);
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            templateManager.toggleTemplateEnabled(player, templateName);
            saveData();
            openTemplateList(player);
        } else if (click == ClickType.RIGHT) {
            openTemplateSettings(player, templateName);
        }
    }

    private void handleSettingsClick(Player player, int slot, ClickType click) {
        String templateName = sessions.getEditingTemplate(player.getUniqueId());
        if (templateName == null) {
            return;
        }
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            return;
        }
        switch (slot) {
            case SETTINGS_SLOT_NAME -> {
                if (click == ClickType.LEFT) {
                    openFilterTitles(player, templateName);
                } else if (click == ClickType.RIGHT) {
                    player.closeInventory();
                    sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.TITLE);
                    player.sendMessage(color("&a请输入要过滤的物品名称"));
                }
            }
            case SETTINGS_SLOT_LORE -> {
                if (click == ClickType.LEFT) {
                    openFilterLores(player, templateName);
                } else if (click == ClickType.RIGHT) {
                    player.closeInventory();
                    sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.LORE);
                    player.sendMessage(color("&a请输入要过滤的物品描述"));
                }
            }
            case SETTINGS_SLOT_ITEMS -> openFilterItems(player, templateName);
            case SETTINGS_SLOT_MODE -> {
                if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                    template.toggleWhitelist();
                    saveData();
                    openTemplateSettings(player, templateName);
                }
            }
            default -> {
            }
        }
    }

    private void handleRuleListClick(Player player, int slot, ClickType click, boolean titleRules) {
        if (click != ClickType.LEFT) {
            return;
        }
        String templateName = sessions.getEditingTemplate(player.getUniqueId());
        if (templateName == null) {
            return;
        }
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            return;
        }
        List<String> rules = titleRules ? template.getTitleRules() : template.getLoreRules();
        if (slot < 0 || slot >= rules.size()) {
            return;
        }
        rules.remove(slot);
        saveData();
        if (titleRules) {
            openFilterTitles(player, templateName);
        } else {
            openFilterLores(player, templateName);
        }
    }

    private void processFilterItemsClose(Player player, Inventory inventory, String templateName) {
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            return;
        }
        Map<Material, Integer> counts = new HashMap<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }

        template.getMaterials().clear();
        List<ItemStack> toReturn = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            Material mat = entry.getKey();
            int total = entry.getValue();
            template.getMaterials().add(mat);
            if (total > 1) {
                toReturn.add(new ItemStack(mat, total - 1));
            }
        }

        for (ItemStack stack : toReturn) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private ItemStack createTemplateListItem(Player player, String templateName) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(color("&a" + templateName));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7左键 开/关"));
        lore.add(color("&7右键 编辑"));
        if (templateManager.isTemplateEnabled(player.getUniqueId(), templateName)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            lore.add(color("&a当前模式: &a开"));
        } else {
            lore.add(color("&a当前模式: &c关"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack modeButton(HopperTemplate template) {
        String modeLine = template.isWhitelist()
                ? "&a白名单模式"
                : "&c黑名单模式";
        return button(Material.REDSTONE_BLOCK, "&e过滤模式", List.of(
                "&7仅作用于过滤物品",
                "当前模式: " + modeLine));
    }

    private ItemStack ruleNameTag(String rule) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&a" + rule));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setLore(List.of(color("&7左键删除")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(color(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack blackGlass() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void bindHolder(Inventory inv, GuiType type) {
        if (inv.getHolder() instanceof XlrGuiHolder holder) {
            holder.bind(inv);
        }
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
