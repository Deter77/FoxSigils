package pl.foxcraft.foxsigile.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.foxcraft.foxsigile.config.ConfigManager;
import pl.foxcraft.foxsigile.util.ItemBuilder;

import java.util.*;

public final class SigilRegistry {
    private final ConfigManager config;
    private final Map<String, Sigil> byId = new LinkedHashMap<>();
    public SigilRegistry(ConfigManager config) { this.config = config; load(); }
    private void load() {
        ConfigurationSection root = config.section("sigils");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            Material material = Material.matchMaterial(section.getString("material", "AIR"));
            SigilType type = SigilType.valueOf(section.getString("type", "FIRE").toUpperCase(Locale.ROOT));
            byId.put(id, new Sigil(id, type, material, section.getInt("custom_model_data"), section.getConfigurationSection("config")));
        }
    }
    public Optional<Sigil> find(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return Optional.empty();
        int cmd = meta.getCustomModelData();
        return byId.values().stream().filter(s -> s.material() == item.getType() && s.customModelData() == cmd).findFirst();
    }
    public Optional<Sigil> get(String id) { return Optional.ofNullable(byId.get(id)); }
    public Collection<Sigil> all() { return Collections.unmodifiableCollection(byId.values()); }
    public ItemStack createItem(String id, int amount) {
        ConfigurationSection section = config.section("sigils." + id);
        return section == null ? new ItemStack(Material.AIR) : ItemBuilder.fromSection(section, amount);
    }
    public boolean isUnlockItem(ItemStack item) {
        ConfigurationSection section = config.section("unlock_item");
        if (section == null || item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Material material = Material.matchMaterial(section.getString("material", "IRON_INGOT"));
        ItemMeta meta = item.getItemMeta();
        return item.getType() == material && meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == section.getInt("custom_model_data");
    }
}
