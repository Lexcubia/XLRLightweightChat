package xlingran;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物品显示功能类
 * 负责处理 [item] 占位符的物品名称、悬浮提示等功能
 */
public class ShanItem {
    
    private final JavaPlugin plugin;
    
    public ShanItem(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 获取物品的显示名称
     * 格式: [物品名称X数量]
     * 使用 § 格式的颜色代码，避免被渐变颜色覆盖
     */
    public String getItemDisplayName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "§r[空手]§r";
        }
        
        String displayName;
        ItemMeta meta = item.getItemMeta();
        
        // 获取物品名称
        if (meta != null && meta.hasDisplayName()) {
            // 使用自定义名称（保留颜色）
            // 如果包含 & 颜色代码，转换为 § 格式
            displayName = ChatColor.translateAlternateColorCodes('&', meta.getDisplayName());
        } else {
            // 根据配置决定使用中文还是英文
            String language = plugin.getConfig().getString("DisitemLanguage", "zh-cn");
            
            if ("zh-cn".equalsIgnoreCase(language)) {
                // 使用中文名称
                displayName = getItemChineseName(item.getType());
            } else {
                // 使用英文名称（首字母大写）
                String typeName = item.getType().name();
                String[] parts = typeName.split("_");
                StringBuilder friendlyName = new StringBuilder();
                for (String part : parts) {
                    if (!friendlyName.isEmpty()) {
                        friendlyName.append(" ");
                    }
                    if (!part.isEmpty()) {
                        friendlyName.append(part.substring(0, 1).toUpperCase())
                                   .append(part.substring(1).toLowerCase());
                    }
                }
                displayName = "&f" + friendlyName;
            }
        }
        
        // 获取数量
        int amount = item.getAmount();
        
