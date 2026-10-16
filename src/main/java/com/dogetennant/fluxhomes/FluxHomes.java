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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class FluxHomes extends JavaPlugin {

    private ConfigUtil configUtil;
    private MessageUtil messageUtil;
    private DatabaseManager database;
    private CooldownManager cooldownManager;
    private HomeManager homeManager;
    /** All database work, one task at a time and in order. */
    private ExecutorService databaseThread;

    @Override
    public void onEnable() {
        // A run stopped before (the same instance enabled again) left its parts behind; onDisable
        // must only shut down what this run starts - it also runs when this run stays off.
        databaseThread = null;
        database = null;

        // Save default config
        saveDefaultConfig();
        configUtil = new ConfigUtil(this);
        // A config.yml with a YAML error reads as empty: filling in the "missing" options would save
        // the built-in ones over it, and FluxHomes would run on another database (SQLite, no prefix)
        if (configUtil.readFile() == null) {
            getLogger().severe("config.yml has a mistake (YAML error, see above), so FluxHomes stays off"
                    + " instead of running on the built-in settings. Fix it and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        reloadConfig();
        updateConfig();

        // Initialize utilities
        messageUtil = new MessageUtil(this);

        // Initialize database
        if (configUtil.getStorageType().equals("mysql")) {
            database = new MySQLManager(this);
        } else {
            database = new SQLiteManager(this);
        }
        try {
            database.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not connect to the database - FluxHomes is disabled. "
                    + "Check storage-type and the mysql section in config.yml.", e);
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        databaseThread = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "FluxHomes-Database");
            thread.setDaemon(true);
            return thread;
        });

        // Initialize managers
        cooldownManager = new CooldownManager();
        homeManager = new HomeManager(this, database, cooldownManager, databaseThread,
                task -> {
                    if (isEnabled()) getServer().getScheduler().runTask(this, task);
                },
                uuid -> getServer().getPlayer(uuid) != null);
        // players already online (plugin reload) get their homes loaded now
        getServer().getOnlinePlayers().forEach(player -> homeManager.loadInBackground(player.getUniqueId()));

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

        // Schedule cooldown cleanup every 5 minutes (on the main thread, like every other cooldown access)
        getServer().getScheduler().runTaskTimer(this,
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
            if (!config.isSet(key)) {
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
        if (databaseThread != null) {
            // let queued saves and deletes finish before the pool closes
            databaseThread.shutdown();
            try {
                if (!databaseThread.awaitTermination(10, TimeUnit.SECONDS)) {
                    getLogger().warning("Some home changes were still being saved when the server stopped.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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