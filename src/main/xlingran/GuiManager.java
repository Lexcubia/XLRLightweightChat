package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GuiManager implements Listener {

    private final Shan plugin;
    private final Map<UUID, Integer> playerCurrentPage = new HashMap<>(); // 玩家当前页码
    private static final int INVENTORY_SIZE = 54; // 6行 * 9列
    private static final int TITLE_SLOTS = 28; // 称号格子数量
    private static final int TITLES_PER_PAGE = 28; // 每页显示28个称号

    public GuiManager(Shan plugin) {
        this.plugin = plugin;
    }

    /**
     * 打开称号仓库 GUI
     */
    public void openTitleGUI(Player player) {
        openTitleGUI(player, 0); // 默认打开第1页
    }

    /**
     * 打开称号仓库 GUI（指定页码）
     */
    public void openTitleGUI(Player player, int page) {
        Inventory gui = createGUI(player, page);
        player.openInventory(gui);
    }

    /**
     * 创建 GUI 界面
     */
    private Inventory createGUI(Player player, int page) {
        // 先按玩家实际可见称号归一化页码，避免 holder 与展示内容不同步。
        List<TitleInfo> availableTitles = getAvailableTitles(player);
        int totalPages = getTotalPages(availableTitles.size());
        page = normalizePage(page, totalPages);

        // 从 Gui.yml 读取标题（任意语言均可，识别界面靠 TitleGuiHolder）
        String pageConfigPath = page == 0 ? "Page1.name" : "Page2.name";
        String guiTitle = plugin.getGuiConfig().getString(pageConfigPath, "&6称号仓库");
        TitleGuiHolder holder = new TitleGuiHolder(page);
        Inventory gui = Bukkit.createInventory(holder, INVENTORY_SIZE,
                ChatColor.translateAlternateColorCodes('&', guiTitle));
        holder.bind(gui);
        playerCurrentPage.put(player.getUniqueId(), page);

        // 1. 填充黑色玻璃板边框
        fillBorder(gui);

        // 2. 显示称号
        displayTitles(gui, player, availableTitles, page);

        // 3. 设置分页按钮（第6行第5格，索引49）
        setupPageButton(gui, player, page, totalPages);

        return gui;
    }

    static int getTotalPages(int titleCount) {
        return Math.max(1, (int) Math.ceil((double) titleCount / TITLES_PER_PAGE));
    }

    static int normalizePage(int page, int totalPages) {
        int lastPage = Math.max(0, totalPages - 1);
        if (page < 0) {
            return 0;
        }
        return Math.min(page, lastPage);
    }

    /**
     * 填充边框（黑色玻璃板）
     */
    private void fillBorder(Inventory gui) {
        ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, "&0");

        // 第1行和第6行全部填充
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, blackGlass); // 第1行
            gui.setItem(45 + i, blackGlass); // 第6行
        }

        // 第1列和第9列（第2-5行）
        for (int i = 9; i < 45; i++) {
            if (i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, blackGlass);
            }
        }
    }

    /**
     * 获取玩家有权限的称号列表
     */
    private List<TitleInfo> getAvailableTitles(Player player) {
        List<TitleInfo> titles = new ArrayList<>();
        Map<Integer, String> allTitles = plugin.getPlayerTitles();

        if (allTitles == null || allTitles.isEmpty()) {
            return titles;
        }

        // TreeMap 已按 ID 排序
        for (Map.Entry<Integer, String> entry : allTitles.entrySet()) {
            int id = entry.getKey();
            String prefix = entry.getValue();
            String permission = "xlr.playertitle." + id;

            if (player.hasPermission(permission)) {
                titles.add(new TitleInfo(id, prefix));
            }
        }

        return titles;
    }

    /**
     * 显示称号到 GUI
     */
    private void displayTitles(Inventory gui, Player player, List<TitleInfo> titles, int page) {
        int startIndex = page * TITLES_PER_PAGE;
        int endIndex = Math.min(startIndex + TITLES_PER_PAGE, titles.size());

        int slotIndex = 0; // GUI 中的称号槽位索引（从第2行第2列开始）

        for (int i = startIndex; i < endIndex; i++) {
            TitleInfo titleInfo = titles.get(i);

            // 计算 GUI 槽位（第2行第2列开始，共4行7列 = 28个格子）
            int row = slotIndex / 7; // 0-3
            int col = slotIndex % 7; // 0-6
            int guiSlot = 10 + (row * 9) + col; // 从索引10开始

            // 创建称号命名牌
            ItemStack nameTag = createTitleNameTag(player, titleInfo);
            gui.setItem(guiSlot, nameTag);

            slotIndex++;
        }
    }

    /**
     * 创建称号命名牌
     */
    private ItemStack createTitleNameTag(Player player, TitleInfo titleInfo) {
        // 处理颜色变量
        String displayName = plugin.processTitleColors(titleInfo.prefix());
        
        // 替换占位符 %ChatPrefix%
        displayName = displayName.replace("%ChatPrefix%", titleInfo.prefix());

        // 转换为传统颜色代码
        displayName = ChatColor.translateAlternateColorCodes('&', displayName);

        ItemStack nameTag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = nameTag.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(displayName);

            // 检查玩家当前穿戴的称号
            String currentTitle = plugin.getPlayerCurrentTitle(player);
            boolean isWearing = currentTitle != null && currentTitle.equals(titleInfo.prefix());

            // 从 Gui.yml 读取 Lore 配置
            List<String> lore = new ArrayList<>();
            List<String> templateLore;
            
            if (isWearing) {
                // 穿戴时显示
                templateLore = plugin.getGuiConfig().getStringList("Page1.OnPlayerTitle.Lore");
                // 添加附魔特效
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                // 未穿戴时显示
                templateLore = plugin.getGuiConfig().getStringList("Page1.OffPlayerTitle.Lore");
            }
            
            // 处理 Lore 模板，插入 %Lore%
            for (String line : templateLore) {
                if (line.contains("%Lore%")) {
                    // 获取称号描述
                    List<String> titleLore = plugin.getPlayerTitleLore().get(titleInfo.id());
                    if (titleLore != null && !titleLore.isEmpty()) {
                        // 插入所有描述行，并处理颜色变量
                        for (String loreLine : titleLore) {
                            // 先处理颜色变量（如 %color4%）
                            String processedLore = loreLine;
                            for (Map.Entry<String, String> entry : plugin.getColorVariables().entrySet()) {
                                if (processedLore.contains(entry.getKey())) {
                                    String gradientConfig = entry.getValue();
                                    String placeholder = entry.getKey();
                                    
                                    // 提取占位符后面的文本
                                    int placeholderIndex = processedLore.indexOf(placeholder);
                                    if (placeholderIndex != -1) {
                                        String afterPlaceholder = processedLore.substring(placeholderIndex + placeholder.length());
                                        String beforePlaceholder = processedLore.substring(0, placeholderIndex);
                                        
                                        // 对文本应用渐变
                                        String gradientText = plugin.applyGradientForGui(gradientConfig, afterPlaceholder);
                                        
                                        // 重新组合
                                        processedLore = beforePlaceholder + gradientText;
                                    }
                                }
                            }
                            // 转换传统颜色代码
                            lore.add(ChatColor.translateAlternateColorCodes('&', processedLore));
                        }
                    }
                    // 如果 Lore 为空，则 %Lore% 不插入任何内容
                } else {
                    // 普通行，直接添加
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }

            meta.setLore(lore);
            nameTag.setItemMeta(meta);
        }

        return nameTag;
    }

    /**
     * 设置分页按钮
     */
    private void setupPageButton(Inventory gui, Player player, int currentPage, int totalPages) {
        int pageButtonSlot = 49; // 第6行第5格

        if (currentPage == 0) {
            // 第1页：显示下一页按钮
            if (totalPages > 1) {
                // 有第2页，显示下一页按钮
                String materialName = plugin.getGuiConfig().getString("Page1.Next.material", "WHITE_STAINED_GLASS_PANE");
                Material material = Material.valueOf(materialName);
                String nextName = plugin.getGuiConfig().getString("Page1.Next.name", "&f下一页");
                List<String> nextLore = plugin.getGuiConfig().getStringList("Page1.Next.Lore");
                ItemStack nextPage = createItemWithLore(material, nextName, nextLore);
                gui.setItem(pageButtonSlot, nextPage);
            } else {
                // 只有一页，显示黑色玻璃板
                ItemStack noPage = createItem(Material.BLACK_STAINED_GLASS_PANE, "&0");
                gui.setItem(pageButtonSlot, noPage);
            }
        } else {
            // 第2页及以后：显示返回上一页按钮
            String materialName = plugin.getGuiConfig().getString("Page2.Back.material", "RED_STAINED_GLASS_PANE");
            Material material = Material.valueOf(materialName);
            String backName = plugin.getGuiConfig().getString("Page2.Back.name", "&f上一页");
            List<String> backLore = plugin.getGuiConfig().getStringList("Page2.Back.Lore");
            ItemStack prevPage = createItemWithLore(material, backName, backLore);
            gui.setItem(pageButtonSlot, prevPage);
        }
    }

    /**
     * 创建物品
     */
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 处理物品名称的语言切换
            String displayName = processItemName(name, material);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建带 Lore 的物品
     */
    private ItemStack createItemWithLore(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 处理物品名称的语言切换
            String displayName = processItemName(name, material);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            List<String> translatedLore = new ArrayList<>();
            for (String line : lore) {
                translatedLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(translatedLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 处理 GUI 点击事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        Inventory topInventory = event.getView().getTopInventory();
        if (!TitleGuiHolder.isTitleGui(topInventory)) {
            return;
        }

        event.setCancelled(true); // 禁止拿取物品

        int slot = event.getSlot();

        // 1. 点击分页按钮（第6行第5格，索引49）
        if (slot == 49) {
            handlePageButton(player, topInventory);
            return;
        }

        // 2. 点击称号命名牌
        if (isTitleSlot(slot)) {
            handleTitleClick(player, topInventory, slot);
        }
    }

    /**
     * 处理 GUI 拖拽事件
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        if (TitleGuiHolder.isTitleGui(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    /**
     * 处理分页按钮点击
     */
    private void handlePageButton(Player player, Inventory gui) {
        int currentPage = playerCurrentPage.getOrDefault(player.getUniqueId(), 0);
        if (gui.getHolder() instanceof TitleGuiHolder titleHolder) {
            currentPage = titleHolder.getPage();
        }
        int totalPages = getTotalPages(getAvailableTitles(player).size());
        currentPage = normalizePage(currentPage, totalPages);

        if (totalPages <= 1) {
            return;
        }

        if (currentPage == 0) {
            // 第1页点击下一页按钮 → 打开第2页
            openTitleGUI(player, 1);
        } else {
            // 第2页点击返回上一页按钮 → 返回第1页
            openTitleGUI(player, currentPage - 1);
        }
    }

    /**
     * 处理称号点击
     */
    private void handleTitleClick(Player player, Inventory gui, int slot) {
        // 获取点击槽位的物品
        ItemStack clickedItem = gui.getItem(slot);
        if (clickedItem == null || clickedItem.getType() != Material.NAME_TAG) {
            return; // 点击的不是命名牌
        }
        
        // 从 GUI 槽位计算称号索引
        int row = (slot - 10) / 9;
        int col = (slot - 10) % 9;
        int titleIndex = row * 7 + col;

        int currentPage = playerCurrentPage.getOrDefault(player.getUniqueId(), 0);
        if (gui.getHolder() instanceof TitleGuiHolder titleHolder) {
            currentPage = titleHolder.getPage();
        }
        List<TitleInfo> availableTitles = getAvailableTitles(player);
        currentPage = normalizePage(currentPage, getTotalPages(availableTitles.size()));
        int globalIndex = currentPage * TITLES_PER_PAGE + titleIndex;

        if (globalIndex >= availableTitles.size()) {
            return;
        }

        TitleInfo clickedTitle = availableTitles.get(globalIndex);

        // 切换称号
        String currentTitle = plugin.getPlayerCurrentTitle(player);
        
        if (currentTitle != null && currentTitle.equals(clickedTitle.prefix())) {
            // 取消穿戴
            plugin.setPlayerCurrentTitle(player, null);
            // 从 Gui.yml 读取消息
            String equipMessage = plugin.getGuiConfig().getString("Message.Equipped", "&a[&c寄寄之家&a] &7已取消穿戴称号");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', equipMessage));
        } else {
            // 穿戴称号
            plugin.setPlayerCurrentTitle(player, clickedTitle.prefix());
            
            // 处理颜色后显示
            String processedTitle = plugin.processTitleColors(clickedTitle.prefix());
            processedTitle = ChatColor.translateAlternateColorCodes('&', processedTitle);
            // 从 Gui.yml 读取消息
            String equipDoneMessage = plugin.getGuiConfig().getString("Message.EquipDone", "&a[&e寄寄之家&a] &7成功穿戴称号: %title%");
            equipDoneMessage = equipDoneMessage.replace("%title%", processedTitle);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', equipDoneMessage));
        }
        
        // 刷新 GUI
        player.openInventory(createGUI(player, currentPage));
    }

    /**
     * 处理物品名称的语言切换
     * @param name 原始名称（来自配置文件）
     * @param material 物品材质
     * @return 处理后的名称
     */
    private String processItemName(String name, Material material) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // 配置里写了 & / § 或任意自定义文案时，一律使用配置原文（不受 DiaplayLanguage 影响）
        if (name.contains("&") || name.contains("§")) {
            return name;
        }

        String language = plugin.getConfig().getString("DiaplayLanguage", "zh-cn").toLowerCase();
        if (language.equals("en-us")) {
            return name;
        }

        // 仅当配置名为空或等于材质枚举英文名时，才替换为中文材质名
        if (name.equalsIgnoreCase(material.name())) {
            return plugin.getItemDisplayName(material);
        }
        return name;
    }

    /**
     * 检查是否是称号槽位
     */
    private boolean isTitleSlot(int slot) {
        // 称号槽位范围：第2-5行，第2-8列（索引10-44，排除边框）
        int row = slot / 9;
        int col = slot % 9;

        return row >= 1 && row <= 4 && col >= 1 && col <= 7;
    }

    /**
     * 称号信息类（使用 record 简化代码）
     */
    public record TitleInfo(int id, String prefix) {
    }
}
