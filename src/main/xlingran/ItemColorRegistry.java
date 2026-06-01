package xlingran;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

/**
 * 物品材质聊天颜色（& 格式），覆盖全部可物品化材质。
 */
public final class ItemColorRegistry {

    private static final Map<Material, String> COLORS = new EnumMap<>(Material.class);

    static {
        for (Material material : Material.values()) {
            if (material.isItem()) {
                COLORS.put(material, inferColor(material));
            }
        }
        applyExplicitOverrides();
    }

    private ItemColorRegistry() {
    }

    public static String getColorCode(Material material) {
        return COLORS.getOrDefault(material, "&f");
    }

    private static void applyExplicitOverrides() {
        put("&7", Material.COAL_ORE, Material.COAL, Material.CHARCOAL);
        put("&f",
                Material.IRON_ORE, Material.IRON_INGOT, Material.RAW_IRON, Material.DEEPSLATE_IRON_ORE,
                Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE, Material.IRON_SWORD,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.IRON_NUGGET, Material.IRON_DOOR, Material.IRON_TRAPDOOR, Material.IRON_BARS, Material.IRON_BLOCK,
                Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.CHAIN);
        put("&6",
                Material.GOLD_ORE, Material.GOLD_INGOT, Material.RAW_GOLD, Material.DEEPSLATE_GOLD_ORE,
                Material.NETHER_GOLD_ORE, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL,
                Material.GOLDEN_HOE, Material.GOLDEN_SWORD, Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
                Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
                Material.GOLD_NUGGET, Material.GOLD_BLOCK, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        put("&b",
                Material.DIAMOND_ORE, Material.DIAMOND, Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND_PICKAXE,
                Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE, Material.DIAMOND_SWORD,
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.DIAMOND_BLOCK);
        put("&a", Material.EMERALD_ORE, Material.EMERALD, Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD_BLOCK);
        put("&9", Material.LAPIS_ORE, Material.LAPIS_LAZULI, Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_BLOCK);
        put("&c", Material.REDSTONE_ORE, Material.REDSTONE, Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE_BLOCK);
        put("&5",
                Material.NETHERITE_SCRAP, Material.NETHERITE_INGOT, Material.ANCIENT_DEBRIS, Material.NETHERITE_BLOCK,
                Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
                Material.NETHERITE_SWORD, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
        put("&6", Material.COPPER_ORE, Material.RAW_COPPER, Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT,
                Material.COPPER_BLOCK);
        put("&b", Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.GLASS_BOTTLE);
        put("&e",
                Material.EXPERIENCE_BOTTLE, Material.GLOWSTONE, Material.GLOW_INK_SAC, Material.GOLDEN_CARROT,
                Material.TOTEM_OF_UNDYING, Material.BREAD, Material.COOKIE, Material.CLOCK, Material.HONEY_BOTTLE,
                Material.WHEAT, Material.MELON_SLICE, Material.TORCH, Material.LANTERN, Material.SOUL_TORCH);
        put("&6", Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.BARREL);
        put("&d", Material.ENCHANTED_BOOK, Material.ENCHANTING_TABLE);
        put("&5", Material.ENDER_PEARL, Material.ENDER_EYE, Material.END_CRYSTAL, Material.DRAGON_BREATH, Material.SHULKER_SHELL);
        put("&c", Material.RED_DYE, Material.ROSE_BUSH, Material.POPPY, Material.APPLE, Material.BEETROOT);
        put("&9", Material.WATER_BUCKET, Material.POWDER_SNOW_BUCKET, Material.HEART_OF_THE_SEA, Material.NAUTILUS_SHELL);
        put("&2", Material.CACTUS, Material.BAMBOO, Material.VINE, Material.KELP, Material.SEAGRASS);
        put("&8", Material.FLINT, Material.GUNPOWDER, Material.BONE, Material.STRING, Material.FEATHER);
    }

    private static void put(String color, Material... materials) {
        for (Material material : materials) {
            COLORS.put(material, color);
        }
    }

    private static String inferColor(Material material) {
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
}
