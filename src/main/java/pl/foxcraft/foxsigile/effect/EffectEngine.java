package pl.foxcraft.foxsigile.effect;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import pl.foxcraft.foxsigile.data.PlayerDataManager;
import pl.foxcraft.foxsigile.item.Sigil;
import pl.foxcraft.foxsigile.item.SigilRegistry;
import pl.foxcraft.foxsigile.item.SigilType;
import pl.foxcraft.foxsigile.util.Text;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class EffectEngine {
    private static final List<SigilType> TYPE_ORDER = List.of(SigilType.FIRE, SigilType.WATER, SigilType.EARTH, SigilType.WIND, SigilType.DARKNESS, SigilType.LIGHT);
    private static final Set<PotionEffectType> NEGATIVE = Set.of(PotionEffectType.BLINDNESS, PotionEffectType.POISON, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.WITHER, PotionEffectType.HUNGER, PotionEffectType.NAUSEA, PotionEffectType.DARKNESS);

    private final JavaPlugin plugin;
    private final SigilRegistry registry;
    private final PlayerDataManager data;
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> lastRegeneration = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private BukkitTask task;

    public EffectEngine(JavaPlugin plugin, SigilRegistry registry, PlayerDataManager data) {
        this.plugin = plugin;
        this.registry = registry;
        this.data = data;
    }

    public void startTasks() {
        long interval = Math.max(20L, plugin.getConfig().getLong("settings.local_check_interval_ticks", 100L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::tick), 20L, interval);
    }

    public void clearAll() {
        if (task != null) task.cancel();
        Bukkit.getOnlinePlayers().forEach(this::clearAttributes);
    }

    public void refreshPlayer(Player player) {
        clearAttributes(player);
        applyAttributes(player);
    }

    public boolean has(Player player, String sigil) {
        return data.get(player.getUniqueId()).activeSigils().contains(sigil);
    }

    public String combinationKey(Player player) {
        Map<SigilType, Integer> counts = new EnumMap<>(SigilType.class);
        for (String id : data.get(player.getUniqueId()).activeSigils()) {
            registry.get(id).ifPresent(sigil -> counts.merge(sigil.type(), 1, Integer::sum));
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total < 2) return null;
        List<String> parts = new ArrayList<>();
        for (SigilType type : TYPE_ORDER) {
            for (int i = 0; i < counts.getOrDefault(type, 0); i++) parts.add(type.name());
        }
        return String.join("-", parts);
    }

    public String combinationLabel(Player player) {
        String key = combinationKey(player);
        if (key == null) return null;
        Map<String, String> names = Map.of(
                "FIRE", Text.color("&x&B&5&2&7&1&3O&x&B&F&3&6&1&Ag&x&C&A&4&4&2&2i&x&D&4&5&3&2&9e&x&D&E&6&1&3&0ń"),
                "WATER", Text.color("&x&2&3&7&8&B&0W&x&1&7&9&3&C&Ao&x&0&C&A&D&E&5d&x&0&0&C&8&F&Fa"),
                "EARTH", Text.color("&x&5&8&3&9&1&6Z&x&6&1&3&D&1&6i&x&6&9&4&2&1&6e&x&7&2&4&6&1&5m&x&7&A&4&B&1&5i&x&8&3&4&F&1&5a"),
                "WIND", Text.color("&x&7&0&8&D&8&6W&x&8&9&A&A&A&3i&x&A&2&C&6&B&Fa&x&B&B&E&3&D&Ct&x&D&4&F&F&F&8r"),
                "DARKNESS", Text.color("&x&1&5&0&3&1&8C&x&1&8&0&6&1&Ci&x&1&C&0&9&2&0e&x&1&F&0&C&2&4m&x&2&3&0&F&2&8n&x&2&6&1&2&2&Co&x&2&A&1&5&3&0ś&x&2&D&1&8&3&4ć"),
                "LIGHT", Text.color("&x&F&F&D&7&0&0Ś&x&F&F&D&B&1&Aw&x&F&F&E&0&3&3i&x&F&F&E&4&4&Da&x&F&F&E&9&6&7t&x&F&F&E&D&8&0ł&x&F&F&F&1&9&Ao&x&F&F&F&6&B&3ś&x&F&F&F&A&C&Dć"));
        StringJoiner joiner = new StringJoiner(Text.color(" &7- &r"));
        for (String part : key.split("-")) joiner.add(names.getOrDefault(part, part));
        return joiner.toString();
    }

    public String combinationDescription(Player player) {
        String key = combinationKey(player);
        if (key == null) return null;
        return Text.color(COMBO_DESCRIPTIONS.getOrDefault(key, "&cBrak opisu kombinacji: " + key));
    }

    public double config(String id, String key, double def) {
        return registry.get(id).map(Sigil::config).map(section -> section == null ? def : section.getDouble(key, def)).orElse(def);
    }

    public int intConfig(String id, String key, int def) {
        return (int) Math.round(config(id, key, def));
    }

    public boolean chance(String id, String key, double def) {
        return chancePercent(config(id, key, def));
    }

    public void applyDamageBonuses(Player attacker, EntityDamageEvent event, boolean melee) {
        double add = 0;
        if (melee && has(attacker, "fire_flame")) add += config("fire_flame", "damage_percent", 10);
        if (has(attacker, "fire_heat") && attacker.getFireTicks() > 0) add += config("fire_heat", "damage_percent", 18);
        if (has(attacker, "fire_warrior") && attacker.getHealth() <= config("fire_warrior", "below_health", 6)) add += config("fire_warrior", "damage_percent", 14);
        if (has(attacker, "fire_hell") && attacker.getWorld().getEnvironment() == World.Environment.NETHER) add += config("fire_hell", "damage_percent", 23);
        if (has(attacker, "dark_shadow") && isNight(attacker.getWorld())) add += config("dark_shadow", "damage_percent", 12);
        if (has(attacker, "light_radiance")) add += config("light_radiance", "self_damage_percent", 10);

        String combo = combinationKey(attacker);
        if ("FIRE-FIRE".equals(combo)) add += 8;
        if ("FIRE-DARKNESS".equals(combo) && attacker.getWorld().getEnvironment() == World.Environment.NETHER) add += 10;
        if ("FIRE-FIRE-FIRE".equals(combo)) add += 18;
        if ("LIGHT-LIGHT-LIGHT".equals(combo) && isNight(attacker.getWorld())) add -= 16;
        if ("FIRE-FIRE-DARKNESS".equals(combo) && event instanceof EntityDamageByEntityEvent byEntity && byEntity.getEntity() instanceof LivingEntity target && target.getHealth() <= maxHealth(target) / 2.0) add += 15;
        if ("FIRE-FIRE-LIGHT".equals(combo) && attacker.getWorld().isClearWeather()) add += 11;
        if ("FIRE-DARKNESS-LIGHT".equals(combo) && attacker.getHealth() <= maxHealth(attacker) / 2.0) add += 12;
        if ("WATER-WATER-DARKNESS".equals(combo) && attacker.getEyeLocation().getBlock().isLiquid()) add += 23;
        if ("WATER-DARKNESS-LIGHT".equals(combo)) add += 7;
        if ("DARKNESS-DARKNESS-LIGHT".equals(combo) && isNight(attacker.getWorld())) add += 12;
        if ("WATER-LIGHT-LIGHT".equals(combo) && event instanceof EntityDamageByEntityEvent byEntity && byEntity.getEntity() instanceof LivingEntity target && isUndead(target)) add += 20;
        event.setDamage(event.getDamage() * Math.max(0, 1 + add / 100.0));
    }

    public void applyDamageReductions(Player victim, EntityDamageEvent event) {
        if (has(victim, "wind_dodge") && event instanceof EntityDamageByEntityEvent && chance("wind_dodge", "chance_percent", 10)) {
            event.setCancelled(true);
            return;
        }
        String combo = combinationKey(victim);
        if (isProjectileDamage(event) && (("WATER-WIND".equals(combo) && chancePercent(20)) || ("WATER-WIND-WIND".equals(combo) && chancePercent(23)))) {
            event.setCancelled(true);
            return;
        }
        if ("EARTH-WIND-WIND".equals(combo) && cooldownChance(victim, "EARTH-WIND-WIND", 15, 22)) {
            event.setCancelled(true);
            return;
        }

        double reduction = 0;
        if (has(victim, "fire_ash") && isFire(event.getCause())) reduction += config("fire_ash", "reduction_percent", 55);
        if (has(victim, "earth_stone")) reduction += config("earth_stone", "reduction_percent", 8);
        if (has(victim, "earth_soil") && Set.of(Material.GRASS_BLOCK, Material.DIRT).contains(victim.getLocation().subtract(0, 1, 0).getBlock().getType())) reduction += config("earth_soil", "reduction_percent", 15);
        if (has(victim, "earth_boulder") && isStandingStill(victim)) reduction += config("earth_boulder", "reduction_percent", 18);
        if (has(victim, "wind_glide") && event.getCause() == EntityDamageEvent.DamageCause.FALL) reduction += config("wind_glide", "fall_reduction_percent", 85);
        if (has(victim, "dark_shadow") && isNight(victim.getWorld())) reduction += config("dark_shadow", "reduction_percent", 15);

        if ("EARTH-WIND".equals(combo)) reduction += 4;
        if ("EARTH-DARKNESS".equals(combo) && victim.isSneaking()) reduction += 16;
        if ("WIND-LIGHT".equals(combo) && event.getCause() == EntityDamageEvent.DamageCause.FALL) reduction += 25;
        if ("WIND-LIGHT-LIGHT".equals(combo) && event.getCause() == EntityDamageEvent.DamageCause.FALL) reduction += 100;
        if ("WATER-WATER-EARTH".equals(combo) && victim.isInWater()) reduction += 15;
        if ("WATER-DARKNESS-LIGHT".equals(combo)) reduction += 7;
        if ("DARKNESS-DARKNESS-LIGHT".equals(combo) && !isNight(victim.getWorld())) reduction += 12;
        if ("EARTH-EARTH-DARKNESS".equals(combo) && Set.of(Material.SOUL_SAND, Material.SOUL_SOIL).contains(victim.getLocation().subtract(0, 1, 0).getBlock().getType())) reduction += 28;
        if ("FIRE-FIRE".equals(combo) && victim.isInWater()) reduction -= 20;
        if ("FIRE-FIRE-FIRE".equals(combo)) reduction -= 15;
        if ("WIND-WIND-WIND".equals(combo)) reduction -= 20;
        event.setDamage(event.getDamage() * Math.max(0, 1 - reduction / 100.0));

        if ("FIRE-EARTH-WIND".equals(combo) && event.getCause() == EntityDamageEvent.DamageCause.FALL && victim.getFallDistance() > 5) {
            victim.getWorld().getNearbyEntities(victim.getLocation(), 4, 4, 4, entity -> entity instanceof LivingEntity && !entity.equals(victim))
                    .forEach(entity -> ((LivingEntity) entity).damage(4, victim));
        }
    }

    public void handleAttackCombos(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        String combo = combinationKey(attacker);
        if (combo == null) return;
        switch (combo) {
            case "FIRE-WATER" -> applyChanceEffect(victim, 13, PotionEffectType.BLINDNESS, 40, 1);
            case "FIRE-WIND" -> ignite(victim, 80);
            case "FIRE-LIGHT" -> { if (chancePercent(6)) event.setDamage(event.getDamage() * 2); }
            case "WATER-EARTH" -> applyChanceEffect(victim, 12, PotionEffectType.SLOWNESS, 60, 0);
            case "WATER-DARKNESS" -> applyChanceEffect(victim, 8, PotionEffectType.HUNGER, 80, 0);
            case "WIND-DARKNESS" -> applyChanceEffect(victim, 10, PotionEffectType.BLINDNESS, 20, 0);
            case "DARKNESS-DARKNESS" -> heal(attacker, event.getFinalDamage() * 0.04);
            case "DARKNESS-LIGHT" -> applyChanceEffect(victim, 4, PotionEffectType.WITHER, 60, 0);
            case "DARKNESS-DARKNESS-DARKNESS" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0, true, true, true));
            case "FIRE-FIRE-WATER" -> { if (chancePercent(15)) victim.damage(2, attacker); }
            case "FIRE-FIRE-WIND" -> { if (chancePercent(15)) igniteArea(attacker, victim, 2, 100); }
            case "FIRE-WATER-WATER" -> applyChanceEffect(victim, 15, PotionEffectType.SLOWNESS, 60, 1);
            case "FIRE-WATER-DARKNESS" -> { if (chancePercent(10)) damageArmor(victim, 5); }
            case "FIRE-WATER-LIGHT" -> { if (chancePercent(10)) removeShortestNegative(attacker); }
            case "FIRE-EARTH-EARTH" -> ignite(victim, 100);
            case "FIRE-EARTH-DARKNESS" -> { if (victim.getFireTicks() > 0) victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, true, true, true)); }
            case "FIRE-WIND-WIND" -> { if (chancePercent(14)) victim.setVelocity(victim.getVelocity().setY(1.1)); }
            case "FIRE-DARKNESS-DARKNESS" -> { if (chancePercent(10)) { victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, true, true)); victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, true, true)); } }
            case "WATER-WATER-WIND" -> effectsKnockback(victim, attacker, 1.2);
            case "WATER-WATER-LIGHT" -> { if (chancePercent(4)) temporaryAttribute(victim, attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED"), "combo_freeze", -1.0, 60); }
            case "WATER-EARTH-WIND" -> applyChanceEffect(victim, 12, PotionEffectType.WEAKNESS, 60, 1);
            case "WATER-EARTH-DARKNESS" -> { if (chancePercent(11)) { victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, true, true, true)); victim.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 80, 1, true, true, true)); } }
            case "WATER-WIND-DARKNESS" -> applyChanceEffect(victim, 7, PotionEffectType.POISON, 100, 0);
            case "WATER-DARKNESS-DARKNESS" -> { if (victim.isInWater() && chancePercent(15)) victim.setRemainingAir(Math.max(0, victim.getRemainingAir() / 2)); }
            case "EARTH-DARKNESS-DARKNESS" -> applyChanceEffect(victim, 8, PotionEffectType.WITHER, 40, 1);
            case "WIND-WIND-DARKNESS" -> { if (cooldownChance(attacker, "WIND-WIND-DARKNESS", 8, 8)) teleportBehind(attacker, victim); }
            case "WIND-DARKNESS-LIGHT" -> attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, true, true));
            default -> { }
        }
    }

    public void handleDefenseCombos(Player victim, LivingEntity damager, EntityDamageByEntityEvent event) {
        String combo = combinationKey(victim);
        if (combo == null) return;
        if ("FIRE-WATER-EARTH".equals(combo) && victim.getHealth() <= 6 && cooldown(victim, "FIRE-WATER-EARTH", 60)) victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1, true, true, true));
        if ("FIRE-WATER-WIND".equals(combo) && chancePercent(10)) strongKnockback(damager, victim, 5);
        if ("FIRE-WIND-DARKNESS".equals(combo) && chancePercent(17)) victim.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, true, false, true));
        if ("WATER-WIND-LIGHT".equals(combo) && chancePercent(6)) randomKnockback(damager);
        if ("EARTH-EARTH-WIND".equals(combo) && damager instanceof Player player && chancePercent(14)) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 2, true, true, true));
    }

    public void applyRegainBonus(Player player, EntityRegainHealthEvent event) {
        String combo = combinationKey(player);
        if ("WATER-LIGHT".equals(combo)) event.setAmount(event.getAmount() * 1.05);
        if ("LIGHT-LIGHT".equals(combo) && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) event.setAmount(event.getAmount() * 1.10);
    }

    public void tick(Player player) {
        applyAttributes(player);
        String combo = combinationKey(player);
        if (has(player, "water_regeneration") && due(player.getUniqueId(), lastRegeneration, intConfig("water_regeneration", "every_seconds", 10))) heal(player, config("water_regeneration", "heal_health", 1));
        if (has(player, "light_illumination")) player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
        if ("FIRE-EARTH".equals(combo) || "FIRE-FIRE-EARTH".equals(combo)) player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, true, false, false));
        if ("FIRE-FIRE-EARTH".equals(combo) && player.getLocation().getBlock().getType() == Material.LAVA && due(player.getUniqueId(), lastRegeneration, 5)) heal(player, 1);
        if ("WATER-WATER-WATER".equals(combo)) { player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, true, false, false)); player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0, true, false, false)); }
        if ("EARTH-EARTH-EARTH".equals(combo)) player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, true, false, false));
        if ("WIND-WIND-WIND".equals(combo)) player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, true, false, false));
        if ("DARKNESS-DARKNESS-DARKNESS".equals(combo) && !isNight(player.getWorld())) player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, true, false, false));
        if ("LIGHT-LIGHT-LIGHT".equals(combo) && !isNight(player.getWorld()) && due(player.getUniqueId(), lastRegeneration, 4)) heal(player, 1);
        if ("FIRE-WIND-LIGHT".equals(combo)) leaveFireTrail(player);
        if ("WATER-EARTH-EARTH".equals(combo)) auraEffect(player, 3, PotionEffectType.SLOWNESS, 60, 0);
        if ("WATER-EARTH-LIGHT".equals(combo) && player.isInWater()) player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 60, 0, true, false, false));
        if ("EARTH-WIND-DARKNESS".equals(combo) && !player.isOnGround()) auraEffect(player, 3, PotionEffectType.SLOWNESS, 40, 0);
        if ("EARTH-DARKNESS-LIGHT".equals(combo) && player.getHealth() <= 6) player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, true, false, false));
        if ("WIND-DARKNESS-DARKNESS".equals(combo) && isNight(player.getWorld())) player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, true, false, false));
        if ("DARKNESS-LIGHT-LIGHT".equals(combo)) auraEffect(player, 4, PotionEffectType.GLOWING, 60, 0, PotionEffectType.SLOWNESS, 60, 0);
        lastLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    public void heal(LivingEntity entity, double amount) {
        entity.setHealth(Math.min(maxHealth(entity), entity.getHealth() + amount));
    }

    public void knockback(LivingEntity victim, Player attacker, double strength) {
        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(strength).setY(0.45);
        victim.setVelocity(direction);
    }

    public void temporaryAttribute(LivingEntity entity, Attribute attribute, String key, double amount, int ticks) {
        if (attribute == null) return;
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        removeModifier(instance, namespacedKey);
        instance.addModifier(new AttributeModifier(namespacedKey, amount, AttributeModifier.Operation.ADD_SCALAR));
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeModifier(instance, namespacedKey), ticks);
    }

    private void applyAttributes(Player player) {
        boolean noArmor = Arrays.stream(player.getInventory().getArmorContents()).allMatch(i -> i == null || i.getType().isAir());
        double speed = 0, jump = 0, knockback = 0, health = 0, swim = 0;
        if (has(player, "wind_breeze")) speed += config("wind_breeze", "speed_percent", 15);
        if (has(player, "wind_lightness") && noArmor) { speed += config("wind_lightness", "speed_percent", 100); jump += config("wind_lightness", "jump_percent", 50); }
        if (has(player, "wind_gust")) jump += config("wind_gust", "jump_percent", 25);
        if (has(player, "earth_strength")) health += config("earth_strength", "extra_health", 4);
        if (has(player, "earth_roots")) knockback += 1;

        String combo = combinationKey(player);
        if ("WATER-WATER".equals(combo)) swim += 15;
        if ("EARTH-EARTH".equals(combo)) knockback += 0.05;
        if ("EARTH-WIND".equals(combo)) speed += 4;
        if ("EARTH-LIGHT".equals(combo)) health += 2;
        if ("WIND-WIND".equals(combo)) speed += 3;
        if ("FIRE-EARTH-LIGHT".equals(combo)) health += 6;
        if ("EARTH-EARTH-LIGHT".equals(combo)) knockback += 1;
        if ("EARTH-WIND-LIGHT".equals(combo) && Set.of(Material.SAND, Material.RED_SAND).contains(player.getLocation().subtract(0, 1, 0).getBlock().getType())) speed += 18;
        if ("EARTH-LIGHT-LIGHT".equals(combo)) { health += 10; speed -= 20; }
        if ("WIND-WIND-LIGHT".equals(combo)) jump += 20;
        if ("EARTH-EARTH-EARTH".equals(combo)) speed -= 17;
        setModifier(player, attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED"), "sum_speed", speed / 100.0);
        setModifier(player, attribute("JUMP_STRENGTH", "GENERIC_JUMP_STRENGTH"), "sum_jump", jump / 100.0);
        setModifier(player, attribute("KNOCKBACK_RESISTANCE", "GENERIC_KNOCKBACK_RESISTANCE"), "sum_kb", knockback, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(player, attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH"), "sum_hp", health, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(player, attribute("WATER_MOVEMENT_EFFICIENCY"), "sum_swim", swim / 100.0);
    }

    private void clearAttributes(Player player) {
        for (Attribute attribute : Attribute.values()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) new ArrayList<>(instance.getModifiers()).stream().filter(m -> m.getKey().getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))).forEach(instance::removeModifier);
        }
    }

    private void setModifier(Player player, Attribute attr, String key, double amount) { setModifier(player, attr, key, amount, AttributeModifier.Operation.ADD_SCALAR); }
    private void setModifier(Player player, Attribute attr, String key, double amount, AttributeModifier.Operation op) {
        if (attr == null) return;
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) return;
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        removeModifier(instance, namespacedKey);
        if (amount != 0) instance.addModifier(new AttributeModifier(namespacedKey, amount, op));
    }

    private void removeModifier(AttributeInstance instance, NamespacedKey key) {
        instance.getModifiers().stream().filter(m -> m.getKey().equals(key)).findFirst().ifPresent(instance::removeModifier);
    }

    public Attribute attribute(String... names) {
        for (String name : names) {
            try { return Attribute.valueOf(name); } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }

    private boolean chancePercent(double percent) { return ThreadLocalRandom.current().nextDouble(100.0) < percent; }
    private boolean due(UUID uuid, Map<UUID, Long> timestamps, int seconds) { long now = System.currentTimeMillis(); long last = timestamps.getOrDefault(uuid, 0L); if (now - last < seconds * 1000L) return false; timestamps.put(uuid, now); return true; }
    private boolean cooldown(Player player, String key, int seconds) { String id = player.getUniqueId() + ":" + key; long now = System.currentTimeMillis(); long last = cooldowns.getOrDefault(id, 0L); if (now - last < seconds * 1000L) return false; cooldowns.put(id, now); return true; }
    private boolean cooldownChance(Player player, String key, int seconds, double percent) { return cooldown(player, key, seconds) && chancePercent(percent); }
    private boolean isFire(EntityDamageEvent.DamageCause cause) { return Set.of(EntityDamageEvent.DamageCause.FIRE, EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.HOT_FLOOR).contains(cause); }
    private boolean isNight(World world) { long time = world.getTime(); return time >= 13000 && time <= 23000; }
    private boolean isStandingStill(Player player) { Location last = lastLocations.get(player.getUniqueId()); return last != null && last.getWorld().equals(player.getWorld()) && last.distanceSquared(player.getLocation()) < 0.0009; }
    private boolean isProjectileDamage(EntityDamageEvent event) { return event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile; }
    private double maxHealth(LivingEntity entity) { Attribute attr = attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH"); AttributeInstance instance = attr == null ? null : entity.getAttribute(attr); return instance == null ? 20.0 : instance.getValue(); }
    private boolean isUndead(LivingEntity entity) { return Set.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER, EntityType.STRAY, EntityType.PHANTOM, EntityType.WITHER, EntityType.ZOMBIFIED_PIGLIN).contains(entity.getType()); }
    private void applyChanceEffect(LivingEntity entity, double chance, PotionEffectType type, int ticks, int amplifier) { if (chancePercent(chance)) entity.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, true, true)); }
    private void ignite(LivingEntity entity, int ticks) { entity.setFireTicks(Math.max(entity.getFireTicks(), ticks)); }
    private void igniteArea(Player attacker, LivingEntity center, double radius, int ticks) { ignite(center, ticks); center.getWorld().getNearbyEntities(center.getLocation(), radius, radius, radius, entity -> entity instanceof LivingEntity && !entity.equals(attacker)).forEach(entity -> ignite((LivingEntity) entity, ticks)); }
    private void effectsKnockback(LivingEntity victim, Player attacker, double multiplier) { knockback(victim, attacker, 1.0 + multiplier); }
    private void strongKnockback(LivingEntity target, Player source, double blocks) { Vector v = target.getLocation().toVector().subtract(source.getLocation().toVector()).normalize().multiply(blocks / 2.0).setY(0.6); target.setVelocity(v); }
    private void randomKnockback(LivingEntity entity) { entity.setVelocity(new Vector(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), ThreadLocalRandom.current().nextDouble(0.7, 1.6), ThreadLocalRandom.current().nextDouble(-1.5, 1.5))); }
    private void damageArmor(LivingEntity entity, int damage) { if (!(entity instanceof Player player)) return; for (ItemStack armor : player.getInventory().getArmorContents()) if (armor != null && armor.getType().getMaxDurability() > 0) armor.setDurability((short) Math.min(armor.getType().getMaxDurability(), armor.getDurability() + damage)); }
    private void removeShortestNegative(Player player) { player.getActivePotionEffects().stream().filter(effect -> NEGATIVE.contains(effect.getType())).min(Comparator.comparingInt(PotionEffect::getDuration)).ifPresent(effect -> player.removePotionEffect(effect.getType())); }
    private void teleportBehind(Player player, LivingEntity target) { Vector dir = target.getLocation().getDirection().normalize().multiply(-1.5); Location loc = target.getLocation().add(dir); loc.setDirection(target.getLocation().toVector().subtract(loc.toVector())); player.teleport(loc); }
    private void leaveFireTrail(Player player) { if (player.isSprinting() || player.getVelocity().setY(0).lengthSquared() < 0.01) return; Location behind = player.getLocation().subtract(player.getLocation().getDirection().normalize()); if (behind.getBlock().getType() == Material.AIR && behind.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) behind.getBlock().setType(Material.FIRE); }
    private void auraEffect(Player player, double radius, PotionEffectType type, int ticks, int amplifier) { player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius, entity -> entity instanceof LivingEntity && !entity.equals(player)).forEach(entity -> ((LivingEntity) entity).addPotionEffect(new PotionEffect(type, ticks, amplifier, true, true, true))); }
    private void auraEffect(Player player, double radius, PotionEffectType t1, int d1, int a1, PotionEffectType t2, int d2, int a2) { player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius, entity -> entity instanceof LivingEntity && !entity.equals(player)).forEach(entity -> { LivingEntity l = (LivingEntity) entity; l.addPotionEffect(new PotionEffect(t1, d1, a1, true, true, true)); l.addPotionEffect(new PotionEffect(t2, d2, a2, true, true, true)); }); }

    private static final Map<String, String> COMBO_DESCRIPTIONS = createDescriptions();
    private static Map<String, String> createDescriptions() {
        Map<String, String> m = new HashMap<>();
        m.put("FIRE-FIRE", "&7Twoje ostrze zadaje &cgłębsze rany&7, lecz zanurzenie w &bwodzie &7przyniesie Ci potężniejszy ból.");
        m.put("FIRE-WATER", "&7Podczas ataku z Twojej broni bucha gęsta para, która może nagle &8odebrać wzrok &7wrogowi.");
        m.put("FIRE-EARTH", "&7Twoje ciało zyskało absolutną &aodporność na płomienie &7oraz &cgorącą lawę&7.");
        m.put("FIRE-WIND", "&7Porywy powietrza roznoszą iskry, dając szansę na &cpodpalenie &7przeciwnika przy uderzeniu.");
        m.put("FIRE-DARKNESS", "&7Twoje ciosy zyskują niszczycielską moc, gdy kroczysz przez czeluście &cNetheru&7.");
        m.put("FIRE-LIGHT", "&7Świetlisty błysk niesie ze sobą rzadką szansę na zadanie &cpodwójnie silnego ciosu&7.");
        m.put("WATER-WATER", "&7Zyskujesz naturalną lekkość, pozwalającą Ci znacznie &bszybciej mknąć &7przez wodne głębiny.");
        m.put("WATER-EARTH", "&7Wilgotna gleba pod nogami ofiar daje szansę na &8spętanie ich ruchów &7przy Twoim ataku.");
        m.put("WATER-WIND", "&7Wodne wiry powietrzne wokół Ciebie dają szansę na całkowite &bodbicie nadlatujących strzał&7.");
        m.put("WATER-DARKNESS", "&7Mroczny nurt niesie klątwę, która może wywołać u wroga &5nagły, dotkliwy głód&7.");
        m.put("WATER-LIGHT", "&7Krystalicznie czyste źródło sprawia, że &amikstury leczące &7zyskują dodatkową skuteczność.");
        m.put("EARTH-EARTH", "&7Wrosłeś w grunt tak mocno, że trudniej jest Cię &aodrzucić z miejsca ciosami&7.");
        m.put("EARTH-WIND", "&7Zyskujesz subtelne wzmocnienie &apancerza &7oraz lekkie przyspieszenie Twoich &bkroków&7.");
        m.put("EARTH-DARKNESS", "&7Gdy przywrzesz do ziemi i zaczniesz się &8skradać&7, Twój &apancerz znacznie twardnieje&7.");
        m.put("EARTH-LIGHT", "&7Życiodajna energia natury tętni w Twoich żyłach, na stałe wydłużając Twoje &azdrowie&7.");
        m.put("WIND-WIND", "&7Twoje stopy rzadziej dotykają ziemi, nieznacznie &bprzyspieszając Twój bieg&7.");
        m.put("WIND-DARKNESS", "&7Mroczny podmuch przy uderzeniu może na moment spowić wroga w &8całkowitej ciemności&7.");
        m.put("WIND-LIGHT", "&7Niewidzialna poduszka powietrzna częściowo łagodzi Twoje &bobrażenia z wysokości&7.");
        m.put("DARKNESS-DARKNESS", "&7Zbroczona krwią broń pozwala Ci stale &cwysysać życie &7z ran zadawanych Twoim wrogom.");
        m.put("DARKNESS-LIGHT", "&7Uderzenie niesie rzadką klątwę, która sprawia, że ciało przeciwnika zaczyna &5obumierać&7.");
        m.put("LIGHT-LIGHT", "&7Świetlisty pokarm o wiele sprawniej i szybciej &aregeneruje Twoje rany&7.");
        m.put("FIRE-FIRE-FIRE", "&7Zadajesz &cpotężniejsze obrażenia&7, lecz Twoja własna powłoka stała się o wiele &cbardziej krucha&7.");
        m.put("WATER-WATER-WATER", "&7Zyskujesz stałą zdolność &boddychania pod wodą &7oraz &bszybszego kopania &7w jej toni.");
        m.put("EARTH-EARTH-EARTH", "&7Zyskujesz stałą, potężną &aodporność na ciosy&7, lecz Twoje ruchy stają się &8bardzo ociężałe&7.");
        m.put("WIND-WIND-WIND", "&7Otrzymujesz stałe, silne &bprzyspieszenie biegów&7, lecz jesteś znacznie &cwątlejszy na ataki wroga&7.");
        m.put("DARKNESS-DARKNESS-DARKNESS", "&7Twoje ciosy zmuszają wroga do &5obumierania&7, lecz słońce za dnia zsyła na Ciebie &8słabość&7.");
        m.put("LIGHT-LIGHT-LIGHT", "&7Słońce za dnia stale powoli &aregeneruje Twoje zdrowie&7, lecz w nocy Twoje ciosy tracą na &csile&7.");
        m.put("FIRE-FIRE-WATER", "&7Wrząca fuzja daje szansę na zadanie ciosu, który całkowicie &cignoruje pancerz przeciwnika&7.");
        m.put("FIRE-FIRE-EARTH", "&7Kąpiel w &clawie &7całkowicie chroni Cię przed ogniem i powoli &aregeneruje Twoje zdrowie&7.");
        m.put("FIRE-FIRE-WIND", "&7Uderzenie może wywołać &bniebieską eksplozję&7, która podpali cel oraz wszystkich wrogów wokół.");
        m.put("FIRE-FIRE-DARKNESS", "&7Twoje ciosy stają się &cznacznie silniejsze&7, gdy cel jest już mocno &cosłabiony&7.");
        m.put("FIRE-FIRE-LIGHT", "&7Bezchmurne, &eczyste niebo &7sprawia, że Twoje ataki zadają znacznie &cwiększy ból&7.");
        m.put("FIRE-WATER-WATER", "&7Wrząca, gęsta mgła daje szansę na &8drastyczne spętanie i spowolnienie ruchów &7przeciwnika.");
        m.put("FIRE-WATER-EARTH", "&7Gdy Twoje zdrowie krytycznie spadnie, zyskasz tymczasową, &apotężną odporność na ciosy&7.");
        m.put("FIRE-WATER-WIND", "&7Otrzymanie obrażeń może wywołać wybuch pary, który &bdrastycznie odrzuci napastnika &7w tył.");
        m.put("FIRE-WATER-DARKNESS", "&7Korozyjny opar daje szansę na gwałtowne &8nadwerężenie trwałości zbroi &7Twojego przeciwnika.");
        m.put("FIRE-WATER-LIGHT", "&7Zadanie ciosu daje szansę na nagłe &eoczyszczenie Twojego ciała &7z jednej negatywnej klątwy.");
        m.put("FIRE-EARTH-EARTH", "&7Każdy Twój cios wręcz bezwzględnie trawi przeciwnika palącym, &bniebieskim płomieniem&7.");
        m.put("FIRE-EARTH-WIND", "&7Zeskok z dużej wysokości tworzy falę uderzeniową, raniącą wrogów &cbez względu na ich zbroję&7.");
        m.put("FIRE-EARTH-DARKNESS", "&7Wrogowie, których ogarną Twoje płomienie, zostaną dodatkowo &8pozbawieni siły w swoich ciosach&7.");
        m.put("FIRE-EARTH-LIGHT", "&7Potężny rdzeń żywotności na stałe obdarowuje Cię &amnożą dodatkowych serc&7.");
        m.put("FIRE-WIND-WIND", "&7Termiczny prąd powietrza daje szansę na gwałtowne &bwyrzucenie przeciwnika wysoko w górę&7.");
        m.put("FIRE-WIND-DARKNESS", "&7Gdy odniesiesz rany, zasłona dymna może natychmiast zapewnić Ci &5chwilową niewidzialność&7.");
        m.put("FIRE-WIND-LIGHT", "&7Spokojny marsz sprawia, że podłoże tuż za Twoimi plecami zaczyna &cstawać w płomieniach&7.");
        m.put("FIRE-DARKNESS-DARKNESS", "&7Mroczna gorączka daje szansę na jednoczesną &8utratę wzroku &7i &8silne spętanie ruchów &7wroga.");
        m.put("FIRE-DARKNESS-LIGHT", "&7Furia konającego sprawia, że Twoje ataki zyskują na &csile&7, gdy utracisz połowę zdrowia.");
        m.put("FIRE-LIGHT-LIGHT", "&7Pozbawienie życia Twej ofiary momentalnie i &anatychmiastowo regeneruje część Twoich ran&7.");
        m.put("WATER-WATER-EARTH", "&7Zanurzenie w wodzie sprawia, że Twój &apancerz staje się znacznie twardszy i odporniejszy&7.");
        m.put("WATER-WATER-WIND", "&7Siła tsunami sprawia, że Twoje ciosy o wiele &bmocniej odrzucają &7każdego przeciwnika.");
        m.put("WATER-WATER-DARKNESS", "&7Gdy Twoją głowę w pełni okrywa woda, zyskujesz potężną &cpremię do zadawanych obrażeń&7.");
        m.put("WATER-WATER-LIGHT", "&7Lodowy dotyk daje rzadką szansę na całkowite &bzamrożenie i zablokowanie ruchu &7wroga.");
        m.put("WATER-EARTH-EARTH", "&7Bagienna aura wokół Ciebie stale &8spętuje i spowalnia kroki &7pobliskich przeciwników.");
        m.put("WATER-EARTH-WIND", "&7Słony podmuch niesie szansę na nagłe i dotkliwe &8odebranie wrogowi siły w jego ciosach&7.");
        m.put("WATER-EARTH-DARKNESS", "&7Zatrucie zmysłów daje szansę na wywołanie u wroga &5zawrotów głowy &7i &5skrajnego łaknienia&7.");
        m.put("WATER-EARTH-LIGHT", "&7Brodzenie w wodzie nieustannie odświeża Twoje siły i &anasyca Twój pasek głodu&7.");
        m.put("WATER-WIND-WIND", "&7Gęsta, burzowa tarcza daje bardzo wysoką szansę na całkowite &bodbicie lecących w Ciebie strzał&7.");
        m.put("WATER-WIND-DARKNESS", "&7Toksyczny powiew przy ataku daje szansę na zainfekowanie ciała wroga &5powolną trucizną&7.");
        m.put("WATER-WIND-LIGHT", "&7Otrzymanie ciosu może wywołać falę, która odepchnie agresora w &blosowym, szalonym kierunku&7.");
        m.put("WATER-DARKNESS-DARKNESS", "&7Uderzenie pod wodą daje szansę na nagłe &8wyssanie połowy zapasu powietrza &7z płuc wroga.");
        m.put("WATER-DARKNESS-LIGHT", "&7Harmonia żywiołów zsyła Ci stałą, równą premię do &apancerza &7oraz &csiły ciosów&7.");
        m.put("WATER-LIGHT-LIGHT", "&7Święte źródło sprawia, że Twoje ataki zadają &czwielokrotnione rany nieumarłym potworom&7.");
        m.put("EARTH-EARTH-WIND", "&7Atakujący Cię wróg ryzykuje, że piaskowa zasłona nagle i drastycznie &8odbierze mu cały wzrok&7.");
        m.put("EARTH-EARTH-DARKNESS", "&7Kiedy stąpasz po piasku lub glebie dusz, Twój &apancerz zyskuje potężną twardość&7.");
        m.put("EARTH-EARTH-LIGHT", "&7Stajesz się niewzruszoną skałą, zyskując permanentną &aodporność na jakiekolwiek odrzucenie&7.");
        m.put("EARTH-WIND-WIND", "&7Postać pyłu daje szansę na całkowite &bzignorowanie i uniknięcie obrażeń &7(Wymaga odnowienia).");
        m.put("EARTH-WIND-DARKNESS", "&7Twój skok tuż obok przeciwnika tworzy wstrząs, który na chwilę &8spęta jego ruchy&7.");
        m.put("EARTH-WIND-LIGHT", "&7Zyskujesz dużą premię do &bszybkości biegu&7, krocząc po zwykłym oraz czerwonym piasku.");
        m.put("EARTH-DARKNESS-DARKNESS", "&7Dotyk rozkładu daje szansę na skażenie ciała przeciwnika czarną &5klątwą obumierania&7.");
        m.put("EARTH-DARKNESS-LIGHT", "&7Gdy Twoje zdrowie krytycznie spadnie, zyskasz stałą, podstawową &aodporność na kolejne ciosy&7.");
        m.put("EARTH-LIGHT-LIGHT", "&7Serce tytana daje &apotężny zastrzyk dodatkowego zdrowia&7, lecz mocno &8spowalnia Twój bieg&7.");
        m.put("WIND-WIND-DARKNESS", "&7Krok cienia daje szansę na nagłe &8przeteleportowanie się za plecy ofiary &7przy uderzeniu (Wymaga odnowienia).");
        m.put("WIND-WIND-LIGHT", "&7Zyskujesz stałe, wyraźne i stałe wzmocnienie &bwysokości Twoich skoków&7.");
        m.put("WIND-DARKNESS-DARKNESS", "&7Gdy zapada głęboka noc, Twoja postać zyskuje stały efekt &5całkowitej niewidzialności&7.");
        m.put("WIND-DARKNESS-LIGHT", "&7Zadanie celnego ciosu natychmiast zsyła na Twoje stopy chwilową &blekkość i szybkość&7.");
        m.put("WIND-LIGHT-LIGHT", "&7Władza nad grawitacją trwale zapewnia Ci &bcałkowity brak obrażeń przy upadkach z wysokości&7.");
        m.put("DARKNESS-DARKNESS-LIGHT", "&7Noc wzmacnia &csiłę Twoich ciosów&7, natomiast dzień zsyła dodatkową moc Twojemu &apancerzowi&7.");
        m.put("DARKNESS-LIGHT-LIGHT", "&7Wrogowie w bliskim otoczeniu zostają spętani &8spowolnieniem &7oraz zaczynają wyraźnie &ejaśnieć&7.");
        return Collections.unmodifiableMap(m);
    }
}
