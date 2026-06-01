package xlingran;

import org.bukkit.inventory.ItemStack;

/**
 * 聊天中 [item] 展示片段：展示文本（& 格式）与物品快照（用于 SHOW_ITEM 悬浮）。
 */
public record ItemDisplaySegment(String displayText, ItemStack snapshot) {
}
