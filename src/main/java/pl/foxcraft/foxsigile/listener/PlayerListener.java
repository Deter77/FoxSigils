package pl.foxcraft.foxsigile.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import pl.foxcraft.foxsigile.config.ConfigManager;
import pl.foxcraft.foxsigile.data.PlayerDataManager;
import pl.foxcraft.foxsigile.effect.EffectEngine;

import java.util.Set;

public final class PlayerListener implements Listener {
    private static final Set<PotionEffectType> NEGATIVE = Set.of(PotionEffectType.BLINDNESS, PotionEffectType.POISON, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.WITHER, PotionEffectType.HUNGER, PotionEffectType.NAUSEA, PotionEffectType.DARKNESS);
    private final JavaPlugin plugin; private final PlayerDataManager data; private final EffectEngine effects;
    public PlayerListener(JavaPlugin plugin, PlayerDataManager data, EffectEngine effects, ConfigManager config) { this.plugin = plugin; this.data = data; this.effects = effects; }
    @EventHandler public void onJoin(PlayerJoinEvent event) { data.load(event.getPlayer().getUniqueId()).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> effects.refreshPlayer(event.getPlayer()))); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { data.saveAsync(event.getPlayer().getUniqueId()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { Player killer = event.getEntity().getKiller(); if (killer != null && (effects.has(killer, "dark_leech") || "FIRE-LIGHT-LIGHT".equals(effects.combinationKey(killer)))) effects.heal(killer, effects.has(killer, "dark_leech") ? 1000 : 4); }
    @EventHandler public void onPotion(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null || !effects.has(player, "light_faith")) return;
        if (NEGATIVE.contains(event.getNewEffect().getType()) && effects.chance("light_faith", "chance_percent", 50)) event.setCancelled(true);
    }
    @EventHandler public void onRegain(EntityRegainHealthEvent event) { if (event.getEntity() instanceof Player player) effects.applyRegainBonus(player, event); }
    @EventHandler public void onTotem(EntityResurrectEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player player) || !effects.has(player, "light_hope")) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> effects.heal(player, effects.config("light_hope", "extra_health", 4)), 1L);
    }
}
