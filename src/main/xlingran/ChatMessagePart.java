package xlingran;

/**
 * 聊天消息分段：普通文本或 [item] 物品段。
 */
public sealed interface ChatMessagePart permits ChatMessagePart.Text, ChatMessagePart.Item {

    record Text(String content) implements ChatMessagePart {
    }

    record Item(ItemDisplaySegment segment) implements ChatMessagePart {
    }
}
