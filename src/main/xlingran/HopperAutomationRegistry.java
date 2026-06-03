package xlingran;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 需要 8 tick 自动化处理的漏斗（反向 / 自动合成 / 自动熔炼）。 */
public final class HopperAutomationRegistry {

    private final Map<String, Location> active = new ConcurrentHashMap<>();

    void setActive(Location location, boolean enabled) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        String key = laneKey(location);
        if (enabled) {
            active.put(key, location.clone());
        } else {
            active.remove(key);
        }
    }

    void syncHopper(Block block, HopperKeys keys, HopperTemplateManager templateManager) {
        if (block == null || block.getType() != Material.HOPPER) {
            return;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(block, keys, templateManager);
        if (template == null) {
            setActive(block.getLocation(), false);
            return;
        }
        boolean needs = HopperBlockConfig.isReverse(block, keys)
                || template.isAutoCraftEnabled()
                || template.isAutoSmeltEnabled();
        setActive(block.getLocation(), needs);
    }

    void indexChunk(Chunk chunk, HopperKeys keys, HopperTemplateManager templateManager) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() == Material.HOPPER) {
                syncHopper(state.getBlock(), keys, templateManager);
            }
        }
    }

    void indexWorlds(HopperKeys keys, HopperTemplateManager templateManager) {
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                indexChunk(chunk, keys, templateManager);
            }
        }
    }

    List<Location> snapshot() {
        return new ArrayList<>(active.values());
    }

    private static String laneKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
