package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class HopperPdc {

    private HopperPdc() {
    }

    public static boolean applyTemplate(Block block, HopperKeys keys, UUID ownerId, String templateName) {
        if (block == null || block.getType() != Material.HOPPER) {
            return false;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return false;
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(keys.template, PersistentDataType.STRING, templateName);
        pdc.set(keys.owner, PersistentDataType.STRING, ownerId.toString());
        tileState.update(true);
        return true;
    }
}
