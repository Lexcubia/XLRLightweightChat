package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.Powerable;

/**
 * 单漏斗红石名单：检测邻域红石控制信号（有信号=白名单语义）。
 */
public final class HopperRedstoneUtil {

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private HopperRedstoneUtil() {
    }

    public static boolean isSignalActive(Block hopper) {
        if (hopper == null || hopper.getType() != Material.HOPPER) {
            return false;
        }
        for (BlockFace face : ADJACENT_FACES) {
            if (hopper.getBlockPower(face) > 0) {
                return true;
            }
        }
        for (BlockFace face : ADJACENT_FACES) {
            Block neighbor = hopper.getRelative(face);
            if (isPoweredRedstoneSource(neighbor)) {
                return true;
            }
        }
        return hopper.isBlockPowered();
    }

    private static boolean isPoweredRedstoneSource(Block block) {
        if (block == null || block.getType().isAir()) {
            return false;
        }
        Material type = block.getType();
        if (type == Material.REDSTONE_BLOCK) {
            return true;
        }
        if (type == Material.REDSTONE_WIRE) {
            return block.getBlockPower() > 0;
        }
        if (block.getBlockData() instanceof Powerable powerable) {
            return powerable.isPowered();
        }
        if (block.getBlockData() instanceof AnaloguePowerable analogue) {
            return analogue.getPower() > 0;
        }
        return false;
    }
}
