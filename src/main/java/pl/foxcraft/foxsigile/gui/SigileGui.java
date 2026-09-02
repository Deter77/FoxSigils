package pl.foxcraft.foxsigile.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.foxcraft.foxsigile.config.ConfigManager;
import pl.foxcraft.foxsigile.data.PlayerDataManager;
import pl.foxcraft.foxsigile.data.PlayerSigilData;
import pl.foxcraft.foxsigile.effect.EffectEngine;
import pl.foxcraft.foxsigile.item.SigilRegistry;
import pl.foxcraft.foxsigile.util.ItemBuilder;
import pl.foxcraft.foxsigile.util.Text;

import java.util.ArrayList;
import java.util.List;

public final class SigileGui {
    private final ConfigManager config;
    private final SigilRegistry registry;
    private final PlayerDataManager dataManager;
    private final EffectEngine effectEngine;

    public SigileGui(ConfigManager config, SigilRegistry registry, PlayerDataManager dataManager, EffectEngine effectEngine) {
        this.config = config; this.registry = registry; this.dataManager = dataManager; this.effectEngine = effectEngine;
    }

    public void open(Player player) {
        open(player, false);
    }

    public void openEditable(Player player) {
        open(player, true);
    }

    private void open(Player player, boolean editable) {
        Inventory inv = Bukkit.createInventory(new SigileHolder(editable), config.guiSize(), config.guiTitle());
        render(player, inv);
        player.openInventory(inv);
    }

    public void render(Player player, Inventory inv) {
        PlayerSigilData data = dataManager.get(player.getUniqueId());
        for (int i = 0; i < 3; i++) {
            int slot = config.sigilSlot(i + 1);
            if (!data.isUnlocked(i)) inv.setItem(slot, ItemBuilder.fromSection(config.section("gui.items.locked_slot"), 1));
            else if (data.getSigil(i) != null) inv.setItem(slot, registry.createItem(data.getSigil(i), 1));
            else inv.setItem(slot, null);
        }
        inv.setItem(config.infoSlot(), infoItem(player, data));
    }

    public boolean isSigilGui(Inventory inv) { return inv != null && inv.getHolder() instanceof SigileHolder; }
    public boolean isEditable(Inventory inv) { return inv != null && inv.getHolder() instanceof SigileHolder holder && holder.isEditable(); }
    public int logicalSlot(int rawSlot) { for (int i = 0; i < 3; i++) if (rawSlot == config.sigilSlot(i + 1)) return i; return -1; }
    public int firstFreeUnlocked(Player player) { PlayerSigilData data = dataManager.get(player.getUniqueId()); for (int i = 0; i < 3; i++) if (data.isUnlocked(i) && data.getSigil(i) == null) return i; return -1; }

    private ItemStack infoItem(Player player, PlayerSigilData data) {
        ItemStack item = ItemBuilder.fromSection(config.section("gui.items.info"), 1);
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        String comboName = effectEngine.combinationLabel(player);
        String comboDesc = effectEngine.combinationDescription(player);
        if (comboName == null) {
            lore.add(Text.color("&7Brak Kombinacji"));
        } else {
            lore.add(comboDesc);
            lore.add(comboName);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
