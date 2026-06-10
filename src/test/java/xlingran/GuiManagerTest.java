package xlingran;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiManagerTest {

    @Test
    void rawSlotsOutsideTopInventoryAreIgnored() {
        assertFalse(GuiManager.isTopInventorySlot(54, 54));
        assertFalse(GuiManager.isTopInventorySlot(80, 54));
        assertFalse(GuiManager.isTopInventorySlot(-1, 54));
    }

    @Test
    void rawSlotsInsideTopInventoryAreHandled() {
        assertTrue(GuiManager.isTopInventorySlot(0, 54));
        assertTrue(GuiManager.isTopInventorySlot(10, 54));
        assertTrue(GuiManager.isTopInventorySlot(53, 54));
    }

    @Test
    void titleSlotsMatchOnlyInteriorTitleGrid() {
        assertTrue(GuiManager.isTitleSlot(10));
        assertTrue(GuiManager.isTitleSlot(16));
        assertTrue(GuiManager.isTitleSlot(37));
        assertFalse(GuiManager.isTitleSlot(9));
        assertFalse(GuiManager.isTitleSlot(17));
        assertFalse(GuiManager.isTitleSlot(49));
    }
}
