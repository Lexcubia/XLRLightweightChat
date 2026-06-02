package xlingran;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

final class GuiClickGuard {

    static final long COOLDOWN_MS = 200L;

    private GuiClickGuard() {
    }

    static boolean shouldIgnoreClick(PlayerGuiSession session, Player player) {
        return !session.tryClickCooldown(player.getUniqueId(), COOLDOWN_MS);
    }

    static void blockItemManipulation(InventoryClickEvent event) {
        event.setCancelled(true);
        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.SWAP_WITH_CURSOR
                || action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT) {
            event.getWhoClicked().setItemOnCursor(null);
        }
    }

    static void cancelDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }
}
