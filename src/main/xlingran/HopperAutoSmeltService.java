package xlingran;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动熔炼：每漏斗单 job，100 tick（5 秒）产出 1 个；每 8 tick 推进 8。
 */
public final class HopperAutoSmeltService {

    public static final int TICK_STEP = 8;

    private final XLRHopperConfig pluginConfig;
    private final Map<String, SmeltJob> jobs = new ConcurrentHashMap<>();

    public HopperAutoSmeltService(XLRHopperConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    private int smeltDurationTicks() {
        return pluginConfig != null ? pluginConfig.getSmeltTick() : 100;
    }

    /**
     * @return 本 tick 预留的漏斗槽位
     */
    public Set<Integer> tick(Block hopperBlock, HopperTemplate template, HopperKeys keys) {
        return processSmelt(hopperBlock, template, keys, true);
    }

    /**
     * 入料后即时尝试启动熔炼，不推进已有 job 的计时。
     */
    public Set<Integer> tryStartSmelt(Block hopperBlock, HopperTemplate template, HopperKeys keys) {
        return processSmelt(hopperBlock, template, keys, false);
    }

    public Set<Integer> getActiveReservedSlots(Location loc) {
        Set<Integer> reserved = new HashSet<>();
        if (loc == null || loc.getWorld() == null) {
            return reserved;
        }
        SmeltJob job = jobs.get(laneKey(loc));
        if (job != null) {
            reserved.add(job.sourceSlot);
        }
        return reserved;
    }

    public boolean shouldHoldOutbound(Block hopperBlock, HopperTemplate template, HopperKeys keys, ItemStack moving) {
        if (hopperBlock == null || template == null || moving == null || moving.getType().isAir()) {
            return false;
        }
        if (!template.isAutoSmeltEnabled() || template.getAutoSmeltOutputs().isEmpty()) {
            return false;
        }
        if (!(hopperBlock.getState() instanceof Container container)) {
            return false;
        }
        Location loc = hopperBlock.getLocation();
        Inventory inv = container.getInventory();
        if (!isSmeltPipelineActive(inv, hopperBlock, template, keys, loc)) {
            return false;
        }
        if (matchesAnySmeltOutputPrototype(moving, template)) {
            return true;
        }
        if (!template.allows(moving, hopperBlock, keys)) {
            return false;
        }
        return matchesAnySmeltInput(template, moving);
    }

    private static boolean matchesAnySmeltOutputPrototype(ItemStack moving, HopperTemplate template) {
        for (ItemStack outputProto : template.getAutoSmeltOutputs()) {
            if (HopperRecipeUtil.matchesPrototype(moving, outputProto)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSmeltPipelineActive(Inventory inv, Block hopperBlock, HopperTemplate template,
                                          HopperKeys keys, Location loc) {
        if (hasJob(loc)) {
            return true;
        }
        for (ItemStack outputProto : template.getAutoSmeltOutputs()) {
            for (HopperRecipeUtil.SmeltMapping mapping : HopperRecipeUtil.findAllSmeltMappings(outputProto)) {
                RecipeChoice inputChoice = mapping.inputChoice();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack slot = inv.getItem(i);
                    if (slot == null || slot.getType().isAir()) {
                        continue;
                    }
                    if (!HopperRecipeUtil.matchesChoice(inputChoice, slot)) {
                        continue;
                    }
                    if (template.allows(slot, hopperBlock, keys)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesAnySmeltInput(HopperTemplate template, ItemStack stack) {
        for (ItemStack outputProto : template.getAutoSmeltOutputs()) {
            for (HopperRecipeUtil.SmeltMapping mapping : HopperRecipeUtil.findAllSmeltMappings(outputProto)) {
                if (HopperRecipeUtil.matchesChoice(mapping.inputChoice(), stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<Integer> processSmelt(Block hopperBlock, HopperTemplate template, HopperKeys keys,
                                      boolean advanceTimer) {
        Set<Integer> reserved = new HashSet<>();
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER || template == null) {
            return reserved;
        }
        if (pluginConfig != null && !pluginConfig.isAutoSmeltEnabled()) {
            jobs.remove(laneKey(hopperBlock.getLocation()));
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
            reserved.add(job.sourceSlot);
            if (advanceTimer) {
                job.ticksRemaining -= TICK_STEP;
                if (job.ticksRemaining <= 0) {
                    ItemStack output = job.outputStack.clone();
                    output.setAmount(1);
                    HopperContainerUtil.deliverDownstream(hopperBlock, output);
                    jobs.remove(key);
                    HopperContainerUtil.syncContainer(hopperBlock);
                }
            }
            return reserved;
        }

        for (ItemStack outputProto : template.getAutoSmeltOutputs()) {
            List<HopperRecipeUtil.SmeltMapping> mappings = HopperRecipeUtil.findAllSmeltMappings(outputProto);
            for (HopperRecipeUtil.SmeltMapping mapping : mappings) {
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
                    jobs.put(key, new SmeltJob(i, result, smeltDurationTicks()));
                    reserved.add(i);
                    HopperContainerUtil.syncContainer(hopperBlock);
                    return reserved;
                }
            }
        }
        return reserved;
    }

    public void clear(Location loc) {
        jobs.remove(laneKey(loc));
    }

    public boolean hasJob(Location loc) {
        return loc != null && loc.getWorld() != null && jobs.containsKey(laneKey(loc));
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
