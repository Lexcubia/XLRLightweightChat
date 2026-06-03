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

public class HopperSettingsListener implements Listener {

    private static final String NO_TEMPLATE_MESSAGE = ChatColor.RED + "玩家当前漏斗没有模板";

    private final Gui gui;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;

    public HopperSettingsListener(Gui gui, HopperTemplateManager templateManager, HopperKeys keys) {
        this.gui = gui;
        this.templateManager = templateManager;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.HOPPER) {
            return;
        }
        if (!HopperTemplateResolver.hasValidTemplate(block, keys, templateManager)) {
            player.sendMessage(NO_TEMPLATE_MESSAGE);
            return;
        }
        event.setCancelled(true);
        gui.openHopperSettings(player, block);
    }
}
