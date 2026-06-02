package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Registry;
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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
    private static final int TEMPLATE_SETTINGS_SIZE = 45;
    private static final int SETTINGS_SLOT_NAME = 10;
    private static final int SETTINGS_SLOT_LORE = 12;
    private static final int SETTINGS_SLOT_ITEMS = 14;
    private static final int SETTINGS_SLOT_MODE = 16;
    private static final int SETTINGS_SLOT_ENCHANT = 28;
    private static final int SETTINGS_SLOT_DURABILITY = 30;
    private static final int SETTINGS_SLOT_REMOTE = 32;
    private static final int SETTINGS_SLOT_BATCH = 34;

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
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.TEMPLATE_SETTINGS), TEMPLATE_SETTINGS_SIZE,
                color("&e模板设置: &b" + templateName));
        bindHolder(inv, GuiType.TEMPLATE_SETTINGS);

        ItemStack glass = blackGlass();
        for (int i = 0; i < TEMPLATE_SETTINGS_SIZE; i++) {
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
        inv.setItem(SETTINGS_SLOT_ENCHANT, button(Material.ENCHANTED_BOOK, "&e附魔过滤",
                List.of("&7点击进入附魔属性过滤")));
        inv.setItem(SETTINGS_SLOT_DURABILITY, durabilityButton(template));
        inv.setItem(SETTINGS_SLOT_REMOTE, button(Material.STRING, "&e远程传输",
                List.of("&7暂未开放")));
        inv.setItem(SETTINGS_SLOT_BATCH, button(Material.ENDER_EYE, "&e批量设置",
                List.of("&7点击后为漏斗批量套用本模板")));

        player.openInventory(inv);
    }

    public void openFilterEnchants(Player player, String templateName) {
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.FILTER_ENCHANTS), 54,
                color("&6过滤附魔属性"));
        bindHolder(inv, GuiType.FILTER_ENCHANTS);

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        List<Enchantment> enchants = listRegistryEnchantments();
        for (int i = 0; i < enchants.size() && i < 54; i++) {
            Enchantment enchant = enchants.get(i);
            Integer minLevel = template != null ? template.getEnchantMinLevels().get(enchant) : null;
            inv.setItem(i, createEnchantFilterBook(enchant, minLevel));
        }
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
            case FILTER_ENCHANTS -> handleFilterEnchantsClick(player, slot);
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
        Bukkit.getScheduler().runTask(plugin, () -> handleChatInput(player, message, templateName, mode));
    }

    private void handleChatInput(Player player, String message, String templateName, PlayerGuiSession.InputMode mode) {
        if (message.equalsIgnoreCase("xlrquit")) {
            if (mode == PlayerGuiSession.InputMode.BATCH_APPLY) {
                sessions.clearInput(player.getUniqueId());
                player.sendMessage(color("&7已退出批量设置模式"));
                openTemplateSettings(player, templateName);
                return;
            }
            if (mode == PlayerGuiSession.InputMode.ENCHANT_LEVEL) {
                sessions.clearInput(player.getUniqueId());
                player.sendMessage(color("&7已取消输入，返回过滤附魔属性"));
                openFilterEnchants(player, templateName);
                return;
            }
            sessions.clearInput(player.getUniqueId());
            player.sendMessage(color("&7已取消输入，返回模板设置"));
            openTemplateSettings(player, templateName);
            return;
        }

        if (mode == PlayerGuiSession.InputMode.BATCH_APPLY) {
            player.sendMessage(color("&7批量设置模式中，输入 xlrquit 退出"));
            return;
        }

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            sessions.clearInput(player.getUniqueId());
            return;
        }

        if (mode == PlayerGuiSession.InputMode.TITLE) {
            template.addTitleRule(message);
            sessions.clearInput(player.getUniqueId());
            saveData();
            player.sendMessage(color("&a已添加过滤规则"));
            openTemplateSettings(player, templateName);
            return;
        }
        if (mode == PlayerGuiSession.InputMode.LORE) {
            template.addLoreRule(message);
            sessions.clearInput(player.getUniqueId());
            saveData();
            player.sendMessage(color("&a已添加过滤规则"));
            openTemplateSettings(player, templateName);
            return;
        }
        if (mode == PlayerGuiSession.InputMode.DURABILITY) {
            Integer value = parsePositiveInt(message);
            if (value == null) {
                player.sendMessage(color("&c输入错误，请输入纯数字耐久度，或输入 xlrquit 退出设置"));
                return;
            }
            template.setDurabilityThreshold(value);
            sessions.clearInput(player.getUniqueId());
            saveData();
            player.sendMessage(color("&a已设置过滤耐久度: &b" + value));
            openTemplateSettings(player, templateName);
            return;
        }
        if (mode == PlayerGuiSession.InputMode.ENCHANT_LEVEL) {
            Enchantment pending = sessions.getPendingEnchant(player.getUniqueId());
            if (pending == null) {
                sessions.clearInput(player.getUniqueId());
                openFilterEnchants(player, templateName);
                return;
            }
            Integer level = parsePositiveInt(message);
            if (level == null) {
                player.sendMessage(color("&c输入错误，请重新输入附魔等级，或输入 xlrquit 退出设置"));
                return;
            }
            template.setEnchantMinLevel(pending, level);
            sessions.clearInput(player.getUniqueId());
            saveData();
            player.sendMessage(color("&a已设置附魔过滤: &e" + formatEnchantName(pending) + " &a" + level));
            openFilterEnchants(player, templateName);
        }
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
                    player.sendMessage(color("&a请输入要过滤的物品名称 &7(输入 xlrquit 取消)"));
                }
            }
            case SETTINGS_SLOT_LORE -> {
                if (click == ClickType.LEFT) {
                    openFilterLores(player, templateName);
                } else if (click == ClickType.RIGHT) {
                    player.closeInventory();
                    sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.LORE);
                    player.sendMessage(color("&a请输入要过滤的物品描述 &7(输入 xlrquit 取消)"));
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
            case SETTINGS_SLOT_ENCHANT -> openFilterEnchants(player, templateName);
            case SETTINGS_SLOT_DURABILITY -> {
                player.closeInventory();
                sessions.clearInput(player.getUniqueId());
                sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.DURABILITY);
                player.sendMessage(color("&a请在聊天栏输入数字耐久度 &7(低于该剩余耐久的物品将被过滤，输入 xlrquit 退出设置)"));
            }
            case SETTINGS_SLOT_REMOTE -> player.sendMessage(color("&7暂未开放"));
            case SETTINGS_SLOT_BATCH -> {
                player.closeInventory();
                sessions.clearInput(player.getUniqueId());
                sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.BATCH_APPLY);
                player.sendMessage(color("&a已进入批量设置模式，左键或右键点击漏斗套用模板"));
                player.sendMessage(color("&7输入 xlrquit 退出批量设置模式"));
            }
            default -> {
            }
        }
    }

    private void handleFilterEnchantsClick(Player player, int slot) {
        if (slot < 0 || slot >= 54) {
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
        List<Enchantment> enchants = listRegistryEnchantments();
        if (slot >= enchants.size()) {
            return;
        }
        Enchantment selected = enchants.get(slot);
        player.closeInventory();
        sessions.setPendingEnchant(player.getUniqueId(), selected);
        sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.ENCHANT_LEVEL);
        player.sendMessage(color("&a请输入附魔等级 &7(低于该等级的附魔将被过滤，输入 xlrquit 退出设置)"));
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

    private ItemStack durabilityButton(HopperTemplate template) {
        List<String> lore = new ArrayList<>();
        lore.add("&7点击设置最低剩余耐久");
        if (template.getDurabilityThreshold() != null) {
            lore.add("&a过滤耐久度: &b" + template.getDurabilityThreshold());
        }
        return button(Material.EMERALD, "&e耐久过滤", lore);
    }

    private ItemStack createEnchantFilterBook(Enchantment enchant, Integer minLevel) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta storageMeta)) {
            return book;
        }
        storageMeta.addStoredEnchant(enchant, 1, true);
        storageMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        storageMeta.setDisplayName(color("&e" + formatEnchantName(enchant)));
        List<String> lore = new ArrayList<>();
        if (minLevel != null) {
            lore.add(color("&a当前过滤: &e" + formatEnchantName(enchant) + " " + minLevel));
        }
        lore.add(color("&7点击设置最低等级"));
        storageMeta.setLore(lore);
        book.setItemMeta(storageMeta);
        return book;
    }

    private static String formatEnchantName(Enchantment enchant) {
        String key = enchant.getKey().getKey();
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private List<Enchantment> listRegistryEnchantments() {
        List<Enchantment> enchants = new ArrayList<>();
        for (Enchantment enchant : Registry.ENCHANTMENT) {
            enchants.add(enchant);
            if (enchants.size() >= 54) {
                plugin.getLogger().warning("[XLRHopper] 附魔数量超过 54，部分附魔未显示");
                break;
            }
        }
        return enchants;
    }

    private static Integer parsePositiveInt(String text) {
        if (text == null || !text.matches("[0-9]+")) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
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
