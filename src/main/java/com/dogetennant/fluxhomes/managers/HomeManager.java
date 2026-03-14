package com.dogetennant.fluxhomes.managers;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.database.DatabaseManager;
import com.dogetennant.fluxhomes.models.Home;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.dogetennant.fluxhomes.models.SetHomeResult;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

import java.util.List;
import java.util.UUID;

public class HomeManager {

    private final FluxHomes plugin;
    private final DatabaseManager database;
    private final CooldownManager cooldownManager;

    public HomeManager(FluxHomes plugin, DatabaseManager database, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.database = database;
        this.cooldownManager = cooldownManager;
    }

    public int getMaxHomes(Player player) {
        // Check permission-based home limits, e.g. fluxhomes.homes.10
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("fluxhomes.homes." + i)) {
                return i;
            }
        }
        if (player.hasPermission("fluxhomes.homes.unlimited")) {
            return Integer.MAX_VALUE;
        }
        return plugin.getConfigUtil().getMaxHomes("default");
    }

    public SetHomeResult setHome(Player player, String name) {
        UUID uuid = player.getUniqueId();

        if (plugin.getConfigUtil().isWorldBlockingEnabled()) {
            String worldName = player.getWorld().getName();
            if (plugin.getConfigUtil().getBlockedWorlds().contains(worldName)) {
                return SetHomeResult.WORLD_BLOCKED;
            }
        }

        int maxHomes = getMaxHomes(player);
        Home existing = database.getHome(uuid, name);
        if (existing == null && database.getHomeCount(uuid) >= maxHomes) {
            return SetHomeResult.LIMIT_REACHED;
        }

        Location loc = player.getLocation();
        Home home = new Home(uuid, name, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());

        new BukkitRunnable() {
            @Override
            public void run() {
                database.saveHome(home);
            }
        }.runTaskAsynchronously(plugin);

        return SetHomeResult.SUCCESS;
    }

    public boolean deleteHome(UUID ownerUUID, String name) {
        Home home = database.getHome(ownerUUID, name);
        if (home == null) return false;

        new BukkitRunnable() {
            @Override
            public void run() {
                database.deleteHome(ownerUUID, name);
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    public void deleteAllHomes(UUID ownerUUID) {
        new BukkitRunnable() {
            @Override
            public void run() {
                database.deleteAllHomes(ownerUUID);
            }
        }.runTaskAsynchronously(plugin);
    }

    public Home getHome(UUID ownerUUID, String name) {
        return database.getHome(ownerUUID, name);
    }

    public List<Home> getHomes(UUID ownerUUID) {
        return database.getHomes(ownerUUID);
    }

    public void teleportHome(Player player, Home home) {
        UUID uuid = player.getUniqueId();

        if (plugin.getConfigUtil().isCooldownEnabled()) {
            if (cooldownManager.isOnCooldown(uuid)) {
                plugin.getMessageUtil().send(player, "cooldown",
                        "{seconds}", String.valueOf(cooldownManager.getRemainingSeconds(uuid)));
                return;
            }
        }

        if (plugin.getConfigUtil().isWarmupEnabled()) {
            int warmup = plugin.getConfigUtil().getWarmupSeconds();
            plugin.getMessageUtil().send(player, "warmup-start", "{seconds}", String.valueOf(warmup));

            Location startLocation = player.getLocation().clone();
            final int[] ticksElapsed = {0};
            final int totalTicks = warmup * 20;

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }

                    Location current = player.getLocation();
                    if (current.getBlockX() != startLocation.getBlockX() ||
                            current.getBlockY() != startLocation.getBlockY() ||
                            current.getBlockZ() != startLocation.getBlockZ()) {
                        plugin.getMessageUtil().send(player, "warmup-cancelled");
                        cancel();
                        return;
                    }

                    ticksElapsed[0]++;
                    if (ticksElapsed[0] >= totalTicks) {
                        performTeleport(player, home);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        } else {
            performTeleport(player, home);
        }
    }

    private void performTeleport(Player player, Home home) {
        Location loc = home.toLocation();
        if (loc.getWorld() == null) {
            plugin.getMessageUtil().send(player, "world-not-found");
            return;
        }

        player.teleport(loc);

        if (plugin.getConfigUtil().isTeleportSoundEnabled()) {
            player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }

        if (plugin.getConfigUtil().isCooldownEnabled()) {
            cooldownManager.setCooldown(player.getUniqueId(), plugin.getConfigUtil().getCooldownSeconds());
        }

        plugin.getMessageUtil().send(player, "teleport-success", "{home}", home.getName());
    }

    public int importFromEssentialsX() {
        File esDataFolder = new File(plugin.getServer().getPluginsFolder(), "Essentials/userdata");
        if (!esDataFolder.exists() || !esDataFolder.isDirectory()) {
            return -1; // Essentials folder not found
        }

        int count = 0;
        File[] files = esDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return 0;

        for (File file : files) {
            // File name is the player's UUID
            String uuidStr = file.getName().replace(".yml", "");
            UUID ownerUUID;
            try {
                ownerUUID = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue; // Skip invalid filenames
            }

            YamlConfiguration playerData = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection homesSection = playerData.getConfigurationSection("homes");
            if (homesSection == null) continue;

            for (String homeName : homesSection.getKeys(false)) {
                ConfigurationSection homeData = homesSection.getConfigurationSection(homeName);
                if (homeData == null) continue;

                String world = homeData.getString("world");
                double x = homeData.getDouble("x");
                double y = homeData.getDouble("y");
                double z = homeData.getDouble("z");
                float yaw = (float) homeData.getDouble("yaw");
                float pitch = (float) homeData.getDouble("pitch");

                if (world == null) continue;

                // Skip if home already exists
                if (database.getHome(ownerUUID, homeName) != null) continue;

                Home home = new Home(ownerUUID, homeName, world, x, y, z, yaw, pitch);
                database.saveHome(home);
                count++;
            }
        }

        return count;
    }
}