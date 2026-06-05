package xlingran;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块内漏斗收集（TileEntity 扫描，避免全高度方块遍历）。
 */
public final class HopperChunkScanUtil {

    private HopperChunkScanUtil() {
    }

    public static List<Block> hoppersInChunk(Chunk chunk) {
        List<Block> out = new ArrayList<>();
        if (chunk == null) {
            return out;
        }
        for (BlockState state : chunk.getTileEntities()) {
            if (state != null && state.getType() == Material.HOPPER) {
                out.add(state.getBlock());
            }
        }
        return out;
    }
}
