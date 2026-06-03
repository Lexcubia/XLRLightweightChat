package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一 8 tick 漏斗管线：熔炼 → 合成 → 反向传输。
 */
public final class HopperTickService implements Listener {

    public static final long HOPPER_TICK_PERIOD = 8L;
    private static final int MAX_PER_TICK = 256;

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final HopperAutomationRegistry registry;
    private final HopperReservation reservation;
    private final HopperAutoSmeltService smeltService;
    private final HopperAutoCraftService craftService;

    public HopperTickService(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys,
                             HopperAutomationRegistry registry) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.keys = keys;
        this.registry = registry;
        this.reservation = new HopperReservation();
        this.smeltService = new HopperAutoSmeltService();
        this.craftService = new HopperAutoCraftService();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, HOPPER_TICK_PERIOD, HOPPER_TICK_PERIOD);
        Bukkit.getScheduler().runTask(plugin, () -> registry.indexWorlds(keys, templateManager));
    }

    public HopperAutomationRegistry getRegistry() {
        return registry;
    }

    public HopperReservation getReservation() {
        return reservation;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        registry.indexChunk(event.getChunk(), keys, templateManager);
    }

    private void tickAll() {
        List<Location> locations = registry.snapshot();
        int processed = 0;
        for (Location loc : locations) {
            if (++processed > MAX_PER_TICK) {
                plugin.getLogger().warning("[XLRHopper] 漏斗自动化队列积压，剩余延后处理");
                break;
            }
            if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            Block block = loc.getBlock();
            if (block.getType() != Material.HOPPER) {
                registry.setActive(loc, false);
                continue;
            }
            HopperTemplate template = HopperTemplateResolver.resolve(block, keys, templateManager);
            if (template == null) {
                registry.setActive(loc, false);
                smeltService.clear(loc);
                reservation.clear(loc);
                continue;
            }
            registry.syncHopper(block, keys, templateManager);

            Set<Integer> reserved = new HashSet<>();
            reserved.addAll(smeltService.tick(block, template, keys));
            reserved.addAll(craftService.tryCraft(block, template, keys));
            reservation.setReserved(loc, reserved);

            if (HopperBlockConfig.isReverse(block, keys)) {
                HopperTransferReverse.transferStep(block, template, keys, reservation);
            }
        }
    }
}
