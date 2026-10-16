package com.dogetennant.fluxhomes.utils;

import com.dogetennant.fluxhomes.FluxHomes;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigUtil {

    private final FluxHomes plugin;

    public ConfigUtil(FluxHomes plugin) {
        this.plugin = plugin;
    }

    /**
     * config.yml as it is on disk now, or null when it has a YAML error (logged). Bukkit itself
     * reads such a file as empty - the built-in settings, and an empty file once saved.
     */
    public YamlConfiguration readFile() {
        YamlConfiguration file = new YamlConfiguration();
        try {
            file.load(new File(plugin.getDataFolder(), "config.yml"));
            return file;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("config.yml cannot be read, so it is left as it is: " + e.getMessage());
            return null;
        }
    }

    /**
     * Changes one option, in the settings in use and in config.yml on disk. Everything else in the
     * file stays as it is there, also hand edits not reloaded yet. A file with a YAML error is left
     * alone: saving the settings in use over it would throw away whatever the admin wrote in it.
     *
     * @return false when config.yml has an error (nothing changed)
     */
    public boolean set(String path, Object value) {
        YamlConfiguration file = readFile();
        if (file == null) return false;
        file.set(path, value);
        try {
            file.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
            return false;
        }
        plugin.getConfig().set(path, value);
        return true;
    }

    public String getStorageType() {
        return plugin.getConfig().getString("storage-type", "sqlite").toLowerCase();
    }

    public String getTablePrefix() {
        return plugin.getConfig().getString("table-prefix", "");
    }

    public int getMaxHomes(String permissionGroup) {
        return plugin.getConfig().getInt("max-homes.default", 3);
    }

    public boolean isCooldownEnabled() {
        return plugin.getConfig().getBoolean("cooldown.enabled", true);
    }

    public int getCooldownSeconds() {
        return plugin.getConfig().getInt("cooldown.seconds", 5);
    }

    public boolean isWarmupEnabled() {
        return plugin.getConfig().getBoolean("warmup.enabled", true);
    }

    public int getWarmupSeconds() {
        return plugin.getConfig().getInt("warmup.seconds", 3);
    }

    public boolean isTeleportSoundEnabled() {
        return plugin.getConfig().getBoolean("teleport-sound.enabled", true);
    }

    public boolean isRespawnAtHomeEnabled() {
        return plugin.getConfig().getBoolean("respawn-at-home.enabled", false);
    }

    public boolean isWorldBlockingEnabled() {
        return plugin.getConfig().getBoolean("blocked-worlds.enabled", false);
    }

    public List<String> getBlockedWorlds() {
        return plugin.getConfig().getStringList("blocked-worlds.worlds");
    }

    public String getLanguage() {
        return plugin.getConfig().getString("language", "en_us");
    }

    public boolean isConfirmDeletionEnabled() {
        return plugin.getConfig().getBoolean("confirm-deletion.enabled", true);
    }

    public int getConfirmDeletionTimeout() {
        return plugin.getConfig().getInt("confirm-deletion.timeout", 30);
    }

}