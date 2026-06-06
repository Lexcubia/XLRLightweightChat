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

import xlingran.core.HopperLaneListener;
import xlingran.display.HopperOverlayDisplayService;
import xlingran.gui.GuiConfig;
import xlingran.gui.MessageConfig;
import xlingran.gui.GuiType;
import xlingran.gui.XlrGuiHolder;
import xlingran.storage.TemplateRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Gui implements Listener {

    private final Shan plugin;
    private final HopperTemplateManager templateManager;
    private final PlayerGuiSession sessions;
    private final TemplateRepository templateRepository;
    private final HopperKeys hopperKeys;
    private final GuiConfig guiConfig;
    private final MessageConfig messageConfig;
    private final XLRHopperConfig pluginConfig;
    private final HopperTickService tickService;
    private final HopperLaneListener laneListener;
    private final HopperOverlayDisplayService overlayService;

    private int slotAutoCraft;
    private int slotAutoDestroy;
    private int slotFilterItems;
    private int slotFilterMode;
    private int slotFilterEnchant;
    private int slotFilterDurability;
    private int slotAutoSmelt;
    private int slotBatch;
    private int slotHopperRedstone;
    private int slotHopperReverse;
    private int slotHopperFloatOverlay;

    public Gui(Shan plugin, HopperTemplateManager templateManager, PlayerGuiSession sessions,
               TemplateRepository templateRepository, HopperKeys hopperKeys, GuiConfig guiConfig,
               MessageConfig messageConfig, XLRHopperConfig pluginConfig, HopperTickService tickService,
               HopperLaneListener laneListener, HopperOverlayDisplayService overlayService) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.sessions = sessions;
        this.templateRepository = templateRepository;
        this.hopperKeys = hopperKeys;
        this.guiConfig = guiConfig;
        this.messageConfig = messageConfig;
        this.pluginConfig = pluginConfig;
        this.tickService = tickService;
        this.laneListener = laneListener;
        this.overlayService = overlayService;
        reloadLayoutFromConfig();
    }

    public void saveData() {
        templateRepository.markDirty();
    }

    /** 样板列表（过滤/合成/熔炼）变更后立即写入 shan.db */
    public void saveStorageDataImmediate() {
        templateRepository.flushSync(templateManager);
    }

    private void logStorageDebug(String message) {
        if (pluginConfig.isDebugTemplateStorage()) {
            plugin.getLogger().info("[XLRHopper][存储调试] " + message);
        }
    }

    private void logStorageTrace(String message) {
        plugin.getLogger().info("[XLRHopper][存储] " + message);
    }

    private boolean isStorageGuiTitle(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }
        String stripped = ChatColor.stripColor(title);
        return stripped.equals(ChatColor.stripColor(guiConfig.storageTitle("Filter-Item")))
                || stripped.equals(ChatColor.stripColor(guiConfig.storageTitle("Auto-Crafting")))
                || stripped.equals(ChatColor.stripColor(guiConfig.storageTitle("Auto-Furnace")));
    }

    private XlrGuiHolder resolveStorageGuiHolder(InventoryCloseEvent event) {
        XlrGuiHolder holder = XlrGuiHolder.from(event.getView().getTopInventory());
        if (holder != null) {
            return holder;
        }
        return XlrGuiHolder.from(event.getInventory());
    }

    private static String describeHolder(Inventory inventory) {
        if (inventory == null) {
            return "null";
        }
        if (inventory.getHolder() == null) {
            return "null-holder";
        }
        return inventory.getHolder().getClass().getName();
    }

    private void logStorageSaved(String templateName, String listKind, int kept, int returned) {
        logStorageDebug("内存已更新 模板=" + templateName + " " + listKind + " 保留=" + kept + " 退回=" + returned);
    }

    private static int countNonEmptySlots(Inventory inventory) {
        int count = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && !stack.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 关服前将仍打开的存储类 GUI 内容写回模板，避免未关闭界面导致列表丢失。
     */
    public void persistOpenStorageGuisBeforeShutdown() {
        int persisted = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view == null) {
                continue;
            }
            XlrGuiHolder holder = XlrGuiHolder.from(view.getTopInventory());
            if (holder == null) {
                continue;
            }
            String templateName = resolveTemplateName(holder, player);
            if (templateName == null) {
                plugin.getLogger().warning("[XLRHopper] 关服跳过存储 GUI：无法解析模板名 player="
                        + player.getName() + " gui=" + holder.getType());
                continue;
            }
            Inventory top = view.getTopInventory();
            switch (holder.getType()) {
                case FILTER_ITEMS -> {
                    processFilterItemsClose(player, top, templateName);
                    persisted++;
                }
                case AUTO_CRAFT -> {
                    processAutoCraftClose(player, top, templateName);
                    persisted++;
                }
                case AUTO_SMELT -> {
                    processAutoSmeltClose(player, top, templateName);
                    persisted++;
                }
                default -> {
                }
            }
        }
        if (persisted > 0) {
            logStorageDebug("关服前处理 " + persisted + " 个仍打开的存储 GUI，执行 flushSync");
            saveStorageDataImmediate();
        }
    }

    public void refreshAfterConfigReload() {
        reloadLayoutFromConfig();
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view == null) {
                continue;
            }
            XlrGuiHolder holder = XlrGuiHolder.from(view.getTopInventory());
            if (holder == null) {
                continue;
            }
            player.closeInventory();
            switch (holder.getType()) {
                case TEMPLATE_LIST -> openTemplateList(player);
                case TEMPLATE_SETTINGS -> {
                    String templateName = resolveTemplateName(holder, player);
                    if (templateName != null) {
                        openTemplateSettings(player, templateName);
                    }
                }
                case HOPPER_SETTINGS -> {
                    if (!holder.hasHopperLocation()) {
                        break;
                    }
                    World world = Bukkit.getWorld(holder.getHopperWorld());
                    if (world == null) {
                        break;
                    }
                    Block block = world.getBlockAt(holder.getHopperX(), holder.getHopperY(), holder.getHopperZ());
                    if (block.getType() == Material.HOPPER) {
                        openHopperSettings(player, block);
                    }
                }
                case FILTER_ENCHANTS -> {
                    String templateName = resolveTemplateName(holder, player);
                    if (templateName != null) {
                        openFilterEnchants(player, templateName);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void reloadLayoutFromConfig() {
        slotAutoCraft = guiConfig.templateButtonSlot("AutoCrafting", 10);
        slotAutoDestroy = guiConfig.templateButtonSlot("Break", 12);
        slotFilterItems = guiConfig.templateButtonSlot("FilterItem", 14);
        slotFilterMode = guiConfig.templateButtonSlot("FilterMode", 16);
        slotFilterEnchant = guiConfig.templateButtonSlot("FilterEnchant", 28);
        slotFilterDurability = guiConfig.templateButtonSlot("FilterDurability", 30);
        slotAutoSmelt = guiConfig.templateButtonSlot("AutoSmelt", 32);
        slotBatch = guiConfig.templateButtonSlot("Batch", 34);
        slotHopperRedstone = guiConfig.hopperSettingSlot("Redstone", 10);
        slotHopperReverse = guiConfig.hopperSettingSlot("Reverse", 12);
        slotHopperFloatOverlay = guiConfig.hopperSettingSlot("FloatOverlay", 14);
    }

    public void openTemplateList(Player player) {
        int size = guiConfig.templateListSize();
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.TEMPLATE_LIST), size,
                guiConfig.templateListTitle());
        bindHolder(inv, GuiType.TEMPLATE_LIST);

        List<String> names = new ArrayList<>(templateManager.getTemplates(player.getUniqueId()).keySet());
        for (int i = 0; i < names.size() && i < size; i++) {
            inv.setItem(i, createTemplateListItem(player, names.get(i)));
        }
        player.openInventory(inv);
    }

    public void openTemplateSettings(Player player, String templateName) {
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        int size = guiConfig.templateSettingsSize();
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.TEMPLATE_SETTINGS, templateName),
                size, guiConfig.templateSettingsTitle(templateName));
        bindHolder(inv, GuiType.TEMPLATE_SETTINGS);

        ItemStack glass = fillerGlass();
        for (int i = 0; i < size; i++) {
            inv.setItem(i, glass);
        }

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            player.openInventory(inv);
            return;
        }

        inv.setItem(slotAutoCraft, autoCraftButton(template));
        inv.setItem(slotAutoDestroy, toggleButton("Break", template.isAutoDestroy(), null));
        inv.setItem(slotFilterItems, configButton("FilterItem"));
        inv.setItem(slotFilterMode, modeButton(template));
        inv.setItem(slotFilterEnchant, configButton("FilterEnchant"));
        inv.setItem(slotFilterDurability, durabilityButton(template));
        inv.setItem(slotAutoSmelt, autoSmeltButton(template));
        inv.setItem(slotBatch, configButton("Batch"));

        player.openInventory(inv);
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
        int size = guiConfig.hopperSettingSize();
        Inventory inv = Bukkit.createInventory(holder, size, guiConfig.hopperSettingTitle());
        holder.bind(inv);

        ItemStack glass = hopperSettingFiller();
        for (int i = 0; i < size; i++) {
            inv.setItem(i, glass);
        }

        HopperBlockConfig config = HopperBlockConfig.read(hopperBlock, hopperKeys);
        inv.setItem(slotHopperRedstone, hopperRedstoneButton(hopperBlock, config));
        inv.setItem(slotHopperReverse, hopperSettingToggle("Reverse", config.isReverseSuction()));
        inv.setItem(slotHopperFloatOverlay, hopperSettingToggle("FloatOverlay", config.isHoverDisplay()));

        player.openInventory(inv);
    }

    public void openFilterEnchants(Player player, String templateName) {
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.FILTER_ENCHANTS, templateName),
                guiConfig.filterEnchantsSize(), guiConfig.filterEnchantsTitle());
        bindHolder(inv, GuiType.FILTER_ENCHANTS);

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        List<Enchantment> enchants = listRegistryEnchantments();
        int maxSlots = guiConfig.filterEnchantsSize();
        for (int i = 0; i < enchants.size() && i < maxSlots; i++) {
            Enchantment enchant = enchants.get(i);
            Integer minLevel = template != null ? template.getEnchantMinLevels().get(enchant) : null;
            inv.setItem(i, createEnchantFilterBook(enchant, minLevel));
        }
        player.openInventory(inv);
    }

    public void openFilterItems(Player player, String templateName) {
        openStorageGui(player, templateName, GuiType.FILTER_ITEMS, "Filter-Item",
                (inv, template) -> {
                    int slot = 0;
                    int max = inv.getSize();
                    for (ItemStack proto : template.getFilterPrototypes()) {
                        if (slot >= max) {
                            break;
                        }
                        ItemStack display = ItemStackUtil.clonePrototype(proto);
                        if (display != null) {
                            inv.setItem(slot++, display);
                        }
                    }
                });
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

        if (holder.getType() == GuiType.FILTER_ITEMS || holder.getType() == GuiType.AUTO_CRAFT
                || holder.getType() == GuiType.AUTO_SMELT) {
            handleStorageGuiClick(event);
            return;
        }
        event.setCancelled(true);
        if (GuiClickGuard.shouldIgnoreClick(sessions, player, pluginConfig.getGuiClickCooldownMs())) {
            return;
        }
        if (event.getClickedInventory() != top) {
            GuiClickGuard.blockItemManipulation(event);
            return;
        }

        int slot = event.getSlot();

        switch (holder.getType()) {
            case TEMPLATE_LIST -> handleTemplateListClick(player, slot, event.getClick());
            case TEMPLATE_SETTINGS -> handleSettingsClick(player, slot, event.getClick(), holder);
            case FILTER_ENCHANTS -> handleFilterEnchantsClick(player, slot, event.getClick(), holder);
            case HOPPER_SETTINGS -> handleHopperSettingsClick(player, slot, event.getClick(), holder);
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
        if (holder.getType() != GuiType.FILTER_ITEMS && holder.getType() != GuiType.AUTO_CRAFT
                && holder.getType() != GuiType.AUTO_SMELT) {
            GuiClickGuard.cancelDrag(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        XlrGuiHolder holder = resolveStorageGuiHolder(event);
        if (holder == null) {
            String title = event.getView().getTitle();
            if (isStorageGuiTitle(title)) {
                plugin.getLogger().warning("[XLRHopper] 存储 GUI 关闭但 holder 丢失，无法保存 player="
                        + player.getName() + " title=" + title + " topHolder="
                        + describeHolder(topInventory) + " eventHolder=" + describeHolder(event.getInventory()));
            }
            return;
        }
        if (holder.getType() == GuiType.FILTER_ITEMS) {
            handleStorageGuiClose(player, topInventory, holder, GuiType.FILTER_ITEMS);
            return;
        }
        if (holder.getType() == GuiType.AUTO_CRAFT) {
            handleStorageGuiClose(player, topInventory, holder, GuiType.AUTO_CRAFT);
            return;
        }
        if (holder.getType() == GuiType.AUTO_SMELT) {
            handleStorageGuiClose(player, topInventory, holder, GuiType.AUTO_SMELT);
            return;
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
                player.sendMessage(messageConfig.message("batch-quit-done"));
                openTemplateSettings(player, templateName);
                return;
            }
            if (mode == PlayerGuiSession.InputMode.ENCHANT_LEVEL) {
                sessions.clearInput(player.getUniqueId());
                player.sendMessage(messageConfig.message("input-cancel-return-enchant"));
                openFilterEnchants(player, templateName);
                return;
            }
            sessions.clearInput(player.getUniqueId());
            player.sendMessage(messageConfig.message("input-cancel-return-settings"));
            openTemplateSettings(player, templateName);
            return;
        }

        if (mode == PlayerGuiSession.InputMode.BATCH_APPLY) {
            if (!pluginConfig.isBatchSetEnabled()) {
                sessions.clearInput(player.getUniqueId());
                player.sendMessage(messageConfig.message("overlay-feature-disabled"));
                openTemplateSettings(player, templateName);
                return;
            }
            player.sendMessage(messageConfig.message("batch-mode-reminder"));
            return;
        }

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            sessions.clearInput(player.getUniqueId());
            return;
        }

        if (mode == PlayerGuiSession.InputMode.DURABILITY) {
            if (!pluginConfig.isFilterDurabilityEnabled()) {
                sessions.clearInput(player.getUniqueId());
                player.sendMessage(messageConfig.message("overlay-feature-disabled"));
                openTemplateSettings(player, templateName);
                return;
            }
            Integer value = parsePositiveInt(message);
            if (value == null) {
                player.sendMessage(messageConfig.message("durability-input-error"));
                return;
            }
            template.setDurabilityThreshold(value);
            sessions.clearInput(player.getUniqueId());
            saveData();
            player.sendMessage(messageConfig.message("durability-set-success",
                    Map.of("Durability", String.valueOf(value))));
            openTemplateSettings(player, templateName);
            return;
        }
        if (mode == PlayerGuiSession.InputMode.ENCHANT_LEVEL) {
            if (!pluginConfig.isFilterEnchanEnabled()) {
                sessions.clearInput(player.getUniqueId());
                player.sendMessage(messageConfig.message("overlay-feature-disabled"));
                openTemplateSettings(player, templateName);
                return;
            }
            Enchantment pending = sessions.getPendingEnchant(player.getUniqueId());
            if (pending == null) {
                sessions.clearInput(player.getUniqueId());
                openFilterEnchants(player, templateName);
                return;
            }
            Integer level = parsePositiveInt(message);
            if (level == null) {
                player.sendMessage(messageConfig.message("enchant-input-error"));
                return;
            }
            template.setEnchantMinLevel(pending, level);
            sessions.clearInput(player.getUniqueId());
            saveData();
            player.sendMessage(messageConfig.message("enchant-set-success", Map.of(
                    "Enchant", formatEnchantName(pending),
                    "Level", String.valueOf(level))));
            openFilterEnchants(player, templateName);
        }
    }

    private void handleTemplateListClick(Player player, int slot, ClickType click) {
        if (slot < 0 || slot >= guiConfig.templateListSize()) {
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
                player.sendMessage(messageConfig.message("template-enabled",
                        Map.of("Template", templateName)));
            } else {
                player.sendMessage(messageConfig.message("template-disabled",
                        Map.of("Template", templateName)));
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
        if (slot == slotAutoDestroy) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                if (rejectDisabledFeature(player, pluginConfig.isDestroyUnmatchedEnabled())) {
                    return;
                }
                template.toggleAutoDestroy();
                saveData();
                openTemplateSettings(player, templateName);
            }
        } else if (slot == slotFilterItems) {
            openFilterItems(player, templateName);
        } else if (slot == slotFilterMode) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                template.toggleWhitelist();
                saveData();
                openTemplateSettings(player, templateName);
            }
        } else if (slot == slotFilterEnchant) {
            if (rejectDisabledFeature(player, pluginConfig.isFilterEnchanEnabled())) {
                return;
            }
            openFilterEnchants(player, templateName);
        } else if (slot == slotFilterDurability) {
            if (rejectDisabledFeature(player, pluginConfig.isFilterDurabilityEnabled())) {
                return;
            }
            player.closeInventory();
            sessions.clearInput(player.getUniqueId());
            sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.DURABILITY, templateName);
            player.sendMessage(messageConfig.message("durability-prompt"));
        } else if (slot == slotAutoCraft) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                if (rejectDisabledFeature(player, pluginConfig.isAutoCraftEnabled())) {
                    return;
                }
                if (click == ClickType.LEFT) {
                    template.toggleAutoCraftEnabled();
                    saveData();
                    openTemplateSettings(player, templateName);
                } else {
                    openAutoCraft(player, templateName);
                }
            }
        } else if (slot == slotAutoSmelt) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                if (rejectDisabledFeature(player, pluginConfig.isAutoSmeltEnabled())) {
                    return;
                }
                if (click == ClickType.LEFT) {
                    template.toggleAutoSmeltEnabled();
                    saveData();
                    openTemplateSettings(player, templateName);
                } else {
                    openAutoSmelt(player, templateName);
                }
            }
        } else if (slot == slotBatch) {
            if (rejectDisabledFeature(player, pluginConfig.isBatchSetEnabled())) {
                return;
            }
            player.closeInventory();
            sessions.clearInput(player.getUniqueId());
            sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.BATCH_APPLY, templateName);
            player.sendMessage(messageConfig.message("batch-enter"));
            player.sendMessage(messageConfig.message("batch-quit-hint"));
        }
    }

    private boolean rejectDisabledFeature(Player player, boolean enabled) {
        if (enabled) {
            return false;
        }
        player.sendMessage(messageConfig.message("overlay-feature-disabled"));
        return true;
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
            player.sendMessage(messageConfig.message("no-template"));
            return;
        }
        HopperBlockConfig config = HopperBlockConfig.read(block, hopperKeys);
        boolean changed = false;
        if (slot == slotHopperRedstone) {
            if (rejectDisabledFeature(player, pluginConfig.isRedstoneToggleEnabled())) {
                return;
            }
            config = config.withRedstoneListToggle(!config.isRedstoneListToggle());
            changed = true;
        } else if (slot == slotHopperReverse) {
            if (rejectDisabledFeature(player, pluginConfig.isReverseHopperEnabled())) {
                return;
            }
            config = config.withReverseSuction(!config.isReverseSuction());
            changed = true;
        } else if (slot == slotHopperFloatOverlay) {
            if (!pluginConfig.isHologramEnabled() || !overlayService.isAvailable()) {
                player.sendMessage(messageConfig.message("overlay-feature-disabled"));
                openHopperSettings(player, block);
                return;
            }
            config = config.withHoverDisplay(!config.isHoverDisplay());
            changed = true;
        }
        if (changed) {
            HopperBlockConfig.write(block, hopperKeys, config);
            tickService.getLaneRegistry().registerLane(block, hopperKeys, templateManager,
                    plugin.getUpdateConfig());
            if (slot == slotHopperRedstone || slot == slotHopperReverse) {
                laneListener.scheduleEvaluateImmediate(block);
            } else {
                laneListener.scheduleEvaluate(block);
            }
            if (config.isHoverDisplay()) {
                overlayService.refresh(block);
            } else {
                overlayService.hide(block);
            }
            openHopperSettings(player, block);
        }
    }

    private void handleFilterEnchantsClick(Player player, int slot, ClickType click, XlrGuiHolder holder) {
        if (rejectDisabledFeature(player, pluginConfig.isFilterEnchanEnabled())) {
            return;
        }
        if (slot < 0 || slot >= guiConfig.filterEnchantsSize()) {
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
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            if (template.getEnchantMinLevels().containsKey(selected)) {
                template.removeEnchantMinLevel(selected);
                saveData();
                player.sendMessage(messageConfig.message("enchant-cleared",
                        Map.of("Enchant", formatEnchantName(selected))));
                openFilterEnchants(player, templateName);
            }
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) {
            return;
        }
        player.closeInventory();
        sessions.setPendingEnchant(player.getUniqueId(), selected);
        sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.ENCHANT_LEVEL, templateName);
        player.sendMessage(messageConfig.message("enchant-prompt"));
    }

    public void openAutoCraft(Player player, String templateName) {
        openStorageGui(player, templateName, GuiType.AUTO_CRAFT, "Auto-Crafting",
                (inv, template) -> fillPrototypeSlots(inv, template.getAutoCraftTargets()));
    }

    public void openAutoSmelt(Player player, String templateName) {
        openStorageGui(player, templateName, GuiType.AUTO_SMELT, "Auto-Furnace",
                (inv, template) -> fillPrototypeSlots(inv, template.getAutoSmeltOutputs()));
    }

    private void fillPrototypeSlots(Inventory inv, List<ItemStack> prototypes) {
        int slot = 0;
        int max = inv.getSize();
        for (ItemStack proto : prototypes) {
            if (slot >= max) {
                break;
            }
            ItemStack display = ItemStackUtil.clonePrototype(proto);
            if (display != null) {
                inv.setItem(slot++, display);
            }
        }
    }

    private void openStorageGui(Player player, String templateName, GuiType type, String configKey,
                                StorageGuiFiller filler) {
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(type, templateName),
                guiConfig.storageSize(configKey), guiConfig.storageTitle(configKey));
        bindHolder(inv, type);
        int loaded = 0;
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template != null) {
            loaded = switch (type) {
                case FILTER_ITEMS -> template.getFilterPrototypes().size();
                case AUTO_CRAFT -> template.getAutoCraftTargets().size();
                case AUTO_SMELT -> template.getAutoSmeltOutputs().size();
                default -> 0;
            };
            filler.fill(inv, template);
        }
        logStorageTrace("存储 GUI 打开 type=" + type + " player=" + player.getName()
                + " template=" + templateName + " 从内存加载=" + loaded + " 条 dataLoaded="
                + templateRepository.isDataLoaded());
        logStorageDebug("打开存储 GUI type=" + type + " player=" + player.getName()
                + " template=" + templateName + " 从内存加载=" + loaded + " 条 dataLoaded="
                + templateRepository.isDataLoaded());
        player.openInventory(inv);
    }

    @FunctionalInterface
    private interface StorageGuiFiller {
        void fill(Inventory inv, HopperTemplate template);
    }

    private void handleStorageGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        int topSize = view.getTopInventory().getSize();
        if (event.getClick() == ClickType.NUMBER_KEY && event.getRawSlot() < topSize) {
            event.setCancelled(true);
        }
    }

    private void handleStorageGuiClose(Player player, Inventory topInventory, XlrGuiHolder holder, GuiType type) {
        String holderName = holder.getTemplateName();
        String sessionName = sessions.getEditingTemplate(player.getUniqueId());
        String templateName = resolveTemplateName(holder, player);
        int slots = countNonEmptySlots(topInventory);
        logStorageTrace("存储 GUI 关闭处理开始 type=" + type + " player=" + player.getName()
                + " template=" + templateName + " 物品格数=" + slots);
        logStorageDebug("关闭存储 GUI type=" + type + " player=" + player.getName()
                + " holderTemplate=" + holderName + " sessionTemplate=" + sessionName
                + " resolved=" + templateName + " 物品格数=" + slots);
        if (templateName == null) {
            plugin.getLogger().warning("[XLRHopper] 存储 GUI 关闭未保存：templateName 为空 player="
                    + player.getName() + " gui=" + type + " holder=" + holderName + " session=" + sessionName);
            return;
        }
        switch (type) {
            case FILTER_ITEMS -> processFilterItemsClose(player, topInventory, templateName);
            case AUTO_CRAFT -> processAutoCraftClose(player, topInventory, templateName);
            case AUTO_SMELT -> processAutoSmeltClose(player, topInventory, templateName);
            default -> {
                return;
            }
        }
        saveStorageDataImmediate();
    }

    private void processAutoCraftClose(Player player, Inventory inventory, String templateName) {
        processPrototypeStorageClose(player, inventory, templateName, true);
    }

    private void processAutoSmeltClose(Player player, Inventory inventory, String templateName) {
        processPrototypeStorageClose(player, inventory, templateName, false);
    }

    private record PrototypeDedupeResult(List<ItemStack> uniqueRules, List<ItemStack> toReturn) {
    }

    private static PrototypeDedupeResult dedupePrototypeSnapshot(Inventory inventory) {
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
                if (stack.getAmount() > 1) {
                    ItemStack excess = stack.clone();
                    excess.setAmount(stack.getAmount() - 1);
                    toReturn.add(excess);
                }
            }
        }
        return new PrototypeDedupeResult(uniqueRules, toReturn);
    }

    private void returnDuplicateItems(Player player, Inventory inventory, List<ItemStack> toReturn) {
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

    private void processPrototypeStorageClose(Player player, Inventory inventory, String templateName,
                                              boolean craftTargets) {
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            plugin.getLogger().warning("[XLRHopper] 存储 GUI 关闭未写入内存：模板不存在 player="
                    + player.getName() + " template=" + templateName);
            return;
        }
        PrototypeDedupeResult result = dedupePrototypeSnapshot(inventory);
        if (craftTargets) {
            template.setAutoCraftTargets(result.uniqueRules());
            logStorageSaved(templateName, "自动合成", result.uniqueRules().size(), result.toReturn().size());
        } else {
            template.setAutoSmeltOutputs(result.uniqueRules());
            logStorageSaved(templateName, "自动熔炼", result.uniqueRules().size(), result.toReturn().size());
        }
        templateRepository.markDirty();
        returnDuplicateItems(player, inventory, result.toReturn());
    }

    private void processFilterItemsClose(Player player, Inventory inventory, String templateName) {
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template == null) {
            plugin.getLogger().warning("[XLRHopper] 存储 GUI 关闭未写入内存：模板不存在 player="
                    + player.getName() + " template=" + templateName);
            return;
        }
        PrototypeDedupeResult result = dedupePrototypeSnapshot(inventory);
        template.setFilterPrototypes(result.uniqueRules());
        logStorageSaved(templateName, "过滤物品", result.uniqueRules().size(), result.toReturn().size());
        templateRepository.markDirty();
        returnDuplicateItems(player, inventory, result.toReturn());
    }

    private ItemStack createTemplateListItem(Player player, String templateName) {
        boolean enabled = templateManager.isTemplateEnabled(player.getUniqueId(), templateName);
        GuiConfig.GuiButtonDef def = guiConfig.templateListItem(enabled);
        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        Map<String, String> vars = Map.of("Template", templateName, "toggle", guiConfig.toggle(enabled));
        meta.setDisplayName(guiConfig.apply(def.name(), vars));
        List<String> lore = guiConfig.resolveLore(def.lore(), vars);
        if (def.showEnchant()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack durabilityButton(HopperTemplate template) {
        GuiConfig.GuiButtonDef def = guiConfig.templateButton("FilterDurability");
        List<String> lore = guiConfig.durabilityLore(template.getDurabilityThreshold() != null,
                template.getDurabilityThreshold());
        return button(def.material(), def.name(), lore);
    }

    private ItemStack createEnchantFilterBook(Enchantment enchant, Integer minLevel) {
        String displayName = guiConfig.getEnchantDisplayName(enchant);
        boolean configured = minLevel != null;
        GuiConfig.GuiButtonDef def = guiConfig.filterEnchantBook(configured);
        ItemStack book = new ItemStack(def.material());
        ItemMeta meta = book.getItemMeta();
        if (meta == null) {
            return book;
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("Enchant", displayName);
        if (minLevel != null) {
            vars.put("EnchantLevel", String.valueOf(minLevel));
        }
        meta.setDisplayName(guiConfig.apply(def.name(), vars));
        if (configured && meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(enchant, 1, true);
            storageMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta = storageMeta;
        }
        meta.setLore(guiConfig.resolveLore(def.lore(), vars));
        book.setItemMeta(meta);
        return book;
    }

    private String formatEnchantName(Enchantment enchant) {
        return guiConfig.getEnchantDisplayName(enchant);
    }

    private List<Enchantment> listRegistryEnchantments() {
        List<Enchantment> enchants = new ArrayList<>();
        for (Enchantment enchant : Registry.ENCHANTMENT) {
            enchants.add(enchant);
            if (enchants.size() >= guiConfig.filterEnchantsSize()) {
                plugin.getLogger().warning("[XLRHopper] 附魔数量超过 GUI 容量，部分附魔未显示");
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

    private ItemStack autoCraftButton(HopperTemplate template) {
        return toggleButton("AutoCrafting", template.isAutoCraftEnabled(), null);
    }

    private ItemStack autoSmeltButton(HopperTemplate template) {
        return toggleButton("AutoSmelt", template.isAutoSmeltEnabled(), null);
    }

    private ItemStack modeButton(HopperTemplate template) {
        GuiConfig.GuiButtonDef def = guiConfig.templateButton("FilterMode");
        Map<String, String> vars = Map.of("filtermode", guiConfig.filterMode(template.isWhitelist()));
        return button(def.material(), def.name(), guiConfig.resolveLore(def.lore(), vars));
    }

    private ItemStack configButton(String key) {
        GuiConfig.GuiButtonDef def = guiConfig.templateButton(key);
        return button(def.material(), def.name(), guiConfig.resolveLore(def.lore(), Map.of()));
    }

    private ItemStack toggleButton(String key, boolean enabled, Map<String, String> extraVars) {
        GuiConfig.GuiButtonDef def = guiConfig.templateButton(key);
        Map<String, String> vars = new HashMap<>();
        vars.put("toggle", guiConfig.toggle(enabled));
        if (extraVars != null) {
            vars.putAll(extraVars);
        }
        return button(def.material(), def.name(), guiConfig.resolveLore(def.lore(), vars));
    }

    private ItemStack hopperSettingToggle(String key, boolean enabled) {
        GuiConfig.GuiButtonDef def = guiConfig.hopperSettingButton(key);
        Map<String, String> vars = Map.of("toggle", guiConfig.toggle(enabled));
        return button(def.material(), def.name(), guiConfig.resolveLore(def.lore(), vars));
    }

    private ItemStack hopperRedstoneButton(Block hopperBlock, HopperBlockConfig config) {
        GuiConfig.GuiButtonDef def = guiConfig.hopperSettingButton("Redstone");
        HopperTemplate template = HopperTemplateResolver.resolve(hopperBlock, hopperKeys, templateManager);
        String modeLine;
        if (config.isRedstoneListToggle() && template != null) {
            modeLine = guiConfig.stoneMode(HopperBlockConfig.getEffectiveWhitelist(hopperBlock, hopperKeys, template));
        } else {
            modeLine = guiConfig.stoneModeDisabled();
        }
        Map<String, String> vars = Map.of(
                "toggle", guiConfig.toggle(config.isRedstoneListToggle()),
                "stonemode", modeLine);
        return button(def.material(), def.name(), guiConfig.resolveLore(def.lore(), vars));
    }

    private ItemStack fillerGlass() {
        ItemStack item = new ItemStack(guiConfig.fillerMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack hopperSettingFiller() {
        ItemStack item = new ItemStack(guiConfig.hopperSettingFiller());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(guiConfig.color(name));
            meta.setLore(loreLines);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void bindHolder(Inventory inv, GuiType type) {
        if (inv.getHolder() instanceof XlrGuiHolder holder) {
            holder.bind(inv);
        }
    }

    private String color(String text) {
        return guiConfig.color(text);
    }
}
