package com.dogetennant.fluxhomes;

import com.dogetennant.fluxhomes.database.DatabaseManager;
import com.dogetennant.fluxhomes.database.MySQLManager;
import com.dogetennant.fluxhomes.database.SQLiteManager;
import com.dogetennant.fluxhomes.listeners.PlayerListener;
import com.dogetennant.fluxhomes.listeners.GUIListener;
import com.dogetennant.fluxhomes.managers.CooldownManager;
import com.dogetennant.fluxhomes.managers.HomeManager;
import com.dogetennant.fluxhomes.utils.ConfigUtil;
import com.dogetennant.fluxhomes.utils.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;
import com.dogetennant.fluxhomes.commands.HomeCommand;
import com.dogetennant.fluxhomes.commands.AdminHomeCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStream;

public class FluxHomes extends JavaPlugin {

    private ConfigUtil configUtil;
    private MessageUtil messageUtil;
    private DatabaseManager database;
    private CooldownManager cooldownManager;
    private HomeManager homeManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        updateConfig();

        // Initialize utilities
        configUtil = new ConfigUtil(this);
        messageUtil = new MessageUtil(this);

        // Initialize database
        if (configUtil.getStorageType().equals("mysql")) {
            database = new MySQLManager(this);
        } else {
            database = new SQLiteManager(this);
        }
        database.initialize();

        // Initialize managers
        cooldownManager = new CooldownManager();
        homeManager = new HomeManager(this, database, cooldownManager);

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        // Register commands
        HomeCommand homeCommand = new HomeCommand(this);
        getCommand("home").setExecutor(homeCommand);
        getCommand("home").setTabCompleter(homeCommand);
        getCommand("sethome").setExecutor(homeCommand);
        getCommand("delhome").setExecutor(homeCommand);
        getCommand("delhome").setTabCompleter(homeCommand);
        getCommand("homes").setExecutor(homeCommand);

        AdminHomeCommand adminCommand = new AdminHomeCommand(this);
        getCommand("homesadmin").setExecutor(adminCommand);
        getCommand("homesadmin").setTabCompleter(adminCommand);

        // Schedule cooldown cleanup every 5 minutes
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> cooldownManager.cleanup(), 6000L, 6000L);

        getLogger().info("FluxHomes has been enabled!");
    }

    private void updateConfig() {
        FileConfiguration config = getConfig();
        InputStream defaultStream = getResource("config.yml");
        if (defaultStream == null) return;

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(defaultStream));

        boolean changed = false;
        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                changed = true;
            }
        }

        if (changed) {
            saveConfig();
            getLogger().info("Added missing keys to config.yml.");
        }
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.shutdown();
        }
        getLogger().info("FluxHomes has been disabled!");
    }

    public ConfigUtil getConfigUtil() { return configUtil; }
    public MessageUtil getMessageUtil() { return messageUtil; }
    public DatabaseManager getDatabase() { return database; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public HomeManager getHomeManager() { return homeManager; }
}