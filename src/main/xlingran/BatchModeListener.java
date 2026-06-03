package xlingran;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import xlingran.core.HopperLaneListener;

public class BatchModeListener implements Listener {

    private final HopperKeys keys;
    private final PlayerGuiSession sessions;
    private final HopperLaneListener laneListener;

    public BatchModeListener(HopperKeys keys, PlayerGuiSession sessions, HopperLaneListener laneListener) {
        this.keys = keys;
        this.sessions = sessions;
        this.laneListener = laneListener;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (sessions.getInputMode(player.getUniqueId()) != PlayerGuiSession.InputMode.BATCH_APPLY) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);

        String templateName = sessions.getEditingTemplate(player.getUniqueId());
        if (templateName == null) {
            sessions.clearInput(player.getUniqueId());
            return;
        }

        if (block.getType() != Material.HOPPER) {
            player.sendMessage(ChatColor.RED + "方块类型错误");
            return;
        }

        if (HopperPdc.applyTemplate(block, keys, player.getUniqueId(), templateName)) {
            player.sendMessage(ChatColor.GREEN + "成功设置模板: " + ChatColor.AQUA + templateName);
            laneListener.scheduleEvaluate(block);
        }
    }
}
