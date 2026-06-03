package xlingran.feature;

import org.bukkit.block.Block;
import xlingran.HopperReservation;
import xlingran.HopperTemplate;
import xlingran.core.FilterSnapshot;
import xlingran.core.HopperLane;

import java.util.Set;

/**
 * 自动化扩展点：由 {@link xlingran.HopperTickService} 按序调用。
 */
public interface HopperFeature {

    /**
     * @return 本 tick 预留的漏斗槽位
     */
    Set<Integer> onTick(HopperLane lane, Block block, HopperTemplate template, FilterSnapshot snapshot,
                          HopperReservation reservation);
}
