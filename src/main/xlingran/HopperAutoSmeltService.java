package xlingran;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动熔炼：每漏斗单 job，100 tick（5 秒）产出 1 个；每 8 tick 推进 8。
 */
public final class HopperAutoSmeltService {

    public static final int SMELT_DURATION_TICKS = 100;
    public static final int TICK_STEP = 8;

    private final Map<String, SmeltJob> jobs = new ConcurrentHashMap<>();

    /**
     * @return 本 tick 预留的漏斗槽位
     */
    public Set<Integer> tick(Block hopperBlock, HopperTemplate template, HopperKeys keys) {
        Set<Integer> reserved = new HashSet<>();
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER || template == null) {
            return reserved;
        }
        if (!template.isAutoSmeltEnabled() || template.getAutoSmeltOutputs().isEmpty()) {
            jobs.remove(laneKey(hopperBlock.getLocation()));
            return reserved;
        }
        if (!(hopperBlock.getState() instanceof Container container)) {
            return reserved;
        }
        Location loc = hopperBlock.getLocation();
        String key = laneKey(loc);
        Inventory inv = container.getInventory();

        SmeltJob job = jobs.get(key);
        if (job != null) {
            job.ticksRemaining -= TICK_STEP;
            reserved.add(job.sourceSlot);
            if (job.ticksRemaining <= 0) {
                ItemStack output = job.outputStack.clone();
                output.setAmount(1);
                HopperContainerUtil.refund(hopperBlock, inv, output);
                jobs.remove(key);
                HopperContainerUtil.syncContainer(hopperBlock);
            }
            return reserved;
        }

        if (jobs.containsKey(key)) {
            return reserved;
        }

        for (ItemStack outputProto : template.getAutoSmeltOutputs()) {
            HopperRecipeUtil.SmeltMapping mapping = HopperRecipeUtil.findSmeltMapping(outputProto);
            if (mapping == null) {
                continue;
            }
            RecipeChoice inputChoice = mapping.inputChoice();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack slot = inv.getItem(i);
                if (slot == null || slot.getType().isAir()) {
                    continue;
                }
                if (!HopperRecipeUtil.matchesChoice(inputChoice, slot)) {
                    continue;
                }
                if (!template.allows(slot, hopperBlock, keys)) {
                    continue;
                }
                int newAmount = slot.getAmount() - 1;
                if (newAmount <= 0) {
                    inv.setItem(i, null);
                } else {
                    ItemStack updated = slot.clone();
                    updated.setAmount(newAmount);
                    inv.setItem(i, updated);
                }
                ItemStack result = mapping.resultStack().clone();
                result.setAmount(1);
                jobs.put(key, new SmeltJob(i, result, SMELT_DURATION_TICKS));
                reserved.add(i);
                HopperContainerUtil.syncContainer(hopperBlock);
                return reserved;
            }
        }
        return reserved;
    }

    public void clear(Location loc) {
        jobs.remove(laneKey(loc));
    }

    private static String laneKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static final class SmeltJob {
        private final int sourceSlot;
        private final ItemStack outputStack;
        private int ticksRemaining;

        private SmeltJob(int sourceSlot, ItemStack outputStack, int ticksRemaining) {
            this.sourceSlot = sourceSlot;
            this.outputStack = outputStack;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
