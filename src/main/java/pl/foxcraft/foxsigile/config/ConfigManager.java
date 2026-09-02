package pl.foxcraft.foxsigile.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import pl.foxcraft.foxsigile.util.Text;

public final class ConfigManager {
    private final JavaPlugin plugin;
    public ConfigManager(JavaPlugin plugin) { this.plugin = plugin; }
    public JavaPlugin plugin() { return plugin; }
    public int guiSize() { return plugin.getConfig().getInt("gui.size", 27); }
    public String guiTitle() { return Text.color(plugin.getConfig().getString("gui.title", "Sigile")); }
    public int sigilSlot(int index) { return plugin.getConfig().getInt("gui.slots.sigil" + index, 11 + index); }
    public int infoSlot() { return plugin.getConfig().getInt("gui.slots.info", 22); }
    public ConfigurationSection section(String path) { return plugin.getConfig().getConfigurationSection(path); }
    public String message(String key) { return Text.color(plugin.getConfig().getString("messages." + key, "&cBrak wiadomości: " + key)); }
}
