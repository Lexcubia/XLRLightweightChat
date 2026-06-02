package xlingran.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class XlrGuiHolder implements InventoryHolder {

    private final GuiType type;
    private Inventory inventory;

    public XlrGuiHolder(GuiType type) {
        this.type = type;
    }

    public GuiType getType() {
        return type;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isPluginGui(Inventory inventory, GuiType type) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof XlrGuiHolder xlr && xlr.getType() == type;
    }

    public static XlrGuiHolder from(Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof XlrGuiHolder holder) {
            return holder;
        }
        return null;
    }
}
