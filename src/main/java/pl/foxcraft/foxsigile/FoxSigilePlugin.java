package pl.foxcraft.foxsigile;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import pl.foxcraft.foxsigile.command.SigileCommand;
import pl.foxcraft.foxsigile.command.SigileOpenCommand;
import pl.foxcraft.foxsigile.config.ConfigManager;
import pl.foxcraft.foxsigile.data.PlayerDataManager;
import pl.foxcraft.foxsigile.effect.EffectEngine;
import pl.foxcraft.foxsigile.gui.SigileGui;
import pl.foxcraft.foxsigile.item.SigilRegistry;
import pl.foxcraft.foxsigile.listener.GuiListener;
import pl.foxcraft.foxsigile.listener.PlayerListener;

public final class FoxSigilePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private SigilRegistry sigilRegistry;
    private PlayerDataManager dataManager;
    private EffectEngine effectEngine;
    private SigileGui sigileGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.sigilRegistry = new SigilRegistry(configManager);
        this.dataManager = new PlayerDataManager(this);
        this.effectEngine = new EffectEngine(this, sigilRegistry, dataManager);
        this.sigileGui = new SigileGui(configManager, sigilRegistry, dataManager, effectEngine);

        SigileCommand command = new SigileCommand(sigileGui, dataManager, effectEngine, configManager, sigilRegistry);
        getCommand("sigile").setExecutor(command);
        getCommand("sigile").setTabCompleter(command);
        SigileOpenCommand openCommand = new SigileOpenCommand(sigileGui, configManager);
        getCommand("sigileopen").setExecutor(openCommand);
        getCommand("sigileopen").setTabCompleter(openCommand);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this, sigileGui, sigilRegistry, dataManager, effectEngine, configManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this, dataManager, effectEngine, configManager), this);
        effectEngine.startTasks();
        Bukkit.getOnlinePlayers().forEach(player -> dataManager.load(player.getUniqueId()).thenRun(() -> Bukkit.getScheduler().runTask(this, () -> effectEngine.refreshPlayer(player))));
    }

    @Override
    public void onDisable() {
        if (effectEngine != null) {
            effectEngine.clearAll();
        }
        if (dataManager != null) {
            dataManager.saveAllNow();
        }
    }
}
