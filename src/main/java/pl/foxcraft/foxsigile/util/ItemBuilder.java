package pl.foxcraft.foxsigile.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemBuilder {
    private ItemBuilder() {}
    public static ItemStack fromSection(ConfigurationSection section, int amount) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null || material == Material.AIR) return new ItemStack(Material.AIR);
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(section.getString("name", "")));
            List<String> lore = section.getStringList("lore").stream().map(Text::color).toList();
            meta.setLore(lore);
            int cmd = section.getInt("custom_model_data", 0);
            if (cmd > 0) meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }
}
