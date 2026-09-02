package pl.foxcraft.foxsigile.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SigileHolder implements InventoryHolder {
    private final boolean editable;

    public SigileHolder(boolean editable) {
        this.editable = editable;
    }

    public boolean isEditable() {
        return editable;
    }

    @Override public Inventory getInventory() { return null; }
}
