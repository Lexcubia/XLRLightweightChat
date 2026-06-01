package xlingran;

import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

/**
 * 物品名称、颜色与 [item] 聊天展示。
 */
public class Item {

    public static final String LANG_ZH_CN = "zh-cn";
    public static final String LANG_EN_US = "en-us";

    /**
     * 构建主手 [item] 展示段；无物品时返回 null。
     */
    public ItemDisplaySegment buildDisplaySegment(Player player, String language) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            return null;
        }
        ItemStack snapshot = hand.clone();
        String displayText = formatDisplayText(snapshot, language);
        return new ItemDisplaySegment(displayText, snapshot);
    }

    public HoverEvent createItemHoverEvent(ItemStack snapshot) {
        return ItemHoverUtil.createShowItemHover(snapshot);
    }

    /**
     * 展示文本 &7[名称 &fx数量&7]；自定义 displayName 保留 &/§ 色码，不使用材质色。
     */
    public String formatDisplayText(ItemStack stack, String language) {
        String itemName = resolveItemName(stack, language);
        int amount = stack.getAmount();
        return "&7[" + itemName + " &fx" + amount + "&7]";
    }

    /**
     * 解析物品名：优先 ItemMeta 自定义名，否则按语言取材质译名。
     */
    public String resolveItemName(ItemStack stack, String language) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String custom = meta.getDisplayName();
            if (custom != null && !custom.isEmpty()) {
                return custom;
            }
        }
        if (meta instanceof PotionMeta potionMeta && ItemPotionNames.isPotionMaterial(stack.getType())) {
            String potionName = ItemPotionNames.resolve(potionMeta, stack.getType(), language);
            return ItemColorRegistry.getColorCode(stack.getType()) + potionName;
        }
        String translated = getDisplayName(stack.getType(), language);
        return ItemColorRegistry.getColorCode(stack.getType()) + translated;
    }

    public String getDisplayName(Material material, String language) {
        if (LANG_EN_US.equalsIgnoreCase(language)) {
            return getEnglishName(material);
        }
        return getChineseName(material);
    }

    public String getChineseName(Material material) {
        return switch (material) {
            // 方块类
            case STONE -> "石头";
            case GRANITE -> "花岗岩";
            case POLISHED_GRANITE -> "磨制花岗岩";
            case DIORITE -> "闪长岩";
            case POLISHED_DIORITE -> "磨制闪长岩";
            case ANDESITE -> "安山岩";
            case POLISHED_ANDESITE -> "磨制安山岩";
            case GRASS_BLOCK -> "草方块";
            case DIRT -> "泥土";
            case COARSE_DIRT -> "砂土";
            case PODZOL -> "灰化土";
            case COBBLESTONE -> "圆石";
            case MOSSY_COBBLESTONE -> "覆苔圆石";
            case OAK_PLANKS -> "橡木木板";
            case SPRUCE_PLANKS -> "云杉木板";
            case BIRCH_PLANKS -> "白桦木板";
            case JUNGLE_PLANKS -> "丛林木板";
            case ACACIA_PLANKS -> "金合欢木板";
            case DARK_OAK_PLANKS -> "深色橡木木板";
            case CRIMSON_PLANKS -> "绯红木板";
            case WARPED_PLANKS -> "诡异木板";
            case OAK_LOG -> "橡木原木";
            case SPRUCE_LOG -> "云杉原木";
            case BIRCH_LOG -> "白桦原木";
            case JUNGLE_LOG -> "丛林原木";
            case ACACIA_LOG -> "金合欢原木";
            case DARK_OAK_LOG -> "深色橡木原木";
            case CRIMSON_STEM -> "绯红木柄";
            case WARPED_STEM -> "诡异木柄";
            case STRIPPED_OAK_LOG -> "去皮橡木原木";
            case STRIPPED_SPRUCE_LOG -> "去皮云杉原木";
            case STRIPPED_BIRCH_LOG -> "去皮白桦原木";
            case STRIPPED_JUNGLE_LOG -> "去皮丛林原木";
            case STRIPPED_ACACIA_LOG -> "去皮金合欢原木";
            case STRIPPED_DARK_OAK_LOG -> "去皮深色橡木原木";
            case SAND -> "沙子";
            case RED_SAND -> "红沙";
            case GRAVEL -> "砂砾";
            case COAL_ORE -> "煤矿石";
            case DEEPSLATE_COAL_ORE -> "深层煤矿石";
            case IRON_ORE -> "铁矿石";
            case DEEPSLATE_IRON_ORE -> "深层铁矿石";
            case GOLD_ORE -> "金矿石";
            case DEEPSLATE_GOLD_ORE -> "深层金矿石";
            case DIAMOND_ORE -> "钻石矿石";
            case DEEPSLATE_DIAMOND_ORE -> "深层钻石矿石";
            case EMERALD_ORE -> "绿宝石矿石";
            case DEEPSLATE_EMERALD_ORE -> "深层绿宝石矿石";
            case LAPIS_ORE -> "青金石矿石";
            case DEEPSLATE_LAPIS_ORE -> "深层青金石矿石";
            case REDSTONE_ORE -> "红石矿石";
            case DEEPSLATE_REDSTONE_ORE -> "深层红石矿石";
            case COPPER_ORE -> "铜矿石";
            case DEEPSLATE_COPPER_ORE -> "深层铜矿石";
            case GLASS -> "玻璃";
            case BRICK -> "砖块";
            case BOOKSHELF -> "书架";
            case CHEST -> "箱子";
            case TRAPPED_CHEST -> "陷阱箱";
            case CRAFTING_TABLE -> "工作台";
            case FURNACE -> "熔炉";
            case BLAST_FURNACE -> "高炉";
            case SMOKER -> "烟熏炉";
            case TORCH -> "火把";
            case SOUL_TORCH -> "灵魂火把";
            case LADDER -> "梯子";
            case OAK_DOOR -> "橡木门";
            case SPRUCE_DOOR -> "云杉门";
            case BIRCH_DOOR -> "白桦门";
            case JUNGLE_DOOR -> "丛林门";
            case ACACIA_DOOR -> "金合欢门";
            case DARK_OAK_DOOR -> "深色橡木门";
            case CRIMSON_DOOR -> "绯红木门";
            case WARPED_DOOR -> "诡异木门";
            case OAK_TRAPDOOR -> "橡木活板门";
            case SPRUCE_TRAPDOOR -> "云杉活板门";
            case BIRCH_TRAPDOOR -> "白桦活板门";
            case JUNGLE_TRAPDOOR -> "丛林活板门";
            case ACACIA_TRAPDOOR -> "金合欢活板门";
            case DARK_OAK_TRAPDOOR -> "深色橡木活板门";
            case CRIMSON_TRAPDOOR -> "绯红木活板门";
            case WARPED_TRAPDOOR -> "诡异木活板门";
            case IRON_DOOR -> "铁门";
            case IRON_TRAPDOOR -> "铁活板门";
            case STONE_PRESSURE_PLATE -> "石头压力板";
            case OAK_PRESSURE_PLATE -> "橡木压力板";
            case SPRUCE_PRESSURE_PLATE -> "云杉压力板";
            case BIRCH_PRESSURE_PLATE -> "白桦压力板";
            case JUNGLE_PRESSURE_PLATE -> "丛林压力板";
            case ACACIA_PRESSURE_PLATE -> "金合欢压力板";
            case DARK_OAK_PRESSURE_PLATE -> "深色橡木压力板";
            case CRIMSON_PRESSURE_PLATE -> "绯红木压力板";
            case WARPED_PRESSURE_PLATE -> "诡异木压力板";
            case STONE_BUTTON -> "石头按钮";
            case OAK_BUTTON -> "橡木按钮";
            case SPRUCE_BUTTON -> "云杉按钮";
            case BIRCH_BUTTON -> "白桦按钮";
            case JUNGLE_BUTTON -> "丛林按钮";
            case ACACIA_BUTTON -> "金合欢按钮";
            case DARK_OAK_BUTTON -> "深色橡木按钮";
            case CRIMSON_BUTTON -> "绯红木按钮";
            case WARPED_BUTTON -> "诡异木按钮";
            case BEDROCK -> "基岩";
            case TNT -> "TNT";
            case OBSIDIAN -> "黑曜石";
            case CRYING_OBSIDIAN -> "哭泣的黑曜石";
            case GLOWSTONE -> "荧石";
            case JACK_O_LANTERN -> "南瓜灯";
            case CARVED_PUMPKIN -> "雕刻过的南瓜";
            case PUMPKIN -> "南瓜";
            case MELON -> "西瓜";
            case HAY_BLOCK -> "干草块";
            case WHITE_WOOL -> "白色羊毛";
            case ORANGE_WOOL -> "橙色羊毛";
            case MAGENTA_WOOL -> "品红色羊毛";
            case LIGHT_BLUE_WOOL -> "淡蓝色羊毛";
            case YELLOW_WOOL -> "黄色羊毛";
            case LIME_WOOL -> "黄绿色羊毛";
            case PINK_WOOL -> "粉红色羊毛";
            case GRAY_WOOL -> "灰色羊毛";
            case LIGHT_GRAY_WOOL -> "淡灰色羊毛";
            case CYAN_WOOL -> "青色羊毛";
            case PURPLE_WOOL -> "紫色羊毛";
            case BLUE_WOOL -> "蓝色羊毛";
            case BROWN_WOOL -> "棕色羊毛";
            case GREEN_WOOL -> "绿色羊毛";
            case RED_WOOL -> "红色羊毛";
            case BLACK_WOOL -> "黑色羊毛";
            case SNOW_BLOCK -> "雪块";
            case ICE -> "冰";
            case PACKED_ICE -> "浮冰";
            case BLUE_ICE -> "蓝冰";
            case NETHERRACK -> "下界岩";
            case SOUL_SAND -> "灵魂沙";
            case SOUL_SOIL -> "灵魂土";
            case BASALT -> "玄武岩";
            case BLACKSTONE -> "黑石";
            case GILDED_BLACKSTONE -> "镶金黑石";
            case NETHER_QUARTZ_ORE -> "下界石英矿石";
            case NETHER_GOLD_ORE -> "下界金矿石";
            case ANCIENT_DEBRIS -> "远古残骸";
            case END_STONE -> "末地石";
            case PURPUR_BLOCK -> "紫珀方块";
            case PRISMARINE -> "海晶石";
            case PRISMARINE_BRICKS -> "海晶石砖";
            case DARK_PRISMARINE -> "暗海晶石";
            case SEA_LANTERN -> "海晶灯";
            case MAGMA_BLOCK -> "岩浆块";
            case NETHER_WART_BLOCK -> "下界疣方块";
            case WARPED_WART_BLOCK -> "诡异疣方块";
            case SHROOMLIGHT -> "菌光体";
            case HONEYCOMB_BLOCK -> "蜜脾块";
            case HONEY_BLOCK -> "蜂蜜块";
            case SLIME_BLOCK -> "黏液块";
            case SPONGE -> "海绵";
            case WET_SPONGE -> "湿海绵";
            case TERRACOTTA -> "陶瓦";
            case WHITE_TERRACOTTA -> "白色陶瓦";
            case ORANGE_TERRACOTTA -> "橙色陶瓦";
            case MAGENTA_TERRACOTTA -> "品红色陶瓦";
            case LIGHT_BLUE_TERRACOTTA -> "淡蓝色陶瓦";
            case YELLOW_TERRACOTTA -> "黄色陶瓦";
            case LIME_TERRACOTTA -> "黄绿色陶瓦";
            case PINK_TERRACOTTA -> "粉红色陶瓦";
            case GRAY_TERRACOTTA -> "灰色陶瓦";
            case LIGHT_GRAY_TERRACOTTA -> "淡灰色陶瓦";
            case CYAN_TERRACOTTA -> "青色陶瓦";
            case PURPLE_TERRACOTTA -> "紫色陶瓦";
            case BLUE_TERRACOTTA -> "蓝色陶瓦";
            case BROWN_TERRACOTTA -> "棕色陶瓦";
            case GREEN_TERRACOTTA -> "绿色陶瓦";
            case RED_TERRACOTTA -> "红色陶瓦";
            case BLACK_TERRACOTTA -> "黑色陶瓦";
            case WHITE_CONCRETE -> "白色混凝土";
            case ORANGE_CONCRETE -> "橙色混凝土";
            case MAGENTA_CONCRETE -> "品红色混凝土";
            case LIGHT_BLUE_CONCRETE -> "淡蓝色混凝土";
            case YELLOW_CONCRETE -> "黄色混凝土";
            case LIME_CONCRETE -> "黄绿色混凝土";
            case PINK_CONCRETE -> "粉红色混凝土";
            case GRAY_CONCRETE -> "灰色混凝土";
            case LIGHT_GRAY_CONCRETE -> "淡灰色混凝土";
            case CYAN_CONCRETE -> "青色混凝土";
            case PURPLE_CONCRETE -> "紫色混凝土";
            case BLUE_CONCRETE -> "蓝色混凝土";
            case BROWN_CONCRETE -> "棕色混凝土";
            case GREEN_CONCRETE -> "绿色混凝土";
            case RED_CONCRETE -> "红色混凝土";
            case BLACK_CONCRETE -> "黑色混凝土";
            case WHITE_GLAZED_TERRACOTTA -> "白色带釉陶瓦";
            case ORANGE_GLAZED_TERRACOTTA -> "橙色带釉陶瓦";
            case MAGENTA_GLAZED_TERRACOTTA -> "品红色带釉陶瓦";
            case LIGHT_BLUE_GLAZED_TERRACOTTA -> "淡蓝色带釉陶瓦";
            case YELLOW_GLAZED_TERRACOTTA -> "黄色带釉陶瓦";
            case LIME_GLAZED_TERRACOTTA -> "黄绿色带釉陶瓦";
            case PINK_GLAZED_TERRACOTTA -> "粉红色带釉陶瓦";
            case GRAY_GLAZED_TERRACOTTA -> "灰色带釉陶瓦";
            case LIGHT_GRAY_GLAZED_TERRACOTTA -> "淡灰色带釉陶瓦";
            case CYAN_GLAZED_TERRACOTTA -> "青色带釉陶瓦";
            case PURPLE_GLAZED_TERRACOTTA -> "紫色带釉陶瓦";
            case BLUE_GLAZED_TERRACOTTA -> "蓝色带釉陶瓦";
            case BROWN_GLAZED_TERRACOTTA -> "棕色带釉陶瓦";
            case GREEN_GLAZED_TERRACOTTA -> "绿色带釉陶瓦";
            case RED_GLAZED_TERRACOTTA -> "红色带釉陶瓦";
            case BLACK_GLAZED_TERRACOTTA -> "黑色带釉陶瓦";
            case WHITE_STAINED_GLASS -> "白色染色玻璃";
            case ORANGE_STAINED_GLASS -> "橙色染色玻璃";
            case MAGENTA_STAINED_GLASS -> "品红色染色玻璃";
            case LIGHT_BLUE_STAINED_GLASS -> "淡蓝色染色玻璃";
            case YELLOW_STAINED_GLASS -> "黄色染色玻璃";
            case LIME_STAINED_GLASS -> "黄绿色染色玻璃";
            case PINK_STAINED_GLASS -> "粉红色染色玻璃";
            case GRAY_STAINED_GLASS -> "灰色染色玻璃";
            case LIGHT_GRAY_STAINED_GLASS -> "淡灰色染色玻璃";
            case CYAN_STAINED_GLASS -> "青色染色玻璃";
            case PURPLE_STAINED_GLASS -> "紫色染色玻璃";
            case BLUE_STAINED_GLASS -> "蓝色染色玻璃";
            case BROWN_STAINED_GLASS -> "棕色染色玻璃";
            case GREEN_STAINED_GLASS -> "绿色染色玻璃";
            case RED_STAINED_GLASS -> "红色染色玻璃";
            case BLACK_STAINED_GLASS -> "黑色染色玻璃";
            
            // 玻璃板类
            case WHITE_STAINED_GLASS_PANE -> "白色玻璃板";
            case ORANGE_STAINED_GLASS_PANE -> "橙色玻璃板";
            case MAGENTA_STAINED_GLASS_PANE -> "品红色玻璃板";
            case LIGHT_BLUE_STAINED_GLASS_PANE -> "淡蓝色玻璃板";
            case YELLOW_STAINED_GLASS_PANE -> "黄色玻璃板";
            case LIME_STAINED_GLASS_PANE -> "黄绿色玻璃板";
            case PINK_STAINED_GLASS_PANE -> "粉红色玻璃板";
            case GRAY_STAINED_GLASS_PANE -> "灰色玻璃板";
            case LIGHT_GRAY_STAINED_GLASS_PANE -> "淡灰色玻璃板";
            case CYAN_STAINED_GLASS_PANE -> "青色玻璃板";
            case PURPLE_STAINED_GLASS_PANE -> "紫色玻璃板";
            case BLUE_STAINED_GLASS_PANE -> "蓝色玻璃板";
            case BROWN_STAINED_GLASS_PANE -> "棕色玻璃板";
            case GREEN_STAINED_GLASS_PANE -> "绿色玻璃板";
            case RED_STAINED_GLASS_PANE -> "红色玻璃板";
            case BLACK_STAINED_GLASS_PANE -> "黑色玻璃板";
            case WHITE_CARPET -> "白色地毯";
            case ORANGE_CARPET -> "橙色地毯";
            case MAGENTA_CARPET -> "品红色地毯";
            case LIGHT_BLUE_CARPET -> "淡蓝色地毯";
            case YELLOW_CARPET -> "黄色地毯";
            case LIME_CARPET -> "黄绿色地毯";
            case PINK_CARPET -> "粉红色地毯";
            case GRAY_CARPET -> "灰色地毯";
            case LIGHT_GRAY_CARPET -> "淡灰色地毯";
            case CYAN_CARPET -> "青色地毯";
            case PURPLE_CARPET -> "紫色地毯";
            case BLUE_CARPET -> "蓝色地毯";
            case BROWN_CARPET -> "棕色地毯";
            case GREEN_CARPET -> "绿色地毯";
            case RED_CARPET -> "红色地毯";
            case BLACK_CARPET -> "黑色地毯";
            case WHITE_BED -> "白色床";
            case ORANGE_BED -> "橙色床";
            case MAGENTA_BED -> "品红色床";
            case LIGHT_BLUE_BED -> "淡蓝色床";
            case YELLOW_BED -> "黄色床";
            case LIME_BED -> "黄绿色床";
            case PINK_BED -> "粉红色床";
            case GRAY_BED -> "灰色床";
            case LIGHT_GRAY_BED -> "淡灰色床";
            case CYAN_BED -> "青色床";
            case PURPLE_BED -> "紫色床";
            case BLUE_BED -> "蓝色床";
            case BROWN_BED -> "棕色床";
            case GREEN_BED -> "绿色床";
            case RED_BED -> "红色床";
            case BLACK_BED -> "黑色床";
            case BARREL -> "木桶";
            case SMITHING_TABLE -> "锻造台";
            case FLETCHING_TABLE -> "制箭台";
            case CARTOGRAPHY_TABLE -> "制图台";
            case LOOM -> "织布机";
            case COMPOSTER -> "堆肥桶";
            case BREWING_STAND -> "酿造台";
            case CAULDRON -> "炼药锅";
            case GRINDSTONE -> "砂轮";
            case STONECUTTER -> "切石机";
            case LECTERN -> "讲台";
            case ANVIL -> "铁砧";
            case CHIPPED_ANVIL -> "受损的铁";
            case DAMAGED_ANVIL -> "严重受损的铁砧";
            case ENCHANTING_TABLE -> "附魔台";
            case ENDER_CHEST -> "末影箱";
            case BEACON -> "信标";
            case CONDUIT -> "潮涌核心";
            case DRAGON_HEAD -> "末影龙头颅";
            case PLAYER_HEAD -> "玩家头颅";
            case ZOMBIE_HEAD -> "僵尸头颅";
            case CREEPER_HEAD -> "苦力怕头颅";
            case SKELETON_SKULL -> "骷髅头颅";
            case WITHER_SKELETON_SKULL -> "凋灵骷髅头颅";
            case PIGLIN_HEAD -> "猪灵头颅";
            case NOTE_BLOCK -> "音符盒";
            case JUKEBOX -> "唱片机";
            case DISPENSER -> "发射器";
            case DROPPER -> "投掷器";
            case HOPPER -> "漏斗";
            case REPEATER -> "红石中继器";
            case COMPARATOR -> "红石比较器";
            case OBSERVER -> "侦测器";
            case PISTON -> "活塞";
            case STICKY_PISTON -> "黏性活塞";
            case RAIL -> "铁轨";
            case POWERED_RAIL -> "充能铁轨";
            case DETECTOR_RAIL -> "探测铁轨";
            case ACTIVATOR_RAIL -> "激活铁轨";
            case MINECART -> "矿车";
            case CHEST_MINECART -> "运输矿车";
            case FURNACE_MINECART -> "动力矿车";
            case TNT_MINECART -> "TNT矿车";
            case HOPPER_MINECART -> "漏斗矿车";
            case COMMAND_BLOCK_MINECART -> "命令方块矿车";
            
            // 工具类
            case WOODEN_PICKAXE -> "木镐";
            case STONE_PICKAXE -> "石镐";
            case IRON_PICKAXE -> "铁镐";
            case GOLDEN_PICKAXE -> "金镐";
            case DIAMOND_PICKAXE -> "钻石镐";
            case NETHERITE_PICKAXE -> "下界合金镐";
            case WOODEN_AXE -> "木斧";
            case STONE_AXE -> "石斧";
            case IRON_AXE -> "铁斧";
            case GOLDEN_AXE -> "金斧";
            case DIAMOND_AXE -> "钻石斧";
            case NETHERITE_AXE -> "下界合金斧";
            case WOODEN_SHOVEL -> "木铲";
            case STONE_SHOVEL -> "石铲";
            case IRON_SHOVEL -> "铁铲";
            case GOLDEN_SHOVEL -> "金铲";
            case DIAMOND_SHOVEL -> "钻石铲";
            case NETHERITE_SHOVEL -> "下界合金铲";
            case WOODEN_HOE -> "木锄";
            case STONE_HOE -> "石锄";
            case IRON_HOE -> "铁锄";
            case GOLDEN_HOE -> "金锄";
            case DIAMOND_HOE -> "钻石锄";
            case NETHERITE_HOE -> "下界合金锄";
            case WOODEN_SWORD -> "木剑";
            case STONE_SWORD -> "石剑";
            case IRON_SWORD -> "铁剑";
            case GOLDEN_SWORD -> "金剑";
            case DIAMOND_SWORD -> "钻石剑";
            case NETHERITE_SWORD -> "下界合金剑";
            
            // 盔甲类
            case LEATHER_HELMET -> "皮革头盔";
            case LEATHER_CHESTPLATE -> "皮革胸甲";
            case LEATHER_LEGGINGS -> "皮革护腿";
            case LEATHER_BOOTS -> "皮革靴子";
            case CHAINMAIL_HELMET -> "锁链头盔";
            case CHAINMAIL_CHESTPLATE -> "锁链胸甲";
            case CHAINMAIL_LEGGINGS -> "锁链护腿";
            case CHAINMAIL_BOOTS -> "锁链靴子";
            case IRON_HELMET -> "铁头盔";
            case IRON_CHESTPLATE -> "铁胸甲";
            case IRON_LEGGINGS -> "铁护腿";
            case IRON_BOOTS -> "铁靴子";
            case GOLDEN_HELMET -> "金头盔";
            case GOLDEN_CHESTPLATE -> "金胸甲";
            case GOLDEN_LEGGINGS -> "金护腿";
            case GOLDEN_BOOTS -> "金靴子";
            case DIAMOND_HELMET -> "钻石头盔";
            case DIAMOND_CHESTPLATE -> "钻石胸甲";
            case DIAMOND_LEGGINGS -> "钻石护腿";
            case DIAMOND_BOOTS -> "钻石靴子";
            case NETHERITE_HELMET -> "下界合金头盔";
            case NETHERITE_CHESTPLATE -> "下界合金胸甲";
            case NETHERITE_LEGGINGS -> "下界合金护腿";
            case NETHERITE_BOOTS -> "下界合金靴子";
            
            // 食物类
            case APPLE -> "苹果";
            case BREAD -> "面包";
            case COOKED_PORKCHOP -> "熟猪排";
            case COOKED_BEEF -> "熟牛肉";
            case COOKED_CHICKEN -> "熟鸡肉";
            case COOKED_MUTTON -> "熟羊肉";
            case COOKED_RABBIT -> "熟兔肉";
            case COOKED_COD -> "熟鳕鱼";
            case COOKED_SALMON -> "熟鲑鱼";
            case GOLDEN_APPLE -> "金苹果";
            case ENCHANTED_GOLDEN_APPLE -> "附魔金苹果";
            case CARROT -> "胡萝卜";
            case POTATO -> "马铃薯";
            case BAKED_POTATO -> "烤马铃薯";
            case BEETROOT -> "甜菜根";
            case MELON_SLICE -> "西瓜片";
            case PUMPKIN_PIE -> "南瓜派";
            case COOKIE -> "曲奇";
            case CAKE -> "蛋糕";
            
            // 材料类
            case COAL -> "煤炭";
            case CHARCOAL -> "木炭";
            case IRON_INGOT -> "铁锭";
            case GOLD_INGOT -> "金锭";
            case DIAMOND -> "钻石";
            case EMERALD -> "绿宝石";
            case LAPIS_LAZULI -> "青金石";
            case REDSTONE -> "红石粉";
            case STICK -> "木棍";
            case STRING -> "线";
            case FEATHER -> "羽毛";
            case GUNPOWDER -> "火药";
            case FLINT -> "燧石";
            case BONE -> "骨头";
            case LEATHER -> "皮革";
            case RABBIT_HIDE -> "兔子皮";
            case PAPER -> "纸";
            case BOOK -> "书";
            case SLIME_BALL -> "黏液球";
            case EGG -> "鸡蛋";
            case GLOWSTONE_DUST -> "荧石粉";
            case INK_SAC -> "墨囊";
            case COCOA_BEANS -> "可可豆";
            
            // 药水类
            case POTION -> "药水";
            case SPLASH_POTION -> "喷溅药水";
            case LINGERING_POTION -> "滞留药水";
            case EXPERIENCE_BOTTLE -> "附魔之瓶";
            
            // 其他
            case BOW -> "弓";
            case CROSSBOW -> "弩";
            case SHIELD -> "盾牌";
            case FISHING_ROD -> "钓鱼竿";
            case FLINT_AND_STEEL -> "打火石";
            case SHEARS -> "剪刀";
            case COMPASS -> "指南针";
            case CLOCK -> "时钟";
            case MAP -> "地图";
            case NAME_TAG -> "命名牌";
            case LEAD -> "栓绳";
            case SADDLE -> "鞍";
            case WATER_BUCKET -> "水桶";
            case LAVA_BUCKET -> "岩浆桶";
            case MILK_BUCKET -> "奶桶";
            case BUCKET -> "桶";
            case ENDER_PEARL -> "末影珍珠";
            case ENDER_EYE -> "末影之眼";
            case BLAZE_ROD -> "烈焰棒";
            case GHAST_TEAR -> "恶魂之泪";
            case NETHER_STAR -> "下界之星";
            case DRAGON_BREATH -> "龙息";
            case TOTEM_OF_UNDYING -> "不死图腾";
            case ELYTRA -> "鞘翅";
            case TRIDENT -> "三叉戟";
            case TURTLE_HELMET -> "海龟壳";
            case PHANTOM_MEMBRANE -> "幻翼膜";
            case NAUTILUS_SHELL -> "鹦鹉螺壳";
            case HEART_OF_THE_SEA -> "海洋之心";
            case SPYGLASS -> "望远镜";
            case GOAT_HORN -> "山羊角";
            case ECHO_SHARD -> "回响碎片";
            case RECOVERY_COMPASS -> "追溯指针";
            case BRUSH -> "刷子";
            case OAK_BOAT -> "橡木船";
            case SPRUCE_BOAT -> "云杉船";
            case BIRCH_BOAT -> "白桦船";
            case JUNGLE_BOAT -> "丛林船";
            case ACACIA_BOAT -> "金合欢船";
            case DARK_OAK_BOAT -> "深色橡木船";
            case MANGROVE_BOAT -> "红树木船";
            case CHERRY_BOAT -> "樱花船";
            case BAMBOO_RAFT -> "竹筏";
            case OAK_SIGN -> "橡木告示牌";
            case SPRUCE_SIGN -> "云杉告示牌";
            case BIRCH_SIGN -> "白桦告示牌";
            case JUNGLE_SIGN -> "丛林告示牌";
            case ACACIA_SIGN -> "金合欢告示牌";
            case DARK_OAK_SIGN -> "深色橡木告示牌";
            case CRIMSON_SIGN -> "绯红木告示牌";
            case WARPED_SIGN -> "诡异木告示牌";
            case MANGROVE_SIGN -> "红树木告示牌";
            case BAMBOO_SIGN -> "竹子告示牌";
            case CHERRY_SIGN -> "樱花告示牌";
            case OAK_HANGING_SIGN -> "橡木悬挂告示牌";
            case SPRUCE_HANGING_SIGN -> "云杉悬挂告示牌";
            case BIRCH_HANGING_SIGN -> "白桦悬挂告示牌";
            case JUNGLE_HANGING_SIGN -> "丛林悬挂告示牌";
            case ACACIA_HANGING_SIGN -> "金合欢悬挂告示牌";
            case DARK_OAK_HANGING_SIGN -> "深色橡木悬挂告示牌";
            case CRIMSON_HANGING_SIGN -> "绯红木悬挂告示牌";
            case WARPED_HANGING_SIGN -> "诡异木悬挂告示牌";
            case MANGROVE_HANGING_SIGN -> "红树木悬挂告示牌";
            case BAMBOO_HANGING_SIGN -> "竹子悬挂告示牌";
            case CHERRY_HANGING_SIGN -> "樱花悬挂告示牌";
            case ARMOR_STAND -> "盔甲架";
            case PAINTING -> "画";
            case ITEM_FRAME -> "物品展示框";
            case GLOW_ITEM_FRAME -> "荧光物品展示框";
            case FLOWER_POT -> "花盆";
            case MUSIC_DISC_13 -> "音乐唱片";
            case MUSIC_DISC_CAT -> "音乐唱片";
            case MUSIC_DISC_BLOCKS -> "音乐唱片";
            case MUSIC_DISC_CHIRP -> "音乐唱片";
            case MUSIC_DISC_FAR -> "音乐唱片";
            case MUSIC_DISC_MALL -> "音乐唱片";
            case MUSIC_DISC_MELLOHI -> "音乐唱片";
            case MUSIC_DISC_STAL -> "音乐唱片";
            case MUSIC_DISC_STRAD -> "音乐唱片";
            case MUSIC_DISC_WARD -> "音乐唱片";
            case MUSIC_DISC_11 -> "音乐唱片";
            case MUSIC_DISC_WAIT -> "音乐唱片";
            case MUSIC_DISC_OTHERSIDE -> "音乐唱片";
            case MUSIC_DISC_5 -> "音乐唱片";
            case MUSIC_DISC_PIGSTEP -> "音乐唱片";
            case MUSIC_DISC_RELIC -> "音乐唱片";
            case DISC_FRAGMENT_5 -> "唱片残片";
            case ALLAY_SPAWN_EGG -> "悦灵刷怪蛋";
            case AXOLOTL_SPAWN_EGG -> "美西螈刷怪蛋";
            case BAT_SPAWN_EGG -> "蝙蝠刷怪蛋";
            case BEE_SPAWN_EGG -> "蜜蜂刷怪蛋";
            case BLAZE_SPAWN_EGG -> "烈焰人刷怪蛋";
            case CAT_SPAWN_EGG -> "猫刷怪蛋";
            case CAVE_SPIDER_SPAWN_EGG -> "洞穴蜘蛛刷怪蛋";
            case CHICKEN_SPAWN_EGG -> "鸡刷怪蛋";
            case COD_SPAWN_EGG -> "鳕鱼刷怪蛋";
            case COW_SPAWN_EGG -> "牛刷怪蛋";
            case CREEPER_SPAWN_EGG -> "苦力怕刷怪蛋";
            case DOLPHIN_SPAWN_EGG -> "海豚刷怪蛋";
            case DONKEY_SPAWN_EGG -> "驴刷怪蛋";
            case DROWNED_SPAWN_EGG -> "溺尸刷怪蛋";
            case ELDER_GUARDIAN_SPAWN_EGG -> "远古守卫者刷怪蛋";
            case ENDERMAN_SPAWN_EGG -> "末影人刷怪蛋";
            case ENDERMITE_SPAWN_EGG -> "末影螨刷怪蛋";
            case EVOKER_SPAWN_EGG -> "唤魔者刷怪蛋";
            case FOX_SPAWN_EGG -> "狐狸刷怪蛋";
            case FROG_SPAWN_EGG -> "青蛙刷怪蛋";
            case GHAST_SPAWN_EGG -> "恶魂刷怪蛋";
            case GLOW_SQUID_SPAWN_EGG -> "发光鱿鱼刷怪蛋";
            case GOAT_SPAWN_EGG -> "山羊刷怪蛋";
            case GUARDIAN_SPAWN_EGG -> "守卫者刷怪蛋";
            case HOGLIN_SPAWN_EGG -> "疣猪兽刷怪蛋";
            case HORSE_SPAWN_EGG -> "马刷怪蛋";
            case HUSK_SPAWN_EGG -> "尸壳刷怪蛋";
            case LLAMA_SPAWN_EGG -> "羊驼刷怪蛋";
            case MAGMA_CUBE_SPAWN_EGG -> "岩浆怪刷怪蛋";
            case MOOSHROOM_SPAWN_EGG -> "哞菇刷怪蛋";
            case MULE_SPAWN_EGG -> "骡刷怪蛋";
            case OCELOT_SPAWN_EGG -> "豹猫刷怪蛋";
            case PANDA_SPAWN_EGG -> "熊猫刷怪蛋";
            case PARROT_SPAWN_EGG -> "鹦鹉刷怪蛋";
            case PHANTOM_SPAWN_EGG -> "幻翼刷怪蛋";
            case PIG_SPAWN_EGG -> "猪刷怪蛋";
            case PIGLIN_SPAWN_EGG -> "猪灵刷怪蛋";
            case PIGLIN_BRUTE_SPAWN_EGG -> "猪灵蛮兵刷怪蛋";
            case PILLAGER_SPAWN_EGG -> "掠夺者刷怪蛋";
            case POLAR_BEAR_SPAWN_EGG -> "北极熊刷怪蛋";
            case PUFFERFISH_SPAWN_EGG -> "河豚刷怪蛋";
            case RABBIT_SPAWN_EGG -> "兔子刷怪蛋";
            case RAVAGER_SPAWN_EGG -> "劫掠兽刷怪蛋";
            case SALMON_SPAWN_EGG -> "鲑鱼刷怪蛋";
            case SHEEP_SPAWN_EGG -> "绵羊刷怪蛋";
            case SHULKER_SPAWN_EGG -> "潜影贝刷怪蛋";
            case SILVERFISH_SPAWN_EGG -> "蠹虫刷怪蛋";
            case SKELETON_SPAWN_EGG -> "骷髅刷怪蛋";
            case SKELETON_HORSE_SPAWN_EGG -> "骷髅马刷怪蛋";
            case SLIME_SPAWN_EGG -> "史莱姆刷怪蛋";
            case SNOW_GOLEM_SPAWN_EGG -> "雪傀儡刷怪蛋";
            case SPIDER_SPAWN_EGG -> "蜘蛛刷怪蛋";
            case SQUID_SPAWN_EGG -> "鱿鱼刷怪蛋";
            case STRAY_SPAWN_EGG -> "流浪者刷怪蛋";
            case STRIDER_SPAWN_EGG -> "炽足兽刷怪蛋";
            case TADPOLE_SPAWN_EGG -> "蝌蚪刷怪蛋";
            case TRADER_LLAMA_SPAWN_EGG -> "行商羊驼刷怪蛋";
            case TROPICAL_FISH_SPAWN_EGG -> "热带鱼刷怪蛋";
            case TURTLE_SPAWN_EGG -> "海龟刷怪蛋";
            case VEX_SPAWN_EGG -> "恼鬼刷怪蛋";
            case VILLAGER_SPAWN_EGG -> "村民刷怪蛋";
            case VINDICATOR_SPAWN_EGG -> "卫道士刷怪蛋";
            case WANDERING_TRADER_SPAWN_EGG -> "流浪商人刷怪蛋";
            case WARDEN_SPAWN_EGG -> "监守者刷怪蛋";
            case WITCH_SPAWN_EGG -> "女巫刷怪蛋";
            case WITHER_SKELETON_SPAWN_EGG -> "凋灵骷髅刷怪蛋";
            case WOLF_SPAWN_EGG -> "狼刷怪蛋";
            case ZOGLIN_SPAWN_EGG -> "僵尸疣猪兽刷怪蛋";
            case ZOMBIE_SPAWN_EGG -> "僵尸刷怪蛋";
            case ZOMBIE_HORSE_SPAWN_EGG -> "僵尸马刷怪蛋";
            case ZOMBIE_VILLAGER_SPAWN_EGG -> "僵尸村民刷怪蛋";
            case ZOMBIFIED_PIGLIN_SPAWN_EGG -> "僵尸猪灵刷怪蛋";
            case OAK_SAPLING -> "橡树苗";
            case SPRUCE_SAPLING -> "云杉树苗";
            case BIRCH_SAPLING -> "白桦树苗";
            case JUNGLE_SAPLING -> "丛林树苗";
            case ACACIA_SAPLING -> "金合欢树苗";
            case DARK_OAK_SAPLING -> "深色橡树苗";
            case MANGROVE_PROPAGULE -> "红树胎生苗";
            case CHERRY_SAPLING -> "樱花树苗";
            case AZALEA -> "杜鹃花丛";
            case FLOWERING_AZALEA -> "盛开的杜鹃花丛";
            case OAK_LEAVES -> "橡树树叶";
            case SPRUCE_LEAVES -> "云杉树叶";
            case BIRCH_LEAVES -> "白桦树叶";
            case JUNGLE_LEAVES -> "丛林树叶";
            case ACACIA_LEAVES -> "金合欢树叶";
            case DARK_OAK_LEAVES -> "深色橡树叶";
            case MANGROVE_LEAVES -> "红树木树叶";
            case AZALEA_LEAVES -> "杜鹃树叶";
            case FLOWERING_AZALEA_LEAVES -> "盛开的杜鹃树叶";
            
            // 默认：返回英文名称
            default -> material.name().replace('_', ' ').toLowerCase();
        
        };
    }

    private String getEnglishName(Material material) {
        return material.name().replace('_', ' ').toLowerCase();
    }

    public String getColorCode(Material material) {
        return ItemColorRegistry.getColorCode(material);
    }
}
