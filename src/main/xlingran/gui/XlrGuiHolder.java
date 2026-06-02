package xlingran.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class XlrGuiHolder implements InventoryHolder {

    private final GuiType type;
    private final String templateName;
    private Inventory inventory;

    public XlrGuiHolder(GuiType type) {
        this(type, null);
    }

    public XlrGuiHolder(GuiType type, String templateName) {
        this.type = type;
        this.templateName = templateName;
    }

    public GuiType getType() {
        return type;
    }

    /** 打开该界面时绑定的模板名；列表等界面为 null。 */
    public String getTemplateName() {
        return templateName;
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
