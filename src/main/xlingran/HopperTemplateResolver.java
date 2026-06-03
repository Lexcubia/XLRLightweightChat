package xlingran;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class HopperTemplateResolver {

    private HopperTemplateResolver() {
    }

    public static HopperTemplate resolve(Block block, HopperKeys keys, HopperTemplateManager templateManager) {
        if (block == null || templateManager == null || keys == null) {
            return null;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return null;
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String templateName = pdc.get(keys.template, PersistentDataType.STRING);
        String ownerStr = pdc.get(keys.owner, PersistentDataType.STRING);
        if (templateName == null || ownerStr == null) {
            return null;
        }
        try {
            UUID owner = UUID.fromString(ownerStr);
            return templateManager.getTemplate(owner, templateName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean hasValidTemplate(Block block, HopperKeys keys, HopperTemplateManager templateManager) {
        return resolve(block, keys, templateManager) != null;
    }
}
