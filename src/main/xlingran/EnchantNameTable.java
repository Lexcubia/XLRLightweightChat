package xlingran;

import org.bukkit.enchantments.Enchantment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 附魔 registry 名（英文 key）→ 中文显示名。首版硬编码，默认使用中文。
 */
public final class EnchantNameTable {

    private static final Map<String, String> EN_TO_ZH = new LinkedHashMap<>();

    static {
        EN_TO_ZH.put("aqua_affinity", "水下速掘");
        EN_TO_ZH.put("bane_of_arthropods", "节肢杀手");
        EN_TO_ZH.put("binding_curse", "绑定诅咒");
        EN_TO_ZH.put("blast_protection", "爆炸保护");
        EN_TO_ZH.put("breach", "破甲");
        EN_TO_ZH.put("channeling", "引雷");
        EN_TO_ZH.put("density", "致密");
        EN_TO_ZH.put("depth_strider", "深海探索者");
        EN_TO_ZH.put("efficiency", "效率");
        EN_TO_ZH.put("feather_falling", "摔落缓冲");
        EN_TO_ZH.put("fire_aspect", "火焰附加");
        EN_TO_ZH.put("fire_protection", "火焰保护");
        EN_TO_ZH.put("flame", "火矢");
        EN_TO_ZH.put("fortune", "时运");
        EN_TO_ZH.put("frost_walker", "冰霜行者");
        EN_TO_ZH.put("impaling", "穿刺");
        EN_TO_ZH.put("infinity", "无限");
        EN_TO_ZH.put("knockback", "击退");
        EN_TO_ZH.put("looting", "抢夺");
        EN_TO_ZH.put("loyalty", "忠诚");
        EN_TO_ZH.put("luck_of_the_sea", "海之眷顾");
        EN_TO_ZH.put("lure", "饵钓");
        EN_TO_ZH.put("mending", "经验修补");
        EN_TO_ZH.put("multishot", "多重射击");
        EN_TO_ZH.put("piercing", "穿透");
        EN_TO_ZH.put("power", "力量");
        EN_TO_ZH.put("projectile_protection", "弹射物保护");
        EN_TO_ZH.put("protection", "保护");
        EN_TO_ZH.put("punch", "冲击");
        EN_TO_ZH.put("quick_charge", "快速装填");
        EN_TO_ZH.put("respiration", "水下呼吸");
        EN_TO_ZH.put("riptide", "激流");
        EN_TO_ZH.put("sharpness", "锋利");
        EN_TO_ZH.put("silk_touch", "精准采集");
        EN_TO_ZH.put("smite", "亡灵杀手");
        EN_TO_ZH.put("soul_speed", "灵魂疾行");
        EN_TO_ZH.put("sweeping_edge", "横扫之刃");
        EN_TO_ZH.put("swift_sneak", "迅捷潜行");
        EN_TO_ZH.put("thorns", "荆棘");
        EN_TO_ZH.put("unbreaking", "耐久");
        EN_TO_ZH.put("vanishing_curse", "消失诅咒");
        EN_TO_ZH.put("wind_burst", "风爆");
    }

    private EnchantNameTable() {
    }

    public static String getEnglishKey(Enchantment enchant) {
        if (enchant == null) {
            return "";
        }
        return enchant.getKey().getKey();
    }

    public static String getChineseName(Enchantment enchant) {
        if (enchant == null) {
            return "";
        }
        String key = enchant.getKey().getKey();
        return EN_TO_ZH.getOrDefault(key, fallbackChinese(key));
    }

    private static String fallbackChinese(String key) {
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
}
