package com.dogetennant.fluxhomes.utils;

import com.dogetennant.fluxhomes.FluxHomes;
import java.util.List;

public class ConfigUtil {

    private final FluxHomes plugin;

    public ConfigUtil(FluxHomes plugin) {
        this.plugin = plugin;
    }

    public String getStorageType() {
        return plugin.getConfig().getString("storage-type", "sqlite").toLowerCase();
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