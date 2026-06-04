package xlingran;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class HopperBlockConfig {

    private final boolean redstoneListToggle;
    private final boolean reverseSuction;
    private final boolean hoverDisplay;

    public HopperBlockConfig(boolean redstoneListToggle, boolean reverseSuction, boolean hoverDisplay) {
        this.redstoneListToggle = redstoneListToggle;
        this.reverseSuction = reverseSuction;
        this.hoverDisplay = hoverDisplay;
    }

    public boolean isRedstoneListToggle() {
        return redstoneListToggle;
    }

    public boolean isReverseSuction() {
        return reverseSuction;
    }

    public boolean isHoverDisplay() {
        return hoverDisplay;
    }

    public HopperBlockConfig withRedstoneListToggle(boolean value) {
        return new HopperBlockConfig(value, reverseSuction, hoverDisplay);
    }

    public HopperBlockConfig withReverseSuction(boolean value) {
        return new HopperBlockConfig(redstoneListToggle, value, hoverDisplay);
    }

    public HopperBlockConfig withHoverDisplay(boolean value) {
        return new HopperBlockConfig(redstoneListToggle, reverseSuction, value);
    }

    public static HopperBlockConfig read(Block block, HopperKeys keys) {
        if (block == null || block.getType() != Material.HOPPER) {
            return defaults();
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return defaults();
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        boolean redstone = Boolean.TRUE.equals(pdc.get(keys.redstoneListToggle, PersistentDataType.BOOLEAN));
        boolean reverse = Boolean.TRUE.equals(pdc.get(keys.reverseSuction, PersistentDataType.BOOLEAN));
        boolean hover = Boolean.TRUE.equals(pdc.get(keys.hoverDisplay, PersistentDataType.BOOLEAN));
        return new HopperBlockConfig(redstone, reverse, hover);
    }

    public static void write(Block block, HopperKeys keys, HopperBlockConfig config) {
        if (block == null || block.getType() != Material.HOPPER || config == null) {
            return;
        }
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return;
        }
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.set(keys.redstoneListToggle, PersistentDataType.BOOLEAN, config.isRedstoneListToggle());
        pdc.set(keys.reverseSuction, PersistentDataType.BOOLEAN, config.isReverseSuction());
        pdc.set(keys.hoverDisplay, PersistentDataType.BOOLEAN, config.isHoverDisplay());
        tileState.update(true);
    }

    public static void initDefaults(Block block, HopperKeys keys) {
        write(block, keys, defaults());
    }

    public static boolean getEffectiveWhitelist(Block hopperBlock, HopperKeys keys, HopperTemplate template) {
        if (template == null) {
            return true;
        }
        HopperBlockConfig config = read(hopperBlock, keys);
        if (config.isRedstoneListToggle() && hopperBlock != null) {
            return hopperBlock.isBlockPowered();
        }
        return template.isWhitelist();
    }

    public static boolean isReverse(Block block, HopperKeys keys) {
        return read(block, keys).isReverseSuction();
    }

    private static HopperBlockConfig defaults() {
        return new HopperBlockConfig(false, false, false);
    }
}
