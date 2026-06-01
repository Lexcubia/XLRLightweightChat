package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 物品展示（Display）、药水名（PotionNames）、材质颜色（ColorRegistry）。
 */
public class Item {

    private static final Map<Material, String> MATERIAL_COLORS = new EnumMap<>(Material.class);

    static {
        for (Material material : Material.values()) {
            if (material.isItem()) {
                MATERIAL_COLORS.put(material, inferMaterialColor(material));
            }
        }
        applyExplicitColorOverrides();
    }

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
        return new ItemDisplaySegment(formatDisplayText(hand, language));
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
        if (meta instanceof PotionMeta potionMeta && isPotionMaterial(stack.getType())) {
            String potionName = resolvePotionName(potionMeta, stack.getType(), language);
            return getColorCode(stack.getType()) + potionName;
        }
        String translated = getDisplayName(stack.getType(), language);
        return getColorCode(stack.getType()) + translated;
    }

    public String getDisplayName(Material material, String language) {
        if (LANG_EN_US.equalsIgnoreCase(language)) {
            return getEnglishName(material);
        }
        return getChineseName(material);
    }

    public String getChineseName(Material material) {
        return ItemNamesZh.name(material);
    }

    private String getEnglishName(Material material) {
        return material.name().replace('_', ' ').toLowerCase();
    }

    public String getColorCode(Material material) {
        return MATERIAL_COLORS.getOrDefault(material, "&f");
    }

    // —— ColorRegistry ——

    private static void applyExplicitColorOverrides() {
        putColor("&7", Material.COAL_ORE, Material.COAL, Material.CHARCOAL);
        putColor("&f",
                Material.IRON_ORE, Material.IRON_INGOT, Material.RAW_IRON, Material.DEEPSLATE_IRON_ORE,
                Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE, Material.IRON_SWORD,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.IRON_NUGGET, Material.IRON_DOOR, Material.IRON_TRAPDOOR, Material.IRON_BARS, Material.IRON_BLOCK,
                Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.CHAIN);
        putColor("&6",
                Material.GOLD_ORE, Material.GOLD_INGOT, Material.RAW_GOLD, Material.DEEPSLATE_GOLD_ORE,
                Material.NETHER_GOLD_ORE, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL,
                Material.GOLDEN_HOE, Material.GOLDEN_SWORD, Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
                Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
                Material.GOLD_NUGGET, Material.GOLD_BLOCK, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        putColor("&b",
                Material.DIAMOND_ORE, Material.DIAMOND, Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND_PICKAXE,
                Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE, Material.DIAMOND_SWORD,
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.DIAMOND_BLOCK);
        putColor("&a", Material.EMERALD_ORE, Material.EMERALD, Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD_BLOCK);
        putColor("&9", Material.LAPIS_ORE, Material.LAPIS_LAZULI, Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_BLOCK);
        putColor("&c", Material.REDSTONE_ORE, Material.REDSTONE, Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE_BLOCK);
        putColor("&5",
                Material.NETHERITE_SCRAP, Material.NETHERITE_INGOT, Material.ANCIENT_DEBRIS, Material.NETHERITE_BLOCK,
                Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
                Material.NETHERITE_SWORD, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
        putColor("&6", Material.COPPER_ORE, Material.RAW_COPPER, Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT,
                Material.COPPER_BLOCK);
        putColor("&b", Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.GLASS_BOTTLE);
        putColor("&e",
                Material.EXPERIENCE_BOTTLE, Material.GLOWSTONE, Material.GLOW_INK_SAC, Material.GOLDEN_CARROT,
                Material.TOTEM_OF_UNDYING, Material.BREAD, Material.COOKIE, Material.CLOCK, Material.HONEY_BOTTLE,
                Material.WHEAT, Material.MELON_SLICE, Material.TORCH, Material.LANTERN, Material.SOUL_TORCH);
        putColor("&6", Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.BARREL);
        putColor("&d", Material.ENCHANTED_BOOK, Material.ENCHANTING_TABLE);
        putColor("&5", Material.ENDER_PEARL, Material.ENDER_EYE, Material.END_CRYSTAL, Material.DRAGON_BREATH, Material.SHULKER_SHELL);
        putColor("&c", Material.RED_DYE, Material.ROSE_BUSH, Material.POPPY, Material.APPLE, Material.BEETROOT);
        putColor("&9", Material.WATER_BUCKET, Material.POWDER_SNOW_BUCKET, Material.HEART_OF_THE_SEA, Material.NAUTILUS_SHELL);
        putColor("&2", Material.CACTUS, Material.BAMBOO, Material.VINE, Material.KELP, Material.SEAGRASS);
        putColor("&8", Material.FLINT, Material.GUNPOWDER, Material.BONE, Material.STRING, Material.FEATHER);
    }

    private static void putColor(String color, Material... materials) {
        for (Material material : materials) {
            MATERIAL_COLORS.put(material, color);
        }
    }

    private static String inferMaterialColor(Material material) {
        String name = material.name();
        if (name.contains("DIAMOND")) {
            return "&b";
        }
        if (name.contains("NETHERITE") || name.contains("ANCIENT_DEBRIS")) {
            return "&5";
        }
        if (name.contains("GOLD") || name.contains("GILDED")) {
            return "&6";
        }
        if (name.contains("IRON") || name.contains("CHAINMAIL")) {
            return "&f";
        }
        if (name.contains("EMERALD")) {
            return "&a";
        }
        if (name.contains("LAPIS")) {
            return "&9";
        }
        if (name.contains("REDSTONE")) {
            return "&c";
        }
        if (name.contains("COPPER")) {
            return "&6";
        }
        if (name.contains("COAL") || name.contains("CHARCOAL")) {
            return "&7";
        }
        if (name.contains("EXPERIENCE")) {
            return "&e";
        }
        if (name.contains("POTION") || name.endsWith("_BOTTLE")) {
            return "&b";
        }
        if (name.contains("LEATHER")) {
            return "&c";
        }
        if (name.contains("WOOD") || name.contains("PLANKS") || name.contains("_LOG")
                || name.contains("STEM") || name.contains("BAMBOO")) {
            return "&6";
        }
        if (name.contains("STONE") || name.contains("DEEPSLATE") || name.contains("COBBLE")
                || name.contains("ANDESITE") || name.contains("DIORITE") || name.contains("GRANITE")
                || name.contains("TUFF") || name.contains("CALCITE")) {
            return "&7";
        }
        if (name.contains("SAND")) {
            return "&e";
        }
        if (name.contains("GRASS") || name.contains("LEAVES") || name.contains("SAPLING")
                || name.contains("MOSS") || name.contains("AZALEA")) {
            return "&2";
        }
        if (name.contains("GLASS")) {
            return "&f";
        }
        if (name.contains("FIREWORK") || name.contains("TNT")) {
            return "&c";
        }
        if (name.contains("FISH") || name.contains("COD") || name.contains("SALMON")
                || name.contains("BUCKET")) {
            return "&9";
        }
        if (name.contains("ENCHANT") || name.contains("BOOK")) {
            return "&d";
        }
        return "&f";
    }

    // —— PotionNames ——

    private static boolean isPotionMaterial(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION;
    }

    private static String resolvePotionName(PotionMeta meta, Material material, String language) {
        PotionType type = meta.getBasePotionType();
        if (type != null && !"uncraftable".equals(type.getKey().getKey())) {
            return formatPotionType(type, material, language);
        }
        if (meta.hasCustomEffects() && !meta.getCustomEffects().isEmpty()) {
            return formatCustomEffectPotion(meta, material, language);
        }
        return formatPotionBaseMaterial(material, language);
    }

    private static String formatPotionType(PotionType type, Material material, String language) {
        String baseKey = type.getKey().getKey();
        String prefix = potionSplashPrefix(material, language);
        String effectName = LANG_EN_US.equalsIgnoreCase(language)
                ? baseKey.replace('_', ' ')
                : potionKeyToChinese(baseKey);
        return prefix + effectName;
    }

    private static String formatCustomEffectPotion(PotionMeta meta, Material material, String language) {
        PotionEffect effect = meta.getCustomEffects().get(0);
        PotionEffectType effectType = effect.getType();
        String prefix = potionSplashPrefix(material, language);
        if (LANG_EN_US.equalsIgnoreCase(language)) {
            return prefix + effectType.getKey().getKey().replace('_', ' ');
        }
        return prefix + effectKeyToChinese(effectType.getKey().getKey());
    }

    private static String potionSplashPrefix(Material material, String language) {
        if (material == Material.SPLASH_POTION) {
            return LANG_EN_US.equalsIgnoreCase(language) ? "splash " : "喷溅";
        }
        if (material == Material.LINGERING_POTION) {
            return LANG_EN_US.equalsIgnoreCase(language) ? "lingering " : "滞留";
        }
        return "";
    }

    private static String formatPotionBaseMaterial(Material material, String language) {
        if (LANG_EN_US.equalsIgnoreCase(language)) {
            return switch (material) {
                case SPLASH_POTION -> "splash potion";
                case LINGERING_POTION -> "lingering potion";
                default -> "potion";
            };
        }
        return switch (material) {
            case SPLASH_POTION -> "喷溅药水";
            case LINGERING_POTION -> "滞留药水";
            default -> "药水";
        };
    }

    private static String potionKeyToChinese(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("long_")) {
            return "长效" + effectKeyToChinese(normalized.substring(5));
        }
        if (normalized.startsWith("strong_")) {
            return "强效" + effectKeyToChinese(normalized.substring(7));
        }
        return effectKeyToChinese(normalized);
    }

    private static String effectKeyToChinese(String key) {
        return switch (key) {
            case "night_vision" -> "夜视药水";
            case "invisibility" -> "隐身药水";
            case "leaping", "jump_boost" -> "跳跃药水";
            case "fire_resistance" -> "抗火药水";
            case "swiftness", "speed" -> "迅捷药水";
            case "slowness" -> "缓慢药水";
            case "water_breathing" -> "水肺药水";
            case "instant_health", "healing" -> "治疗药水";
            case "instant_damage", "harming" -> "伤害药水";
            case "poison" -> "剧毒药水";
            case "regeneration" -> "再生药水";
            case "strength" -> "力量药水";
            case "weakness" -> "虚弱药水";
            case "luck" -> "幸运药水";
            case "turtle_master" -> "神龟药水";
            case "slow_falling" -> "缓降药水";
            case "oozing" -> "渗浆药水";
            case "weaving" -> "编织药水";
            case "wind_charged" -> "蓄风药水";
            case "infested" -> "虫蚀药水";
            default -> "药水";
        };
    }
}
