package pl.foxcraft.foxsigile.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.foxcraft.foxsigile.config.ConfigManager;
import pl.foxcraft.foxsigile.gui.SigileGui;

import java.util.List;

public final class SigileOpenCommand implements CommandExecutor, TabCompleter {
    private final SigileGui gui;
    private final ConfigManager config;

    public SigileOpenCommand(SigileGui gui, ConfigManager config) {
        this.gui = gui;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxsigile.admin")) {
            sender.sendMessage(config.message("no_permission"));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(config.message("usage_open"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(config.message("player_not_found"));
            return true;
        }
        gui.openEditable(target);
        sender.sendMessage(config.message("opened_edit_gui"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
