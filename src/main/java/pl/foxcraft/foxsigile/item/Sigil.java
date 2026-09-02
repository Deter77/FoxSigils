package pl.foxcraft.foxsigile.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record Sigil(String id, SigilType type, Material material, int customModelData, ConfigurationSection config) {}