        // 格式: &b[物品名称 &eX 数量&b]
        // 使用 & 格式的颜色代码，最后会被 ChatColor.translateAlternateColorCodes() 转换
        if (amount > 1) {
            return "&b[" + displayName + " &eX " + amount + "&b]";
        } else {
            return "&b[" + displayName + "&b]";
        }
    }
    
    /**
     * 获取物品的中文名称
     */
    public String getItemChineseName(Material material) {
        // 常见物品的中文名称映射表
        switch (material) {
            // 工具类
            case DIAMOND_SWORD: return "&f钻石剑";
            case IRON_SWORD: return "&f铁剑";
            case GOLDEN_SWORD: return "&f金剑";
            case STONE_SWORD: return "&f石剑";
            case WOODEN_SWORD: return "&f木剑";
            
            case DIAMOND_PICKAXE: return "&f钻石镐";
            case IRON_PICKAXE: return "&f铁镐";
            case GOLDEN_PICKAXE: return "&f金镐";
            case STONE_PICKAXE: return "&f石镐";
            case WOODEN_PICKAXE: return "&f木镐";
            
            case DIAMOND_AXE: return "&f钻石斧";
            case IRON_AXE: return "&f铁斧";
            case GOLDEN_AXE: return "&f金斧";
            case STONE_AXE: return "&f石斧";
            case WOODEN_AXE: return "&f木斧";
            
            case DIAMOND_SHOVEL: return "&f钻石锹";
            case IRON_SHOVEL: return "&f铁锹";
            case GOLDEN_SHOVEL: return "&f金锹";
            case STONE_SHOVEL: return "&f石锹";
            case WOODEN_SHOVEL: return "&f木锹";
            
            case DIAMOND_HOE: return "&f钻石锄";
            case IRON_HOE: return "&f铁锄";
            case GOLDEN_HOE: return "&f金锄";
            case STONE_HOE: return "&f石锄";
            case WOODEN_HOE: return "&f木锄";
            
            // 盔甲类
            case DIAMOND_HELMET: return "&f钻石头盔";
            case IRON_HELMET: return "&f铁头盔";
            case GOLDEN_HELMET: return "&f金头盔";
            case CHAINMAIL_HELMET: return "&f锁链头盔";
            case LEATHER_HELMET: return "&f皮革头盔";
            
            case DIAMOND_CHESTPLATE: return "&f钻石胸甲";
            case IRON_CHESTPLATE: return "&f铁胸甲";
            case GOLDEN_CHESTPLATE: return "&f金胸甲";
            case CHAINMAIL_CHESTPLATE: return "&f锁链胸甲";
            case LEATHER_CHESTPLATE: return "&f皮革胸甲";
            
            case DIAMOND_LEGGINGS: return "&f钻石护腿";
            case IRON_LEGGINGS: return "&f铁护腿";
            case GOLDEN_LEGGINGS: return "&f金护腿";
            case CHAINMAIL_LEGGINGS: return "&f锁链护腿";
            case LEATHER_LEGGINGS: return "&f皮革护腿";
            
            case DIAMOND_BOOTS: return "&f钻石靴子";
            case IRON_BOOTS: return "&f铁靴子";
            case GOLDEN_BOOTS: return "&f金靴子";
            case CHAINMAIL_BOOTS: return "&f锁链靴子";
            case LEATHER_BOOTS: return "&f皮革靴子";
            
            // 材料类
            case DIAMOND: return "&f钻石";
            case IRON_INGOT: return "&f铁锭";
            case GOLD_INGOT: return "&f金锭";
            case EMERALD: return "&f绿宝石";
            case REDSTONE: return "&f红石";
            case LAPIS_LAZULI: return "&f青金石";
            case COAL: return "&f煤炭";
            
            // 药水类
            case POTION: return "&f药水";
            case SPLASH_POTION: return "&f喷溅型药水";
            case LINGERING_POTION: return "&f滞留型药水";
            case EXPERIENCE_BOTTLE: return "&f附魔之瓶";
            
            // 食物类
            case APPLE: return "&f苹果";
            case GOLDEN_APPLE: return "&f金苹果";
            case ENCHANTED_GOLDEN_APPLE: return "&f附魔金苹果";
            case BREAD: return "&f面包";
            case COOKED_BEEF: return "&f熟牛肉";
            case COOKED_PORKCHOP: return "&f熟猪排";
            case COOKED_CHICKEN: return "&f熟鸡肉";
            
            // 方块类
            case DIAMOND_BLOCK: return "&f钻石块";
            case IRON_BLOCK: return "&f铁块";
            case GOLD_BLOCK: return "&f金块";
            case EMERALD_BLOCK: return "&f绿宝石块";
            case STONE: return "&f石头";
            case OAK_PLANKS: return "&f橡木木板";
            case GLASS: return "&f玻璃";
            
            // 栅栏类
            case OAK_FENCE: return "&f橡木栅栏";
            case SPRUCE_FENCE: return "&f云杉木栅栏";
            case BIRCH_FENCE: return "&f白桦木栅栏";
            case JUNGLE_FENCE: return "&f丛林木栅栏";
            case ACACIA_FENCE: return "&f金合欢木栅栏";
            case DARK_OAK_FENCE: return "&f深色橡木栅栏";
            case CRIMSON_FENCE: return "&f绯红木栅栏";
            case WARPED_FENCE: return "&f诡异木栅栏";
            case BAMBOO_FENCE: return "&f竹栅栏";
            case NETHER_BRICK_FENCE: return "&f下界砖栅栏";
            
            // 栅栏门类
            case OAK_FENCE_GATE: return "&f橡木栅栏门";
            case SPRUCE_FENCE_GATE: return "&f云杉木栅栏门";
            case BIRCH_FENCE_GATE: return "&f白桦木栅栏门";
            case JUNGLE_FENCE_GATE: return "&f丛林木栅栏门";
            case ACACIA_FENCE_GATE: return "&f金合欢木栅栏门";
            case DARK_OAK_FENCE_GATE: return "&f深色橡木栅栏门";
            case CRIMSON_FENCE_GATE: return "&f绯红木栅栏门";
            case WARPED_FENCE_GATE: return "&f诡异木栅栏门";
            
            // 门类
            case OAK_DOOR: return "&f橡木门";
            case SPRUCE_DOOR: return "&f云杉木门";
            case BIRCH_DOOR: return "&f白桦木门";
            case JUNGLE_DOOR: return "&f丛林木门";
            case ACACIA_DOOR: return "&f金合欢木门";
            case DARK_OAK_DOOR: return "&f深色橡木门";
            case CRIMSON_DOOR: return "&f绯红木门";
            case WARPED_DOOR: return "&f诡异木门";
            case BAMBOO_DOOR: return "&f竹门";
            case IRON_DOOR: return "&f铁门";
            
            // 按钮类
            case OAK_BUTTON: return "&f橡木按钮";
            case SPRUCE_BUTTON: return "&f云杉木按钮";
            case BIRCH_BUTTON: return "&f白桦木按钮";
            case JUNGLE_BUTTON: return "&f丛林木按钮";
            case ACACIA_BUTTON: return "&f金合欢木按钮";
            case DARK_OAK_BUTTON: return "&f深色橡木按钮";
            case CRIMSON_BUTTON: return "&f绯红木按钮";
            case WARPED_BUTTON: return "&f诡异木按钮";
            case BAMBOO_BUTTON: return "&f竹按钮";
            case STONE_BUTTON: return "&f石头按钮";
            case POLISHED_BLACKSTONE_BUTTON: return "&f磨制黑石按钮";
            
            // 压力板类
            case OAK_PRESSURE_PLATE: return "&f橡木压力板";
            case SPRUCE_PRESSURE_PLATE: return "&f云杉木压力板";
            case BIRCH_PRESSURE_PLATE: return "&f白桦木压力板";
            case JUNGLE_PRESSURE_PLATE: return "&f丛林木压力板";
            case ACACIA_PRESSURE_PLATE: return "&f金合欢木压力板";
            case DARK_OAK_PRESSURE_PLATE: return "&f深色橡木压力板";
            case CRIMSON_PRESSURE_PLATE: return "&f绯红木压力板";
            case WARPED_PRESSURE_PLATE: return "&f诡异木压力板";
            case BAMBOO_PRESSURE_PLATE: return "&f竹压力板";
            case STONE_PRESSURE_PLATE: return "&f石头压力板";
            case POLISHED_BLACKSTONE_PRESSURE_PLATE: return "&f磨制黑石压力板";
            case LIGHT_WEIGHTED_PRESSURE_PLATE: return "&f轻质测重压力板";
            case HEAVY_WEIGHTED_PRESSURE_PLATE: return "&f重质测重压力板";
            
            // 台阶类
            case OAK_SLAB: return "&f橡木台阶";
            case SPRUCE_SLAB: return "&f云杉木台阶";
            case BIRCH_SLAB: return "&f白桦木台阶";
            case JUNGLE_SLAB: return "&f丛林木台阶";
            case ACACIA_SLAB: return "&f金合欢木台阶";
            case DARK_OAK_SLAB: return "&f深色橡木台阶";
            case CRIMSON_SLAB: return "&f绯红木台阶";
            case WARPED_SLAB: return "&f诡异木台阶";
            case BAMBOO_SLAB: return "&f竹台阶";
            case STONE_SLAB: return "&f石头台阶";
            case SMOOTH_STONE_SLAB: return "&f平滑石头台阶";
            case SANDSTONE_SLAB: return "&f砂岩台阶";
            case RED_SANDSTONE_SLAB: return "&f红砂岩台阶";
            case COBBLESTONE_SLAB: return "&f圆石台阶";
            case BRICK_SLAB: return "&f砖台阶";
            case STONE_BRICK_SLAB: return "&f石砖台阶";
            case NETHER_BRICK_SLAB: return "&f下界砖台阶";
            case QUARTZ_SLAB: return "&f石英台阶";
            case PURPUR_SLAB: return "&f紫珀台阶";
            case PRISMARINE_SLAB: return "&f海晶石台阶";
            case DARK_PRISMARINE_SLAB: return "&f暗海晶石台阶";
            case PRISMARINE_BRICK_SLAB: return "&f海晶石砖台阶";
            
            // 楼梯类
            case OAK_STAIRS: return "&f橡木楼梯";
            case SPRUCE_STAIRS: return "&f云杉木楼梯";
            case BIRCH_STAIRS: return "&f白桦木楼梯";
            case JUNGLE_STAIRS: return "&f丛林木楼梯";
            case ACACIA_STAIRS: return "&f金合欢木楼梯";
            case DARK_OAK_STAIRS: return "&f深色橡木楼梯";
            case CRIMSON_STAIRS: return "&f绯红木楼梯";
            case WARPED_STAIRS: return "&f诡异木楼梯";
            case BAMBOO_STAIRS: return "&f竹楼梯";
            case STONE_STAIRS: return "&f石头楼梯";
            case SANDSTONE_STAIRS: return "&f砂岩楼梯";
            case RED_SANDSTONE_STAIRS: return "&f红砂岩楼梯";
            case COBBLESTONE_STAIRS: return "&f圆石楼梯";
            case BRICK_STAIRS: return "&f砖楼梯";
            case STONE_BRICK_STAIRS: return "&f石砖楼梯";
            case NETHER_BRICK_STAIRS: return "&f下界砖楼梯";
            case QUARTZ_STAIRS: return "&f石英楼梯";
            case PURPUR_STAIRS: return "&f紫珀楼梯";
            case PRISMARINE_STAIRS: return "&f海晶石楼梯";
            case DARK_PRISMARINE_STAIRS: return "&f暗海晶石楼梯";
            case PRISMARINE_BRICK_STAIRS: return "&f海晶石砖楼梯";
            
            // 原木类
            case OAK_LOG: return "&f橡木原木";
            case SPRUCE_LOG: return "&f云杉木原木";
            case BIRCH_LOG: return "&f白桦木原木";
            case JUNGLE_LOG: return "&f丛林木原木";
            case ACACIA_LOG: return "&f金合欢木原木";
            case DARK_OAK_LOG: return "&f深色橡木原木";
            case CRIMSON_STEM: return "&f绯红木柄";
            case WARPED_STEM: return "&f诡异木柄";
            case BAMBOO_BLOCK: return "&f竹块";
            
            // 树叶类
            case OAK_LEAVES: return "&f橡树叶";
            case SPRUCE_LEAVES: return "&f云杉树叶";
            case BIRCH_LEAVES: return "&f白桦树叶";
            case JUNGLE_LEAVES: return "&f丛林树叶";
            case ACACIA_LEAVES: return "&f金合欢树叶";
            case DARK_OAK_LEAVES: return "&f深色橡树叶";
            case AZALEA_LEAVES: return "&f杜鹃树叶";
            case FLOWERING_AZALEA_LEAVES: return "&f盛开的杜鹃树叶";
            
            // 花类
            case POPPY: return "&f虞美人";
            case BLUE_ORCHID: return "&f兰花";
            case ALLIUM: return "&f绒球葱";
            case AZURE_BLUET: return "&f蓝花美耳草";
            case RED_TULIP: return "&f红色郁金香";
            case ORANGE_TULIP: return "&f橙色郁金香";
            case WHITE_TULIP: return "&f白色郁金香";
            case PINK_TULIP: return "&f粉色郁金香";
            case OXEYE_DAISY: return "&f滨菊";
            case CORNFLOWER: return "&f矢车菊";
            case LILY_OF_THE_VALLEY: return "&f铃兰";
            case WITHER_ROSE: return "&f凋零玫瑰";
            case SUNFLOWER: return "&f向日葵";
            case LILAC: return "&f丁香";
            case ROSE_BUSH: return "&f玫瑰丛";
            case PEONY: return "&f牡丹";
            case TORCHFLOWER: return "&f火把花";
            case PITCHER_PLANT: return "&f瓶子草";
            
            // 蘑菇类
            case RED_MUSHROOM: return "&f红色蘑菇";
            case BROWN_MUSHROOM: return "&f棕色蘑菇";
            case CRIMSON_FUNGUS: return "&f绯红菌";
            case WARPED_FUNGUS: return "&f诡异菌";
            
            // 草和植物类
            case SHORT_GRASS: return "&f草";
            case FERN: return "&f蕨";
            case DEAD_BUSH: return "&f枯灌木";
            case SEAGRASS: return "&f海草";
            case TALL_GRASS: return "&f高草";
            case LARGE_FERN: return "&f大型蕨";
            case VINE: return "&f藤蔓";
            case GLOW_LICHEN: return "&f发光地衣";
            
            // 沙子类
            case SAND: return "&f沙子";
            case RED_SAND: return "&f红沙";
            case GRAVEL: return "&f沙砾";
            
            // 矿石类
            case COAL_ORE: return "&f煤矿石";
            case IRON_ORE: return "&f铁矿石";
            case GOLD_ORE: return "&f金矿石";
            case DIAMOND_ORE: return "&f钻石矿石";
            case EMERALD_ORE: return "&f绿宝石矿石";
            case LAPIS_ORE: return "&f青金矿石";
            case REDSTONE_ORE: return "&f红石矿石";
            case COPPER_ORE: return "&f铜矿石";
            case NETHER_GOLD_ORE: return "&f下界金矿石";
            case NETHER_QUARTZ_ORE: return "&f下界石英矿石";
            case ANCIENT_DEBRIS: return "&f远古残骸";
            
            // 铜制品类
            case COPPER_INGOT: return "&f铜锭";
            case COPPER_BLOCK: return "&f铜块";
            case CUT_COPPER: return "&f切制铜块";
            case EXPOSED_COPPER: return "&f斑驳的铜块";
            case WEATHERED_COPPER: return "&f氧化的铜块";
            case OXIDIZED_COPPER: return "&f完全氧化的铜块";
            
            // 深板岩类
            case DEEPSLATE: return "&f深板岩";
            case COBBLED_DEEPSLATE: return "&f深板岩圆石";
            case POLISHED_DEEPSLATE: return "&f磨制深板岩";
            case DEEPSLATE_BRICKS: return "&f深板岩砖";
            case DEEPSLATE_TILES: return "&f深板岩瓦";
            case CRACKED_DEEPSLATE_BRICKS: return "&f裂纹深板岩砖";
            case CRACKED_DEEPSLATE_TILES: return "&f裂纹深板岩瓦";
            
            // 其他方块
            case BEDROCK: return "&f基岩";
            case OBSIDIAN: return "&f黑曜石";
            case CRYING_OBSIDIAN: return "&f哭泣的黑曜石";
            case GLOWSTONE: return "&f荧石";
            case SEA_LANTERN: return "&f海晶灯";
            case JACK_O_LANTERN: return "&f南瓜灯";
            case LANTERN: return "&f灯笼";
            case SOUL_LANTERN: return "&f灵魂灯笼";
            case TORCH: return "&f火把";
            case SOUL_TORCH: return "&f灵魂火把";
            case END_ROD: return "&f末地烛";
            
            // 床类
            case RED_BED: return "&f红色床";
            case WHITE_BED: return "&f白色床";
            case ORANGE_BED: return "&f橙色床";
            case MAGENTA_BED: return "&f品红色床";
            case LIGHT_BLUE_BED: return "&f淡蓝色床";
            case YELLOW_BED: return "&f黄色床";
            case LIME_BED: return "&f淡绿色床";
            case PINK_BED: return "&f粉色床";
            case GRAY_BED: return "&f灰色床";
            case LIGHT_GRAY_BED: return "&f淡灰色床";
            case CYAN_BED: return "&f青色床";
            case PURPLE_BED: return "&f紫色床";
            case BLUE_BED: return "&f蓝色床";
            case BROWN_BED: return "&f棕色床";
            case GREEN_BED: return "&f绿色床";
            case BLACK_BED: return "&f黑色床";
            
            // 地毯类
            case RED_CARPET: return "&f红色地毯";
            case WHITE_CARPET: return "&f白色地毯";
            case ORANGE_CARPET: return "&f橙色地毯";
            case MAGENTA_CARPET: return "&f品红色地毯";
            case LIGHT_BLUE_CARPET: return "&f淡蓝色地毯";
            case YELLOW_CARPET: return "&f黄色地毯";
            case LIME_CARPET: return "&f淡绿色地毯";
            case PINK_CARPET: return "&f粉色地毯";
            case GRAY_CARPET: return "&f灰色地毯";
            case LIGHT_GRAY_CARPET: return "&f淡灰色地毯";
            case CYAN_CARPET: return "&f青色地毯";
            case PURPLE_CARPET: return "&f紫色地毯";
            case BLUE_CARPET: return "&f蓝色地毯";
            case BROWN_CARPET: return "&f棕色地毯";
            case GREEN_CARPET: return "&f绿色地毯";
            case BLACK_CARPET: return "&f黑色地毯";
            
            // 其他
            case BOW: return "&f弓";
            case CROSSBOW: return "&f弩";
            case TRIDENT: return "&f三叉戟";
            case SHIELD: return "&f盾牌";
            case FISHING_ROD: return "&f钓鱼竿";
            case FLINT_AND_STEEL: return "&f打火石";
            case SHEARS: return "&f剪刀";
            case CLOCK: return "&f钟表";
            case COMPASS: return "&f指南针";
            case MAP: return "&f地图";
            case BOOK: return "&f书";
            case WRITTEN_BOOK: return "&f成书";
            case WRITABLE_BOOK: return "&f书与笔";
            
            default:
                // 如果没有中文映射，使用英文名称（首字母大写）
                String typeName = material.name();
                String[] parts = typeName.split("_");
                StringBuilder friendlyName = new StringBuilder();
                for (String part : parts) {
                    if (!friendlyName.isEmpty()) {
                        friendlyName.append(" ");
                    }
                    if (!part.isEmpty()) {
                        friendlyName.append(part.substring(0, 1).toUpperCase())
                                   .append(part.substring(1).toLowerCase());
                    }
                }
                return "&f" + friendlyName;
        }
    }
    
    /**
     * 创建带有悬浮提示的物品组件
     * 鼠标悬浮时显示物品名称、NBT、Lore
     */
    public BaseComponent[] createItemHoverComponent(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new BaseComponent[]{new TextComponent("§r[空手]§r")};
        }
        
        // 获取物品显示名称（已经是 § 格式）
        String displayName = getItemDisplayName(item);
        
        // 创建悬浮提示的 JSON 内容
        BaseComponent[] hoverComponents = createItemTooltipComponents(item);
        
        // 创建文本组件 - 将 & 格式的颜色代码转换为 § 格式
        // 注意：TextComponent 的构造函数会直接解析 § 颜色代码
        String coloredDisplayName = ChatColor.translateAlternateColorCodes('&', displayName);
        
        // 在开头添加 §r 重置符，清除前面的样式影响
        TextComponent itemComponent = new TextComponent("§r" + coloredDisplayName + "§r");
        
        // 设置悬浮事件 - 使用 SHOW_TEXT 显示完整的物品信息
        itemComponent.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            hoverComponents
        ));
        
        return new BaseComponent[]{itemComponent};
    }
    
    /**
     * 创建物品悬浮提示的组件数组
     * 按照游戏标准格式显示物品信息
     */
    public BaseComponent[] createItemTooltipComponents(ItemStack item) {
        List<BaseComponent> tooltip = new ArrayList<>();
        
        // 添加物品名称（标题）
        String displayName;
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null && meta.hasDisplayName()) {
            displayName = ChatColor.translateAlternateColorCodes('&', meta.getDisplayName());
        } else {
            String language = plugin.getConfig().getString("DisitemLanguage", "zh-cn");
            if ("zh-cn".equalsIgnoreCase(language)) {
                displayName = getItemChineseName(item.getType());
            } else {
                String typeName = item.getType().name();
                String[] parts = typeName.split("_");
                StringBuilder friendlyName = new StringBuilder();
                for (String part : parts) {
                    if (!friendlyName.isEmpty()) {
                        friendlyName.append(" ");
                    }
                    if (!part.isEmpty()) {
                        friendlyName.append(part.substring(0, 1).toUpperCase())
                                   .append(part.substring(1).toLowerCase());
                    }
                }
                displayName = "&f" + friendlyName;
            }
        }
        
        // 使用 ComponentBuilder 构建完整的悬浮提示，确保每行样式正确
        ComponentBuilder builder = new ComponentBuilder();
        
        // 标题行：物品名称（斜体）
        builder.append(displayName).italic(true);
        
        // 添加 Lore（描述）- 按照游戏标准格式
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                builder.append("\n"); // 名称和Lore之间的分隔
                // 重置样式，然后显示 Lore 内容
                for (String loreLine : lore) {
                    String coloredLore = ChatColor.translateAlternateColorCodes('&', loreLine);
                    builder.reset().append(coloredLore).italic(false);
                    builder.append("\n");
                }
            }
        }
        
        // 添加附魔信息
        if (meta != null && meta.hasEnchants()) {
            // 在 Lore 和附魔之间添加空行
            if (meta.hasLore()) {
                builder.append("\n");
            }
            
            List<Map.Entry<Enchantment, Integer>> enchants = new ArrayList<>(meta.getEnchants().entrySet());
            for (int i = 0; i < enchants.size(); i++) {
                Map.Entry<Enchantment, Integer> enchant = enchants.get(i);
                String enchantName = getEnchantmentChineseName(enchant.getKey());
                String level = getRomanNumber(enchant.getValue());
                String enchantLine = "§7" + enchantName + " " + level;
                builder.reset().append(enchantLine).italic(false);
                // 只在不是最后一个附魔时添加换行符
                if (i < enchants.size() - 1) {
                    builder.append("\n");
                }
            }
        }
        
        // 添加其他属性（不可破坏等）
        if (meta != null) {
            List<String> extraInfo = new ArrayList<>();
            
            // 检查是否有不可破坏属性
            if (meta.isUnbreakable()) {
                extraInfo.add("§7§o不可破坏");
            }
            
            if (!extraInfo.isEmpty()) {
                // 添加空行
                builder.append("\n");
                
                for (int i = 0; i < extraInfo.size(); i++) {
                    String info = extraInfo.get(i);
                    builder.reset().append(info).italic(false);
                    // 只在不是最后一个属性时添加换行符
                    if (i < extraInfo.size() - 1) {
                        builder.append("\n");
                    }
                }
            }
        }
        
        return builder.create();
    }
    
    /**
     * 获取附魔的中文名称
     */
    public String getEnchantmentChineseName(Enchantment enchant) {
        return switch (enchant.getKey().getKey()) {
            case "sharpness" -> "锋利";
            case "smite" -> "亡灵杀手";
            case "bane_of_arthropods" -> "节肢杀手";
            case "knockback" -> "击退";
            case "fire_aspect" -> "火焰附加";
            case "looting" -> "掠夺";
            case "sweeping" -> "横扫之刃";
            case "efficiency" -> "效率";
            case "silk_touch" -> "精准采集";
            case "unbreaking" -> "耐久";
            case "fortune" -> "时运";
            case "power" -> "力量";
            case "punch" -> "冲击";
            case "flame" -> "火焰";
            case "infinity" -> "无限";
            case "luck_of_the_sea" -> "海之眷顾";
            case "lure" -> "饵钓";
            case "loyalty" -> "忠诚";
            case "impaling" -> "穿刺";
            case "riptide" -> "激流";
            case "channeling" -> "引雷";
            case "multishot" -> "多重射击";
            case "piercing" -> "穿透";
            case "quick_charge" -> "快速装填";
            case "mending" -> "经验修补";
            case "vanishing_curse" -> "消失诅咒";
            case "binding_curse" -> "绑定诅咒";
            case "protection" -> "保护";
            case "fire_protection" -> "火焰保护";
            case "blast_protection" -> "爆炸保护";
            case "projectile_protection" -> "弹射物保护";
            case "respiration" -> "水下呼吸";
            case "aqua_affinity" -> "水下速掘";
            case "thorns" -> "荆棘";
            case "depth_strider" -> "深海探索者";
            case "frost_walker" -> "冰霜行者";
            case "soul_speed" -> "灵魂疾行";
            case "swift_sneak" -> "迅捷潜行";
            case "feather_falling" -> "摔落缓冲";
            default -> enchant.getKey().getKey();
        };
    }
    
    /**
     * 将数字转换为罗马数字
     */
    public String getRomanNumber(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }
}
