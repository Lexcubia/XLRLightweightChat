package xlingran;

import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 聊天 SHOW_ITEM 悬浮。兼容 Spigot 1.21.1 内置 bungeecord-chat 1.20-R0.2（标准 Item + ItemTag）。
 */
public final class ItemHoverUtil {

    private static final Logger LOGGER = Bukkit.getLogger();

    private ItemHoverUtil() {
    }

    public static HoverEvent createShowItemHover(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        try {
            return createShowItemHoverInternal(stack.clone());
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[XLRLightweightChat] 构建物品悬浮失败: " + t.getMessage(), t);
            return null;
        }
    }

    private static HoverEvent createShowItemHoverInternal(ItemStack stack) {
        String id = stack.getType().getKey().toString();
        int count = stack.getAmount();
        ItemTag tag = resolveItemTag(stack);
        Item item = tag != null ? new Item(id, count, tag) : new Item(id, count, null);
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, item);
    }

    /**
     * 优先 legacy NBT（Bungee ItemTag），否则用 ItemMeta 合成 SNBT 风格展示数据。
     */
    private static ItemTag resolveItemTag(ItemStack stack) {
        ItemTag fromMeta = tryItemTagFromMeta(stack);
        if (fromMeta != null) {
            return fromMeta;
        }
        return tryItemTagFromRebuiltStack(stack);
    }

    private static ItemTag tryItemTagFromMeta(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        try {
            String nbt = meta.getAsString();
            if (nbt != null && !nbt.isBlank()) {
                return ItemTag.ofNbt(nbt);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] getAsString 失败", t);
        }
        return null;
    }

    private static ItemTag tryItemTagFromRebuiltStack(ItemStack stack) {
        try {
            String spec = stack.getType().getKey().toString();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String componentSpec = meta.getAsComponentString();
                if (componentSpec != null && !componentSpec.isEmpty() && !"[]".equals(componentSpec)) {
                    spec = spec + componentSpec;
                }
            }
            ItemStack rebuilt = Bukkit.getItemFactory().createItemStack(spec);
            rebuilt.setAmount(stack.getAmount());
            ItemTag tag = tryItemTagFromMeta(rebuilt);
            if (tag != null) {
                return tag;
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] createItemStack 重建失败", t);
        }
        return buildLegacyDisplayTag(stack);
    }

    /**
     * 将 displayName / lore / 药水信息写成 Bungee ItemTag 可识别的 legacy display 结构。
     */
    private static ItemTag buildLegacyDisplayTag(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        boolean hasDisplay = meta.hasDisplayName();
        boolean hasLore = meta.hasLore();
        boolean hasPotion = meta instanceof PotionMeta potionMeta && hasPotionData(potionMeta);
        if (!hasDisplay && !hasLore && !hasPotion) {
            return null;
        }
        StringBuilder nbt = new StringBuilder("{");
        boolean needComma = false;
        if (hasDisplay || hasLore) {
            nbt.append("display:{");
            if (hasDisplay) {
                nbt.append("Name:").append(jsonQuoted(meta.getDisplayName()));
            }
            if (hasLore) {
                if (hasDisplay) {
                    nbt.append(',');
                }
                nbt.append("Lore:[");
                List<String> lore = meta.getLore();
                if (lore != null) {
                    for (int i = 0; i < lore.size(); i++) {
                        if (i > 0) {
                            nbt.append(',');
                        }
                        nbt.append(jsonQuoted(lore.get(i)));
                    }
                }
                nbt.append(']');
            }
            nbt.append('}');
            needComma = true;
        }
        if (hasPotion && meta instanceof PotionMeta potionMeta) {
            if (needComma) {
                nbt.append(',');
            }
            appendPotionNbt(nbt, potionMeta);
        }
        nbt.append('}');
        try {
            return ItemTag.ofNbt(nbt.toString());
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] ItemTag.ofNbt 失败", t);
            return null;
        }
    }

    private static boolean hasPotionData(PotionMeta meta) {
        if (meta.getBasePotionType() != null
                && !"uncraftable".equals(meta.getBasePotionType().getKey().getKey())) {
            return true;
        }
        return meta.hasCustomEffects() && !meta.getCustomEffects().isEmpty();
    }

    private static void appendPotionNbt(StringBuilder nbt, PotionMeta meta) {
        if (meta.getBasePotionType() != null) {
            String key = meta.getBasePotionType().getKey().getKey();
            if (!"uncraftable".equals(key)) {
                nbt.append("Potion:\"minecraft:").append(key).append('"');
                return;
            }
        }
        if (meta.hasCustomEffects() && !meta.getCustomEffects().isEmpty()) {
            nbt.append("CustomPotionEffects:[");
            List<org.bukkit.potion.PotionEffect> effects = meta.getCustomEffects();
            for (int i = 0; i < effects.size(); i++) {
                if (i > 0) {
                    nbt.append(',');
                }
                org.bukkit.potion.PotionEffect effect = effects.get(i);
                nbt.append('{');
                nbt.append("Id:").append(effect.getType().getId());
                nbt.append(",Amplifier:").append(effect.getAmplifier());
                nbt.append(",Duration:").append(effect.getDuration());
                nbt.append(",Ambient:").append(effect.isAmbient() ? 1 : 0);
                nbt.append(",ShowParticles:").append(effect.hasParticles() ? 1 : 0);
                nbt.append(",ShowIcon:").append(effect.hasIcon() ? 1 : 0);
                nbt.append('}');
            }
            nbt.append(']');
        }
    }

    private static String jsonQuoted(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return '"' + escaped + '"';
    }
}
