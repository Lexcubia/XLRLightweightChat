package xlingran;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 称号仓库 GUI 持有者，用于识别界面（不依赖窗口标题语言）。
 */
public final class TitleGuiHolder implements InventoryHolder {

    private final int page;
    private Inventory inventory;

    public TitleGuiHolder(int page) {
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isTitleGui(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof TitleGuiHolder;
    }
}
