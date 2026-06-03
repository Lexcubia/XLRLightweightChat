package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.InventoryView;
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
    private static final int HOPPER_SETTINGS_SIZE = 27;
    private static final int HOPPER_SLOT_REDSTONE_LIST = 10;
    private static final int HOPPER_SLOT_REVERSE = 12;
    private static final int SETTINGS_SLOT_LINK_BOX = 10;
    private static final int SETTINGS_SLOT_AUTO_DESTROY = 12;
    private static final int SETTINGS_SLOT_ITEMS = 14;
    private static final int SETTINGS_SLOT_MODE = 16;
    private static final int BOX_LIST_SIZE = 27;
    private static final int BOX_STORAGE_SIZE = 54;
    private static final int SETTINGS_SLOT_ENCHANT = 28;
    private static final int SETTINGS_SLOT_DURABILITY = 30;
    private static final int SETTINGS_SLOT_REMOTE = 32;
    private static final int SETTINGS_SLOT_BATCH = 34;

    private final Shan plugin;
    private final HopperTemplateManager templateManager;
    private final PlayerGuiSession sessions;
    private final DataStore dataStore;
    private final PlayerBoxManager boxManager;
    private final HopperKeys hopperKeys;

    public Gui(Shan plugin, HopperTemplateManager templateManager, PlayerGuiSession sessions, DataStore dataStore,
               PlayerBoxManager boxManager, HopperKeys hopperKeys) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.sessions = sessions;
        this.dataStore = dataStore;
        this.boxManager = boxManager;
        this.hopperKeys = hopperKeys;
    }

    public void saveData() {
        dataStore.save(templateManager, boxManager);
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
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.TEMPLATE_SETTINGS, templateName),
                TEMPLATE_SETTINGS_SIZE, color("&e模板设置: &b" + templateName));
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

        inv.setItem(SETTINGS_SLOT_LINK_BOX, linkedBoxButton(template));
        inv.setItem(SETTINGS_SLOT_AUTO_DESTROY, toggleStateButton(Material.GRASS_BLOCK, "&e自动销毁",
                template.isAutoDestroy()));
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

    public void openBoxList(Player player) {
        sessions.clearLinkingBoxTemplate(player.getUniqueId());
        openBoxListInternal(player);
    }

    public void openBoxListForLink(Player player, String templateName) {
        sessions.setLinkingBoxTemplate(player.getUniqueId(), templateName);
        openBoxListInternal(player);
    }

    private void openBoxListInternal(Player player) {
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.BOX_LIST), BOX_LIST_SIZE,
                color("&e漏斗仓库"));
        bindHolder(inv, GuiType.BOX_LIST);

        List<String> names = boxManager.getBoxNames(player.getUniqueId());
        for (int i = 0; i < names.size() && i < BOX_LIST_SIZE; i++) {
            inv.setItem(i, boxListItem(names.get(i)));
        }
        player.openInventory(inv);
    }

    public void openBoxStorage(Player player, String boxName) {
        UUID playerId = player.getUniqueId();
        if (!boxManager.hasBox(playerId, boxName)) {
            player.sendMessage(color("&c仓库不存在: &b" + boxName));
            return;
        }
        ItemStack[] contents = boxManager.getBoxContents(playerId, boxName);
        sessions.setOpenBoxSnapshot(playerId, boxName, contents);

        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.BOX_STORAGE, boxName),
                BOX_STORAGE_SIZE, color("&e仓库: &b" + boxName));
        bindHolder(inv, GuiType.BOX_STORAGE);

        if (contents != null) {
            for (int i = 0; i < BOX_STORAGE_SIZE && i < contents.length; i++) {
                if (contents[i] != null && !contents[i].getType().isAir()) {
                    inv.setItem(i, contents[i].clone());
                }
            }
        }
        player.openInventory(inv);
    }

    /** 漏斗写入链接仓库后，刷新玩家当前打开的仓库界面，避免关闭 GUI 时用旧快照覆盖新入库。 */
    public void refreshOpenBoxStorage(UUID ownerId, String boxName) {
        if (ownerId == null || boxName == null || boxName.isEmpty()) {
            return;
        }
        ItemStack[] live = boxManager.getBoxContents(ownerId, boxName);
        if (live == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getUniqueId().equals(ownerId)) {
                continue;
            }
            InventoryView view = player.getOpenInventory();
            XlrGuiHolder holder = XlrGuiHolder.from(view.getTopInventory());
            if (holder == null || holder.getType() != GuiType.BOX_STORAGE || !boxName.equals(holder.getTemplateName())) {
                continue;
            }
            Inventory top = view.getTopInventory();
            for (int i = 0; i < BOX_STORAGE_SIZE && i < live.length; i++) {
                ItemStack stack = live[i];
                top.setItem(i, stack == null || stack.getType().isAir() ? null : stack.clone());
            }
        }
    }

    public void openHopperSettings(Player player, Block hopperBlock) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) {
            return;
        }
        Location loc = hopperBlock.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        XlrGuiHolder holder = new XlrGuiHolder(GuiType.HOPPER_SETTINGS, world, loc.getBlockX(), loc.getBlockY(),
                loc.getBlockZ());
        Inventory inv = Bukkit.createInventory(holder, HOPPER_SETTINGS_SIZE, color("&e漏斗设置"));
        holder.bind(inv);

        ItemStack glass = blackGlass();
        for (int i = 0; i < HOPPER_SETTINGS_SIZE; i++) {
            inv.setItem(i, glass);
        }

        HopperBlockConfig config = HopperBlockConfig.read(hopperBlock, hopperKeys);
        inv.setItem(HOPPER_SLOT_REDSTONE_LIST, toggleStateButton(Material.REDSTONE, "&e红石开关名单",
                config.isRedstoneListToggle(), List.of("&7充能=白名单 未充能=黑名单")));
        inv.setItem(HOPPER_SLOT_REVERSE, toggleStateButton(Material.GOLD_INGOT, "&e反向吸取",
                config.isReverseSuction()));

        player.openInventory(inv);
    }

    public void openFilterEnchants(Player player, String templateName) {
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.FILTER_ENCHANTS, templateName), 54,
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
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.FILTER_ITEMS, templateName), 54,
                color("&6过滤的物品"));
        bindHolder(inv, GuiType.FILTER_ITEMS);

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template != null) {
            int slot = 0;
            for (ItemStack proto : template.getFilterPrototypes()) {
                if (slot >= 54) {
                    break;
                }
                ItemStack display = ItemStackUtil.clonePrototype(proto);
                if (display != null) {
                    inv.setItem(slot++, display);
                }
            }
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
            handleFilterItemsClick(event);
            return;
        }
        if (holder.getType() == GuiType.BOX_STORAGE) {
            handleBoxStorageClick(event);
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
            case TEMPLATE_SETTINGS -> handleSettingsClick(player, slot, event.getClick(), holder);
            case FILTER_ENCHANTS -> handleFilterEnchantsClick(player, slot, holder);
            case HOPPER_SETTINGS -> handleHopperSettingsClick(player, slot, event.getClick(), holder);
            case BOX_LIST -> handleBoxListClick(player, slot, holder);
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
        if (holder.getType() != GuiType.FILTER_ITEMS && holder.getType() != GuiType.BOX_STORAGE) {
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
            String templateName = resolveTemplateName(holder, player);
            if (templateName != null) {
                processFilterItemsClose(player, event.getInventory(), templateName);
                saveData();
            }
            return;
        }
        if (holder.getType() == GuiType.TEMPLATE_SETTINGS) {
            saveData();
        }
        if (holder.getType() == GuiType.BOX_STORAGE) {
            String boxName = holder.getTemplateName();
            if (boxName != null) {
                saveBoxStorage(player, event.getInventory(), boxName);
                sessions.clearOpenBoxSnapshot(player.getUniqueId());
                saveData();
            }
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
        String templateName = sessions.getChatTemplate(player.getUniqueId());
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
            if (templateManager.isTemplateEnabled(player.getUniqueId(), templateName)) {
                player.sendMessage(color("&a已启用模板: &b" + templateName));
                player.sendMessage(color("&7仅影响此后新放置的漏斗；已套用其它模板的漏斗不会改变"));
            } else {
                player.sendMessage(color("&7已关闭模板: &b" + templateName));
            }
            openTemplateList(player);
        } else if (click == ClickType.RIGHT) {
            openTemplateSettings(player, templateName);
        }
    }

    private String resolveTemplateName(XlrGuiHolder holder, Player player) {
        if (holder != null && holder.getTemplateName() != null && !holder.getTemplateName().isEmpty()) {
            return holder.getTemplateName();
        }
        return sessions.getEditingTemplate(player.getUniqueId());
    }

    private void handleSettingsClick(Player player, int slot, ClickType click, XlrGuiHolder holder) {
        String templateName = resolveTemplateName(holder, player);
        if (templateName == null) {
            return;
        }
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            return;
        }
        switch (slot) {
            case SETTINGS_SLOT_LINK_BOX -> {
                if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                    openBoxListForLink(player, templateName);
                }
            }
            case SETTINGS_SLOT_AUTO_DESTROY -> {
                if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                    template.toggleAutoDestroy();
                    saveData();
                    openTemplateSettings(player, templateName);
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
                sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.DURABILITY, templateName);
                player.sendMessage(color("&a请在聊天栏输入数字耐久度 &7(低于该剩余耐久的物品将被过滤，输入 xlrquit 退出设置)"));
            }
            case SETTINGS_SLOT_REMOTE -> player.sendMessage(color("&7暂未开放"));
            case SETTINGS_SLOT_BATCH -> {
                player.closeInventory();
                sessions.clearInput(player.getUniqueId());
                sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.BATCH_APPLY, templateName);
                player.sendMessage(color("&a已进入批量设置模式，左键或右键点击漏斗套用模板"));
                player.sendMessage(color("&7输入 xlrquit 退出批量设置模式"));
            }
            default -> {
            }
        }
    }

    private void handleBoxListClick(Player player, int slot, XlrGuiHolder holder) {
        if (slot < 0 || slot >= BOX_LIST_SIZE) {
            return;
        }
        List<String> names = boxManager.getBoxNames(player.getUniqueId());
        if (slot >= names.size()) {
            return;
        }
        String boxName = names.get(slot);
        String linkingTemplate = sessions.getLinkingBoxTemplate(player.getUniqueId());
        if (linkingTemplate != null) {
            HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), linkingTemplate);
            if (template != null) {
                template.setLinkedBoxName(boxName);
                saveData();
                player.sendMessage(color("&a已链接漏斗仓库: &b" + boxName));
            }
            sessions.clearLinkingBoxTemplate(player.getUniqueId());
            openTemplateSettings(player, linkingTemplate);
            return;
        }
        openBoxStorage(player, boxName);
    }

    private void handleBoxStorageClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryView view = event.getView();
        int topSize = view.getTopInventory().getSize();
        if (event.getClick() == ClickType.NUMBER_KEY && event.getRawSlot() < topSize) {
            event.setCancelled(true);
        }
    }

    private void saveBoxStorage(Player player, Inventory inventory, String boxName) {
        UUID playerId = player.getUniqueId();
        if (!boxManager.hasBox(playerId, boxName)) {
            return;
        }
        ItemStack[] live = boxManager.getBoxContents(playerId, boxName);
        PlayerGuiSession.BoxOpenState openState = sessions.getOpenBoxSnapshot(playerId);
        ItemStack[] snapshot = openState != null && boxName.equals(openState.boxName())
                ? openState.snapshot() : null;

        ItemStack[] contents = new ItemStack[PlayerBoxManager.BOX_CAPACITY];
        for (int i = 0; i < BOX_STORAGE_SIZE && i < contents.length; i++) {
            ItemStack guiStack = inventory.getItem(i);
            ItemStack snapStack = snapshot != null && i < snapshot.length ? snapshot[i] : null;
            if (snapshot != null && !slotStacksEqual(guiStack, snapStack)) {
                contents[i] = guiStack == null || guiStack.getType().isAir() ? null : guiStack.clone();
            } else if (live != null && i < live.length) {
                ItemStack backend = live[i];
                contents[i] = backend == null || backend.getType().isAir() ? null : backend.clone();
            } else if (guiStack != null && !guiStack.getType().isAir()) {
                contents[i] = guiStack.clone();
            }
        }
        boxManager.setBoxContents(playerId, boxName, contents);
    }

    private static boolean slotStacksEqual(ItemStack a, ItemStack b) {
        if (a == null || a.getType().isAir()) {
            return b == null || b.getType().isAir();
        }
        if (b == null || b.getType().isAir()) {
            return false;
        }
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private void handleHopperSettingsClick(Player player, int slot, ClickType click, XlrGuiHolder holder) {
        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            return;
        }
        if (!holder.hasHopperLocation()) {
            return;
        }
        World world = Bukkit.getWorld(holder.getHopperWorld());
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(holder.getHopperX(), holder.getHopperY(), holder.getHopperZ());
        if (block.getType() != Material.HOPPER) {
            player.closeInventory();
            return;
        }
        if (!HopperTemplateResolver.hasValidTemplate(block, hopperKeys, templateManager)) {
            player.closeInventory();
            player.sendMessage(color("&c玩家当前漏斗没有模板"));
            return;
        }
        HopperBlockConfig config = HopperBlockConfig.read(block, hopperKeys);
        boolean changed = false;
        switch (slot) {
            case HOPPER_SLOT_REDSTONE_LIST -> {
                config = config.withRedstoneListToggle(!config.isRedstoneListToggle());
                changed = true;
            }
            case HOPPER_SLOT_REVERSE -> {
                config = config.withReverseSuction(!config.isReverseSuction());
                changed = true;
            }
            default -> {
            }
        }
        if (changed) {
            HopperBlockConfig.write(block, hopperKeys, config);
            openHopperSettings(player, block);
        }
    }

    private void handleFilterEnchantsClick(Player player, int slot, XlrGuiHolder holder) {
        if (slot < 0 || slot >= 54) {
            return;
        }
        String templateName = resolveTemplateName(holder, player);
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
        sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.ENCHANT_LEVEL, templateName);
        player.sendMessage(color("&a请输入附魔等级 &7(低于该等级的附魔将被过滤，输入 xlrquit 退出设置)"));
    }

    private void handleFilterItemsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        int topSize = view.getTopInventory().getSize();
        if (event.getClick() == ClickType.NUMBER_KEY && event.getRawSlot() < topSize) {
            event.setCancelled(true);
        }
    }

    private void processFilterItemsClose(Player player, Inventory inventory, String templateName) {
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            return;
        }
        List<ItemStack> snapshot = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            snapshot.add(stack.clone());
        }

        List<ItemStack> uniqueRules = new ArrayList<>();
        List<ItemStack> toReturn = new ArrayList<>();
        for (ItemStack stack : snapshot) {
            ItemStack proto = ItemStackUtil.clonePrototype(stack);
            if (proto == null) {
                continue;
            }
            boolean duplicate = false;
            for (ItemStack existing : uniqueRules) {
                if (FilterItemMatcher.sameRule(proto, existing)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                toReturn.add(stack.clone());
            } else {
                uniqueRules.add(proto);
            }
        }

        template.setFilterPrototypes(uniqueRules);

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
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
        String displayName = EnchantNameTable.getChineseName(enchant);
        boolean configured = minLevel != null;

        Material material = configured ? Material.ENCHANTED_BOOK : Material.BOOK;
        ItemStack book = new ItemStack(material);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) {
            return book;
        }

        meta.setDisplayName(color("&e" + displayName));
        List<String> lore = new ArrayList<>();
        if (configured) {
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchant, 1, true);
                storageMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta = storageMeta;
            }
            lore.add(color("&a当前过滤: &e" + displayName + " " + minLevel));
        }
        lore.add(color("&7点击设置最低等级"));
        meta.setLore(lore);
        book.setItemMeta(meta);
        return book;
    }

    private static String formatEnchantName(Enchantment enchant) {
        return EnchantNameTable.getChineseName(enchant);
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

    private ItemStack linkedBoxButton(HopperTemplate template) {
        String linked = template.getLinkedBoxName();
        String line = linked == null || linked.isEmpty()
                ? "&e当前链接仓库: 无"
                : "&e当前链接仓库: " + linked;
        return button(Material.ENDER_CHEST, "&e链接漏斗仓库", List.of(line, "&7点击选择要链接的仓库"));
    }

    private ItemStack boxListItem(String boxName) {
        return button(Material.ENDER_CHEST, "&b" + boxName, List.of(
                "&7左键打开仓库（6行）",
                "&7模板链接时左键选择"));
    }

    private ItemStack modeButton(HopperTemplate template) {
        String modeLine = template.isWhitelist()
                ? "&a白名单模式"
                : "&c黑名单模式";
        return button(Material.REDSTONE_BLOCK, "&e过滤模式", List.of(
                "&7仅作用于过滤物品",
                "当前模式: " + modeLine));
    }

    private ItemStack toggleStateButton(Material material, String name, boolean enabled) {
        return toggleStateButton(material, name, enabled, List.of("&7左键或右键点击切换"));
    }

    private ItemStack toggleStateButton(Material material, String name, boolean enabled, List<String> extraLore) {
        String stateLine = enabled ? "&a当前状态: &a开" : "&a当前状态: &c关";
        List<String> lore = new ArrayList<>();
        lore.add(stateLine);
        if (extraLore != null) {
            lore.addAll(extraLore);
        }
        lore.add("&7左键或右键点击切换");
        return button(material, name, lore);
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
