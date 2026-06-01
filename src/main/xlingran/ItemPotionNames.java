package xlingran;

import org.bukkit.Material;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.Locale;

/**
 * 药水名称（无自定义 displayName 时）。
 */
public final class ItemPotionNames {

    private ItemPotionNames() {
    }

    public static boolean isPotionMaterial(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION;
    }

    public static String resolve(PotionMeta meta, Material material, String language) {
        PotionType type = meta.getBasePotionType();
        if (type != null && !"uncraftable".equals(type.getKey().getKey())) {
            return formatPotionType(type, material, language);
        }
        if (meta.hasCustomEffects() && !meta.getCustomEffects().isEmpty()) {
            return formatCustomEffectPotion(meta, material, language);
        }
        return formatBaseMaterial(material, language);
    }

    private static String formatPotionType(PotionType type, Material material, String language) {
        String baseKey = type.getKey().getKey();
        String prefix = splashPrefix(material, language);
        String effectName = Item.LANG_EN_US.equalsIgnoreCase(language)
                ? baseKey.replace('_', ' ')
                : potionKeyToChinese(baseKey);
        return prefix + effectName;
    }

    private static String formatCustomEffectPotion(PotionMeta meta, Material material, String language) {
        PotionEffect effect = meta.getCustomEffects().get(0);
        PotionEffectType effectType = effect.getType();
        String prefix = splashPrefix(material, language);
        if (Item.LANG_EN_US.equalsIgnoreCase(language)) {
            String name = effectType.getKey().getKey().replace('_', ' ');
            return prefix + name;
        }
        return prefix + effectKeyToChinese(effectType.getKey().getKey());
    }

    private static String splashPrefix(Material material, String language) {
        if (material == Material.SPLASH_POTION) {
            return Item.LANG_EN_US.equalsIgnoreCase(language) ? "splash " : "喷溅";
        }
        if (material == Material.LINGERING_POTION) {
            return Item.LANG_EN_US.equalsIgnoreCase(language) ? "lingering " : "滞留";
        }
        return "";
    }

    private static String formatBaseMaterial(Material material, String language) {
        if (Item.LANG_EN_US.equalsIgnoreCase(language)) {
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
