package xlingran.gui;

import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class XlrGuiHolder implements InventoryHolder {

    private final GuiType type;
    private final String templateName;
    private final String hopperWorld;
    private final int hopperX;
    private final int hopperY;
    private final int hopperZ;
    private Inventory inventory;

    public XlrGuiHolder(GuiType type) {
        this(type, null);
    }

    public XlrGuiHolder(GuiType type, String templateName) {
        this(type, templateName, null, 0, 0, 0);
    }

    public XlrGuiHolder(GuiType type, World world, int x, int y, int z) {
        this(type, null, world != null ? world.getName() : null, x, y, z);
    }

    private XlrGuiHolder(GuiType type, String templateName, String hopperWorld, int hopperX, int hopperY, int hopperZ) {
        this.type = type;
        this.templateName = templateName;
        this.hopperWorld = hopperWorld;
        this.hopperX = hopperX;
        this.hopperY = hopperY;
        this.hopperZ = hopperZ;
    }

    public GuiType getType() {
        return type;
    }

    /** 打开该界面时绑定的模板名；列表等界面为 null。 */
    public String getTemplateName() {
        return templateName;
    }

    public String getHopperWorld() {
        return hopperWorld;
    }

    public int getHopperX() {
        return hopperX;
    }

    public int getHopperY() {
        return hopperY;
    }

    public int getHopperZ() {
        return hopperZ;
    }

    public boolean hasHopperLocation() {
        return hopperWorld != null && !hopperWorld.isEmpty();
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static XlrGuiHolder from(Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof XlrGuiHolder holder) {
            return holder;
        }
        return null;
    }
}
