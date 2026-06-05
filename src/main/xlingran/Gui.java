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
    private final HopperTickService tickService;
    private final HopperLaneListener laneListener;
    private final HopperOverlayDisplayService overlayService;

    private final int slotAutoCraft;
    private final int slotAutoDestroy;
    private final int slotFilterItems;
    private final int slotFilterMode;
    private final int slotFilterEnchant;
    private final int slotFilterDurability;
    private final int slotAutoSmelt;
    private final int slotBatch;
    private final int slotHopperRedstone;
    private final int slotHopperReverse;
    private final int slotHopperFloatOverlay;

    public Gui(Shan plugin, HopperTemplateManager templateManager, PlayerGuiSession sessions,
               TemplateRepository templateRepository, HopperKeys hopperKeys, GuiConfig guiConfig,
               MessageConfig messageConfig, HopperTickService tickService, HopperLaneListener laneListener,
               HopperOverlayDisplayService overlayService) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.sessions = sessions;
        this.templateRepository = templateRepository;
        this.hopperKeys = hopperKeys;
        this.guiConfig = guiConfig;
        this.messageConfig = messageConfig;
        this.tickService = tickService;
        this.laneListener = laneListener;
        this.overlayService = overlayService;
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

    public void saveData() {
        templateRepository.markDirty();
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
        inv.setItem(slotHopperRedstone, hopperSettingToggle("Redstone", config.isRedstoneListToggle()));
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
        sessions.setEditingTemplate(player.getUniqueId(), templateName);
        Inventory inv = Bukkit.createInventory(new XlrGuiHolder(GuiType.FILTER_ITEMS, templateName),
                guiConfig.storageSize("Filter-Item"), guiConfig.storageTitle("Filter-Item"));
        bindHolder(inv, GuiType.FILTER_ITEMS);

        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template != null) {
            int slot = 0;
            for (ItemStack proto : template.getFilterPrototypes()) {
                if (slot >= guiConfig.storageSize("Filter-Item")) {
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

        if (holder.getType() == GuiType.FILTER_ITEMS || holder.getType() == GuiType.AUTO_CRAFT
                || holder.getType() == GuiType.AUTO_SMELT) {
            handleStorageGuiClick(event);
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
        if (holder.getType() == GuiType.AUTO_CRAFT) {
            String templateName = resolveTemplateName(holder, player);
            if (templateName != null) {
                processAutoCraftClose(player, event.getInventory(), templateName);
                saveData();
            }
            return;
        }
        if (holder.getType() == GuiType.AUTO_SMELT) {
            String templateName = resolveTemplateName(holder, player);
            if (templateName != null) {
                processAutoSmeltClose(player, event.getInventory(), templateName);
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
            player.sendMessage(messageConfig.message("batch-mode-reminder"));
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
            openFilterEnchants(player, templateName);
        } else if (slot == slotFilterDurability) {
            player.closeInventory();
            sessions.clearInput(player.getUniqueId());
            sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.DURABILITY, templateName);
            player.sendMessage(messageConfig.message("durability-prompt"));
        } else if (slot == slotAutoCraft) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
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
                if (click == ClickType.LEFT) {
                    template.toggleAutoSmeltEnabled();
                    saveData();
                    openTemplateSettings(player, templateName);
                } else {
                    openAutoSmelt(player, templateName);
                }
            }
        } else if (slot == slotBatch) {
            player.closeInventory();
            sessions.clearInput(player.getUniqueId());
            sessions.setInputMode(player.getUniqueId(), PlayerGuiSession.InputMode.BATCH_APPLY, templateName);
            player.sendMessage(messageConfig.message("batch-enter"));
            player.sendMessage(messageConfig.message("batch-quit-hint"));
        }
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
            config = config.withRedstoneListToggle(!config.isRedstoneListToggle());
            changed = true;
        } else if (slot == slotHopperReverse) {
            config = config.withReverseSuction(!config.isReverseSuction());
            changed = true;
        } else if (slot == slotHopperFloatOverlay) {
            if (!config.isHoverDisplay() && !overlayService.isAvailable()) {
                player.sendMessage(messageConfig.message("overlay-dh-missing"));
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
            laneListener.scheduleEvaluate(block);
            if (config.isHoverDisplay()) {
                overlayService.show(block);
            } else {
                overlayService.hide(block);
            }
            openHopperSettings(player, block);
        }
    }

    private void handleFilterEnchantsClick(Player player, int slot, ClickType click, XlrGuiHolder holder) {
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
        HopperTemplate template = templateManager.getTemplate(player.getUniqueId(), templateName);
        if (template != null) {
            filler.fill(inv, template);
        }
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

    private void processAutoCraftClose(Player player, Inventory inventory, String templateName) {
        processPrototypeStorageClose(player, inventory, templateName, true);
    }

    private void processAutoSmeltClose(Player player, Inventory inventory, String templateName) {
        processPrototypeStorageClose(player, inventory, templateName, false);
    }

    private void processPrototypeStorageClose(Player player, Inventory inventory, String templateName,
                                              boolean craftTargets) {
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

        if (craftTargets) {
            template.setAutoCraftTargets(uniqueRules);
        } else {
            template.setAutoSmeltOutputs(uniqueRules);
        }

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
        String displayName = EnchantNameTable.getChineseName(enchant);
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

    private static String formatEnchantName(Enchantment enchant) {
        return EnchantNameTable.getChineseName(enchant);
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
