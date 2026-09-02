package pl.foxcraft.foxsigile.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.foxcraft.foxsigile.config.ConfigManager;
import pl.foxcraft.foxsigile.data.PlayerDataManager;
import pl.foxcraft.foxsigile.data.PlayerSigilData;
import pl.foxcraft.foxsigile.effect.EffectEngine;
import pl.foxcraft.foxsigile.gui.SigileGui;
import pl.foxcraft.foxsigile.item.SigilRegistry;

import java.util.ArrayList;
import java.util.List;

public final class SigileCommand implements CommandExecutor, TabCompleter {
    private final SigileGui gui; private final PlayerDataManager data; private final EffectEngine effects; private final ConfigManager config; private final SigilRegistry registry;
    public SigileCommand(SigileGui gui, PlayerDataManager data, EffectEngine effects, ConfigManager config, SigilRegistry registry) { this.gui = gui; this.data = data; this.effects = effects; this.config = config; this.registry = registry; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { if (sender instanceof Player player) gui.open(player); else sender.sendMessage(config.message("player_only")); return true; }
        if (args.length != 3 || !(args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("lock"))) { sender.sendMessage(config.message("usage_admin")); return true; }
        if (!sender.hasPermission("foxsigile.admin")) { sender.sendMessage(config.message("no_permission")); return true; }
        Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { sender.sendMessage(config.message("player_not_found")); return true; }
        boolean unlock = args[0].equalsIgnoreCase("unlock");
        PlayerSigilData playerData = data.get(target.getUniqueId());
        List<Integer> slots = parseSlots(args[2]); if (slots.isEmpty()) { sender.sendMessage(config.message("bad_slot")); return true; }
        for (int slot : slots) {
            if (!unlock && playerData.getSigil(slot) != null) {
                target.getInventory().addItem(registry.createItem(playerData.getSigil(slot), 1)).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
            }
            playerData.setUnlocked(slot, unlock);
        }
        data.saveAsync(target.getUniqueId()); effects.refreshPlayer(target); sender.sendMessage(config.message(unlock ? "admin_unlocked" : "admin_locked")); return true;
    }
    private List<Integer> parseSlots(String input) { List<Integer> slots = new ArrayList<>(); if (input.equalsIgnoreCase("all")) { slots.add(0); slots.add(1); slots.add(2); return slots; } try { int slot = Integer.parseInt(input); if (slot >= 1 && slot <= 3) slots.add(slot - 1); } catch (NumberFormatException ignored) {} return slots; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("unlock", "lock");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3) return List.of("all", "1", "2", "3");
        return List.of();
    }
}
