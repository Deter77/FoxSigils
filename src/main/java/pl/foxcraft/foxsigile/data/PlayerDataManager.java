package pl.foxcraft.foxsigile.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {
    private final JavaPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerSigilData> cache = new ConcurrentHashMap<>();
    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        if (!folder.exists()) folder.mkdirs();
    }
    public PlayerSigilData get(UUID uuid) { return cache.computeIfAbsent(uuid, ignored -> new PlayerSigilData()); }
    public CompletableFuture<Void> load(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            File file = file(uuid);
            PlayerSigilData data = new PlayerSigilData();
            if (file.exists()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (int i = 0; i < 3; i++) {
                    data.setUnlocked(i, yaml.getBoolean("slots." + (i + 1) + ".unlocked", false));
                    data.setSigil(i, yaml.getString("slots." + (i + 1) + ".sigil", null));
                }
            }
            cache.put(uuid, data);
        });
    }
    public void saveAsync(UUID uuid) {
        PlayerSigilData snapshot = get(uuid).copy();
        CompletableFuture.runAsync(() -> saveSnapshot(uuid, snapshot));
    }
    public void saveAllNow() { cache.forEach((uuid, data) -> saveSnapshot(uuid, data.copy())); }
    private void saveSnapshot(UUID uuid, PlayerSigilData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int i = 0; i < 3; i++) {
            yaml.set("slots." + (i + 1) + ".unlocked", data.isUnlocked(i));
            yaml.set("slots." + (i + 1) + ".sigil", data.getSigil(i));
        }
        try { yaml.save(file(uuid)); } catch (IOException e) { plugin.getLogger().severe("Nie można zapisać danych gracza " + uuid + ": " + e.getMessage()); }
    }
    private File file(UUID uuid) { return new File(folder, uuid + ".yml"); }
}
