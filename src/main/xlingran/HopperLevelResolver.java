package xlingran;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import xlingran.gui.HopperLevelDef;
import xlingran.gui.UpdateConfig;

public final class HopperLevelResolver {

    private HopperLevelResolver() {
    }

    public static HopperLevelDef resolveForBlock(Block block, HopperKeys keys, UpdateConfig config) {
        if (config == null) {
            return null;
        }
        String levelId = readLevelId(block, keys);
        if (levelId != null) {
            HopperLevelDef level = config.getLevel(levelId);
            if (level != null) {
                return level;
            }
        }
        return config.getDefault();
    }

    public static String readLevelId(Block block, HopperKeys keys) {
        if (block == null || keys == null) {
            return null;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return null;
        }
        return tileState.getPersistentDataContainer().get(keys.hopperLevel, PersistentDataType.STRING);
    }

    public static void applyLevelToLane(xlingran.core.HopperLane lane, Block block, HopperKeys keys, UpdateConfig config) {
        HopperLevelDef def = resolveForBlock(block, keys, config);
        if (def == null || lane == null) {
            return;
        }
        lane.setTransferTick(def.transferTick());
        lane.setMaxItem(def.maxItem());
    }
}
