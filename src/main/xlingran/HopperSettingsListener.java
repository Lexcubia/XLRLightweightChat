package xlingran;

import org.bukkit.Material;
import xlingran.gui.MessageConfig;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class HopperSettingsListener implements Listener {

    private final Gui gui;
    private final HopperTemplateManager templateManager;
    private final HopperKeys keys;
    private final MessageConfig messageConfig;

    public HopperSettingsListener(Gui gui, HopperTemplateManager templateManager, HopperKeys keys,
                                  MessageConfig messageConfig) {
        this.gui = gui;
        this.templateManager = templateManager;
        this.keys = keys;
        this.messageConfig = messageConfig;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
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
            player.sendMessage(messageConfig.message("no-template"));
            return;
        }
        event.setCancelled(true);
        gui.openHopperSettings(player, block);
    }
}
