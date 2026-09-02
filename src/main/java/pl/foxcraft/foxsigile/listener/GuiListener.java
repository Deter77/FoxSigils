package pl.foxcraft.foxsigile.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.foxcraft.foxsigile.config.ConfigManager;
import pl.foxcraft.foxsigile.data.PlayerDataManager;
import pl.foxcraft.foxsigile.data.PlayerSigilData;
import pl.foxcraft.foxsigile.effect.EffectEngine;
import pl.foxcraft.foxsigile.gui.SigileGui;
import pl.foxcraft.foxsigile.item.Sigil;
import pl.foxcraft.foxsigile.item.SigilRegistry;

public final class GuiListener implements Listener {
    private final JavaPlugin plugin; private final SigileGui gui; private final SigilRegistry registry; private final PlayerDataManager data; private final EffectEngine effects; private final ConfigManager config;
    public GuiListener(JavaPlugin plugin, SigileGui gui, SigilRegistry registry, PlayerDataManager data, EffectEngine effects, ConfigManager config) { this.plugin = plugin; this.gui = gui; this.registry = registry; this.data = data; this.effects = effects; this.config = config; }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !gui.isSigilGui(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        PlayerSigilData playerData = data.get(player.getUniqueId());
        if (!gui.isEditable(event.getView().getTopInventory())) {
            boolean clickedInventorySigil = event.getClickedInventory().equals(event.getView().getBottomInventory()) && registry.find(event.getCurrentItem()).isPresent();
            int clickedSigilSlot = gui.logicalSlot(event.getRawSlot());
            boolean clickedActiveSigil = clickedSigilSlot >= 0 && playerData.getSigil(clickedSigilSlot) != null;
            if (clickedInventorySigil || clickedActiveSigil) denyReadonlyEdit(player);
            return;
        }
        if (event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            ItemStack current = event.getCurrentItem();
            Sigil sigil = registry.find(current).orElse(null); if (sigil == null) return;
            if (playerData.activeSigils().contains(sigil.id())) { player.sendMessage(config.message("duplicate_sigil")); return; }
            int free = gui.firstFreeUnlocked(player); if (free < 0) { player.sendMessage(config.message("no_free_slot")); return; }
            playerData.setSigil(free, sigil.id());
            if (current.getAmount() > 1) current.setAmount(current.getAmount() - 1); else event.getClickedInventory().setItem(event.getSlot(), null);
            finish(player, event);
            return;
        }
        int slot = gui.logicalSlot(event.getRawSlot());
        if (slot < 0 || playerData.getSigil(slot) == null) return;
        ItemStack item = registry.createItem(playerData.getSigil(slot), 1);
        if (player.getInventory().addItem(item).isEmpty()) {
            playerData.setSigil(slot, null);
            finish(player, event);
        } else player.sendMessage(config.message("inventory_full"));
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!gui.isSigilGui(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (!gui.isEditable(event.getView().getTopInventory()) && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            if (event.getNewItems().values().stream().anyMatch(item -> registry.find(item).isPresent())) denyReadonlyEdit((Player) event.getWhoClicked());
        }
    }
    private void denyReadonlyEdit(Player player) { player.closeInventory(); player.sendMessage(config.message("readonly_edit_denied")); }
    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;
        Player player = event.getPlayer(); ItemStack item = event.getItem(); if (!registry.isUnlockItem(item)) return;
        PlayerSigilData playerData = data.get(player.getUniqueId());
        for (int i = 0; i < 3; i++) if (!playerData.isUnlocked(i)) {
            playerData.setUnlocked(i, true); item.setAmount(item.getAmount() - 1); data.saveAsync(player.getUniqueId()); player.sendMessage(config.message("slot_unlocked")); event.setCancelled(true); return;
        }
        player.sendMessage(config.message("all_slots_unlocked")); event.setCancelled(true);
    }
    @EventHandler public void onDamage(EntityDamageEvent event) { if (event.getEntity() instanceof Player player) effects.applyDamageReductions(player, event); }
    @EventHandler public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            effects.applyDamageBonuses(attacker, event, event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK);
            if (event.getEntity() instanceof LivingEntity victim) procAttack(attacker, victim, event);
        }
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof LivingEntity damager) procDefense(victim, damager, event);
    }
    private void procAttack(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (effects.has(attacker, "water_wave") && effects.chance("water_wave", "chance_percent", 10)) effects.knockback(victim, attacker, effects.config("water_wave", "knockback_strength", 2.4));
        if (effects.has(attacker, "water_frost") && effects.chance("water_frost", "chance_percent", 50)) effects.temporaryAttribute(victim, effects.attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED"), "water_frost_slow", -effects.config("water_frost", "slow_percent", 20) / 100.0, effects.intConfig("water_frost", "duration_seconds", 2) * 20);
        if (effects.has(attacker, "dark_shade") && effects.chance("dark_shade", "chance_percent", 15)) attacker.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, effects.intConfig("dark_shade", "duration_seconds", 8) * 20, effects.intConfig("dark_shade", "amplifier", 0), true, false, true));
        if (effects.has(attacker, "dark_poison") && effects.chance("dark_poison", "chance_percent", 5)) victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, effects.intConfig("dark_poison", "duration_seconds", 7) * 20, effects.intConfig("dark_poison", "amplifier", 0), true, true, true));
        if (effects.has(attacker, "light_illumination")) victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, effects.intConfig("light_illumination", "glowing_seconds", 8) * 20, 0, true, true, true));
        effects.handleAttackCombos(attacker, victim, event);
    }
    private void procDefense(Player victim, LivingEntity damager, EntityDamageByEntityEvent event) {
        if (effects.has(victim, "water_healing") && effects.chance("water_healing", "chance_percent", 15)) Bukkit.getScheduler().runTask(plugin, () -> effects.heal(victim, effects.config("water_healing", "heal_health", 2)));
        if (effects.has(victim, "dark_blind") && damager instanceof Player player && effects.chance("dark_blind", "chance_percent", 6)) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, effects.intConfig("dark_blind", "duration_seconds", 10) * 20, effects.intConfig("dark_blind", "amplifier", 1), true, true, true));
        if (effects.has(victim, "light_guardian") && effects.chance("light_guardian", "chance_percent", 10)) victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, effects.intConfig("light_guardian", "duration_seconds", 6) * 20, effects.intConfig("light_guardian", "amplifier", 1), true, true, true));
        effects.handleDefenseCombos(victim, damager, event);
    }
    private void finish(Player player, InventoryClickEvent event) { data.saveAsync(player.getUniqueId()); effects.refreshPlayer(player); gui.render(player, event.getView().getTopInventory()); }
}
