package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;

/**
 * 反向吸取：从下方向上搬运；取消原版从上吸入与向下输出，并由定时任务持续尝试反向传输。
 */
public class HopperReverseHandler implements Listener {

    /** 与原版漏斗传输间隔一致（8 game ticks）。 */
    private static final long HOPPER_TICK_PERIOD = 8L;

    private final JavaPlugin plugin;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final HopperReverseRegistry registry = new HopperReverseRegistry();

    public HopperReverseHandler(JavaPlugin plugin, HopperTemplateManager templateManager, HopperKeys keys) {
        this.plugin = plugin;
        this.templateManager = templateManager;
        this.keys = keys;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickReverseHoppers, HOPPER_TICK_PERIOD, HOPPER_TICK_PERIOD);
        Bukkit.getScheduler().runTask(plugin, this::indexLoadedReverseHoppers);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        indexChunk(event.getChunk());
    }

    private void indexLoadedReverseHoppers() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                indexChunk(chunk);
            }
        }
    }

    private void indexChunk(Chunk chunk) {
        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState.getType() != Material.HOPPER) {
                continue;
            }
            Block block = blockState.getBlock();
            if (HopperBlockConfig.isReverse(block, keys)) {
                registry.setActive(block.getLocation(), true);
            }
        }
    }

    public HopperReverseRegistry getRegistry() {
        return registry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block destHopper = getHopperBlock(event.getDestination());
        Block srcHopper = getHopperBlock(event.getSource());
        boolean cancelled = false;

        if (destHopper != null) {
            registry.syncFromBlock(destHopper, keys);
            if (HopperBlockConfig.isReverse(destHopper, keys)
                    && isInventoryAboveHopper(destHopper, event.getSource())) {
                event.setCancelled(true);
                cancelled = true;
            }
        }
        if (srcHopper != null) {
            registry.syncFromBlock(srcHopper, keys);
            if (HopperBlockConfig.isReverse(srcHopper, keys)
                    && isInventoryBelowHopper(srcHopper, event.getDestination())) {
                event.setCancelled(true);
                cancelled = true;
            }
        }

        if (cancelled) {
            Block reverseBlock = destHopper != null && HopperBlockConfig.isReverse(destHopper, keys) ? destHopper
                    : srcHopper;
            if (reverseBlock != null) {
                Bukkit.getScheduler().runTask(plugin, () -> attemptReverseTransfer(reverseBlock));
            }
        }
    }

    private void tickReverseHoppers() {
        List<Location> locations = registry.snapshot();
        for (Location loc : locations) {
            if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            Block block = loc.getBlock();
            if (block.getType() != Material.HOPPER) {
                registry.setActive(loc, false);
                continue;
            }
            if (!HopperBlockConfig.isReverse(block, keys)) {
                registry.setActive(loc, false);
                continue;
            }
            attemptReverseTransfer(block);
        }
    }

    private void attemptReverseTransfer(Block hopperBlock) {
        if (hopperBlock.getType() != Material.HOPPER) {
            return;
        }
        if (!HopperBlockConfig.isReverse(hopperBlock, keys)) {
            return;
        }
        HopperTemplate template = HopperTemplateResolver.resolve(hopperBlock, keys, templateManager);
        if (template == null) {
            return;
        }
        BlockState state = hopperBlock.getState();
        if (!(state instanceof Container hopperContainer)) {
            return;
        }
        Inventory hopperInv = hopperContainer.getInventory();

        Block belowBlock = hopperBlock.getRelative(BlockFace.DOWN);
        Inventory belowInv = getNeighborInventory(belowBlock);
        if (belowInv != null) {
            pullOne(belowInv, belowBlock, hopperInv, hopperBlock, template);
        }

        Block aboveBlock = hopperBlock.getRelative(BlockFace.UP);
        Inventory aboveInv = getNeighborInventory(aboveBlock);
        if (aboveInv != null) {
            pushOne(hopperInv, hopperBlock, aboveInv, aboveBlock, template);
        }

        syncContainer(hopperBlock);
    }

    private void pullOne(Inventory from, Block fromBlock, Inventory hopperInv, Block hopperBlock,
                         HopperTemplate template) {
        for (int i = 0; i < from.getSize(); i++) {
            ItemStack slot = from.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            if (!template.allows(slot, hopperBlock, keys)) {
                continue;
            }
            ItemStack one = slot.clone();
            one.setAmount(1);
            HashMap<Integer, ItemStack> leftover = hopperInv.addItem(one);
            if (leftover.isEmpty()) {
                int newAmount = slot.getAmount() - 1;
                if (newAmount <= 0) {
                    from.setItem(i, null);
                } else {
                    ItemStack updated = slot.clone();
                    updated.setAmount(newAmount);
                    from.setItem(i, updated);
                }
                syncContainer(fromBlock);
                syncContainer(hopperBlock);
                return;
            }
        }
    }

    private void pushOne(Inventory hopperInv, Block hopperBlock, Inventory to, Block toBlock,
                         HopperTemplate template) {
        for (int i = 0; i < hopperInv.getSize(); i++) {
            ItemStack slot = hopperInv.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            if (!template.allows(slot, hopperBlock, keys)) {
                continue;
            }
            ItemStack one = slot.clone();
            one.setAmount(1);
            HashMap<Integer, ItemStack> leftover = to.addItem(one);
            if (leftover.isEmpty()) {
                int newAmount = slot.getAmount() - 1;
                if (newAmount <= 0) {
                    hopperInv.setItem(i, null);
                } else {
                    ItemStack updated = slot.clone();
                    updated.setAmount(newAmount);
                    hopperInv.setItem(i, updated);
                }
                syncContainer(hopperBlock);
                syncContainer(toBlock);
                return;
            }
        }
    }

    private static void syncContainer(Block block) {
        if (block == null) {
            return;
        }
        BlockState blockState = block.getState();
        if (blockState instanceof Container) {
            blockState.update(true, false);
        }
    }

    private static Inventory getNeighborInventory(Block block) {
        if (block == null) {
            return null;
        }
        BlockState blockState = block.getState();
        if (blockState instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    private static Block getHopperBlock(Inventory inventory) {
        if (inventory == null || inventory.getType() != InventoryType.HOPPER) {
            return null;
        }
        if (inventory.getHolder() instanceof BlockState blockState) {
            return blockState.getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }

    private static boolean isInventoryAboveHopper(Block hopper, Inventory other) {
        Block otherBlock = getInventoryBlock(other);
        return otherBlock != null && otherBlock.equals(hopper.getRelative(BlockFace.UP));
    }

    private static boolean isInventoryBelowHopper(Block hopper, Inventory other) {
        Block otherBlock = getInventoryBlock(other);
        return otherBlock != null && otherBlock.equals(hopper.getRelative(BlockFace.DOWN));
    }

    private static Block getInventoryBlock(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        if (inventory.getHolder() instanceof BlockState blockState) {
            return blockState.getBlock();
        }
        if (inventory.getLocation() != null) {
            return inventory.getLocation().getBlock();
        }
        return null;
    }
}
