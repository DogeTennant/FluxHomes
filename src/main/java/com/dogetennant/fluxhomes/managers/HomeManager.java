package com.dogetennant.fluxhomes.managers;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.database.DatabaseManager;
import com.dogetennant.fluxhomes.models.Home;
import com.dogetennant.fluxhomes.models.SetHomeResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Homes, kept in memory for online players.
 *
 * <ul>
 *   <li>A player's homes are loaded while they log in ({@link #loadBlocking}) and dropped when they
 *       leave ({@link #unload}). Commands, tab completion and respawn read that cache, so the main
 *       thread never waits for the database.</li>
 *   <li>Everything that touches the database runs on {@code databaseThread}, a single thread, so
 *       writes reach the database in the order they were made (a delete never overtakes the save
 *       before it). Results come back through {@code mainThread}.</li>
 *   <li>Homes of players who are not loaded (admin commands for offline players) are read from the
 *       database on that thread.</li>
 * </ul>
 *
 * The cached maps are only changed on the main thread.
 */
public class HomeManager {

    /** Longest home name; the MySQL column is {@code VARCHAR(32)}. */
    public static final int MAX_NAME_LENGTH = 32;

    private static final String LIMIT_PERMISSION = "fluxhomes.homes.";

    private final FluxHomes plugin;
    private final DatabaseManager database;
    private final CooldownManager cooldownManager;
    private final Executor databaseThread;
    private final Executor mainThread;
    private final Predicate<UUID> isOnline;

    /** Homes of loaded players, name to home, in database order. */
    private final Map<UUID, Map<String, Home>> cache = new ConcurrentHashMap<>();

    /** The running teleport warmup per player (main thread). */
    private final Map<UUID, BukkitRunnable> warmups = new HashMap<>();

    /**
     * @param databaseThread runs database work, one task at a time in order
     * @param mainThread     runs results on the server thread
     * @param isOnline       whether a player is online (only their homes are kept in the cache)
     */
    public HomeManager(FluxHomes plugin, DatabaseManager database, CooldownManager cooldownManager,
                       Executor databaseThread, Executor mainThread, Predicate<UUID> isOnline) {
        this.plugin = plugin;
        this.database = database;
        this.cooldownManager = cooldownManager;
        this.databaseThread = databaseThread;
        this.mainThread = mainThread;
        this.isOnline = isOnline;
    }

    //
    // Cache
    //

    /**
     * Loads a player's homes into the cache, waiting for the database thread (so earlier writes
     * are done first). For {@code AsyncPlayerPreLoginEvent}, which runs off the main thread.
     */
    public void loadBlocking(UUID owner) {
        CompletableFuture<List<Home>> loaded = new CompletableFuture<>();
        databaseThread.execute(() -> {
            try {
                loaded.complete(database.getHomes(owner));
            } catch (RuntimeException e) {
                loaded.completeExceptionally(e);
            }
        });
        try {
            cache.put(owner, byName(loaded.get(10, TimeUnit.SECONDS)));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not load the homes of " + owner
                    + " at login; they are loaded on first use.", e);
        }
    }

    /** Loads the homes of a player who is already online (e.g. after a plugin reload). */
    public void loadInBackground(UUID owner) {
        withHomes(owner, homes -> { });
    }

    /** Drops a player's homes from the cache (they are saved already). */
    public void unload(UUID owner) {
        cache.remove(owner);
    }

    /** A loaded player's homes, or empty when they are not loaded. Main thread. */
    public Optional<List<Home>> cachedHomes(UUID owner) {
        Map<String, Home> homes = cache.get(owner);
        return homes == null ? Optional.empty() : Optional.of(List.copyOf(homes.values()));
    }

    /** A loaded player's home, or empty when it does not exist or the player is not loaded. */
    public Optional<Home> cachedHome(UUID owner, String name) {
        Map<String, Home> homes = cache.get(owner);
        return homes == null ? Optional.empty() : Optional.ofNullable(homes.get(name));
    }

    /**
     * Runs {@code then} on the main thread with the owner's homes (name to home, changeable):
     * right away from the cache, or after loading them on the database thread.
     */
    private void withHomes(UUID owner, Consumer<Map<String, Home>> then) {
        Map<String, Home> cached = cache.get(owner);
        if (cached != null) {
            then.accept(cached);
            return;
        }
        databaseThread.execute(() -> {
            Map<String, Home> loaded = byName(database.getHomes(owner));
            mainThread.execute(() -> {
                Map<String, Home> current = cache.get(owner);
                if (current == null && isOnline.test(owner)) {
                    cache.put(owner, loaded);
                    current = loaded;
                }
                then.accept(current != null ? current : loaded);
            });
        });
    }

    private static Map<String, Home> byName(List<Home> homes) {
        Map<String, Home> map = new LinkedHashMap<>();
        for (Home home : homes) map.put(home.getName(), home);
        return map;
    }

    /** Runs {@code work} on the database thread and hands its result to {@code then} on the main thread. */
    public <T> void inBackground(Supplier<T> work, Consumer<T> then) {
        databaseThread.execute(() -> {
            T result = work.get();
            mainThread.execute(() -> then.accept(result));
        });
    }

    //
    // Limits
    //

    /**
     * {@code fluxhomes.homes.unlimited}, else the highest {@code fluxhomes.homes.<n>} the player has,
     * else {@code max-homes.default}.
     */
    public int getMaxHomes(Player player) {
        if (player.hasPermission(LIMIT_PERMISSION + "unlimited")) {
            return Integer.MAX_VALUE;
        }
        int limit = homeLimit(player.getEffectivePermissions());
        return limit > 0 ? limit : plugin.getConfigUtil().getMaxHomes("default");
    }

    /** The highest {@code fluxhomes.homes.<n>} set to true, or 0 without one. */
    static int homeLimit(Iterable<PermissionAttachmentInfo> permissions) {
        int highest = 0;
        for (PermissionAttachmentInfo permission : permissions) {
            String node = permission.getPermission();
            if (!permission.getValue() || !node.startsWith(LIMIT_PERMISSION)) continue;
            try {
                highest = Math.max(highest, Integer.parseInt(node.substring(LIMIT_PERMISSION.length())));
            } catch (NumberFormatException ignored) {
                // fluxhomes.homes.unlimited or another word
            }
        }
        return highest;
    }

    //
    // Homes
    //

    /** {@code /sethome}: the home is where the player stands now; {@code then} gets the result. */
    public void setHome(Player player, String name, Consumer<SetHomeResult> then) {
        UUID uuid = player.getUniqueId();

        if (plugin.getConfigUtil().isWorldBlockingEnabled()) {
            String worldName = player.getWorld().getName();
            if (plugin.getConfigUtil().getBlockedWorlds().contains(worldName)) {
                then.accept(SetHomeResult.WORLD_BLOCKED);
                return;
            }
        }
        if (name.length() > MAX_NAME_LENGTH) {
            then.accept(SetHomeResult.NAME_TOO_LONG);
            return;
        }

        int maxHomes = getMaxHomes(player);
        Location loc = player.getLocation();
        Home home = new Home(uuid, name, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());

        withHomes(uuid, homes -> {
            if (!homes.containsKey(name) && homes.size() >= maxHomes) {
                then.accept(SetHomeResult.LIMIT_REACHED);
                return;
            }
            homes.put(name, home);
            databaseThread.execute(() -> database.saveHome(home));
            then.accept(SetHomeResult.SUCCESS);
        });
    }

    /** {@code then} gets whether the home existed (and is deleted now). */
    public void deleteHome(UUID ownerUUID, String name, Consumer<Boolean> then) {
        withHomes(ownerUUID, homes -> {
            if (homes.remove(name) == null) {
                then.accept(false);
                return;
            }
            databaseThread.execute(() -> database.deleteHome(ownerUUID, name));
            then.accept(true);
        });
    }

    public void deleteAllHomes(UUID ownerUUID) {
        Map<String, Home> cached = cache.get(ownerUUID);
        if (cached != null) cached.clear();
        databaseThread.execute(() -> database.deleteAllHomes(ownerUUID));
    }

    /** {@code then} gets the home, or {@code null} when it does not exist. */
    public void getHome(UUID ownerUUID, String name, Consumer<Home> then) {
        withHomes(ownerUUID, homes -> then.accept(homes.get(name)));
    }

    public void getHomes(UUID ownerUUID, Consumer<List<Home>> then) {
        withHomes(ownerUUID, homes -> then.accept(new ArrayList<>(homes.values())));
    }

    //
    // Teleport
    //

    /** Teleports after the warmup; a new call replaces the player's running warmup. */
    public void teleportHome(Player player, Home home) {
        UUID uuid = player.getUniqueId();

        if (plugin.getConfigUtil().isCooldownEnabled()) {
            if (cooldownManager.isOnCooldown(uuid)) {
                plugin.getMessageUtil().send(player, "cooldown",
                        "{seconds}", String.valueOf(cooldownManager.getRemainingSeconds(uuid)));
                return;
            }
        }

        BukkitRunnable running = warmups.remove(uuid);
        if (running != null) running.cancel();

        if (plugin.getConfigUtil().isWarmupEnabled()) {
            int warmup = plugin.getConfigUtil().getWarmupSeconds();
            plugin.getMessageUtil().send(player, "warmup-start", "{seconds}", String.valueOf(warmup));

            Location startLocation = player.getLocation().clone();
            final int[] ticksElapsed = {0};
            final int totalTicks = warmup * 20;

            BukkitRunnable warmupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        finish();
                        return;
                    }

                    Location current = player.getLocation();
                    if (current.getBlockX() != startLocation.getBlockX() ||
                            current.getBlockY() != startLocation.getBlockY() ||
                            current.getBlockZ() != startLocation.getBlockZ()) {
                        plugin.getMessageUtil().send(player, "warmup-cancelled");
                        finish();
                        return;
                    }

                    ticksElapsed[0]++;
                    if (ticksElapsed[0] >= totalTicks) {
                        finish();
                        performTeleport(player, home);
                    }
                }

                private void finish() {
                    cancel();
                    warmups.remove(uuid, this);
                }
            };
            warmups.put(uuid, warmupTask);
            warmupTask.runTaskTimer(plugin, 0L, 1L);
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

    //
    // EssentialsX import
    //

    /**
     * Imports on the database thread, refreshes the loaded players' homes, and gives {@code then}
     * the number of imported homes ({@code -1}: no EssentialsX userdata folder).
     */
    public void importFromEssentialsX(Consumer<Integer> then) {
        databaseThread.execute(() -> {
            int count = importFromEssentialsX();
            Map<UUID, Map<String, Home>> reloaded = new HashMap<>();
            for (UUID owner : List.copyOf(cache.keySet())) {
                reloaded.put(owner, byName(database.getHomes(owner)));
            }
            mainThread.execute(() -> {
                reloaded.forEach((owner, homes) -> cache.computeIfPresent(owner, (k, old) -> homes));
                then.accept(count);
            });
        });
    }

    /**
     * Reads {@code plugins/Essentials/userdata/<uuid>.yml} and saves every home the player does not
     * have yet. Blocks; runs on the database thread. Returns the number imported, or {@code -1}
     * when there is no userdata folder.
     */
    int importFromEssentialsX() {
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

            for (String essentialsName : homesSection.getKeys(false)) {
                ConfigurationSection homeData = homesSection.getConfigurationSection(essentialsName);
                if (homeData == null) continue;

                String world = homeData.getString("world");
                if (world == null) continue;
                world = essentialsWorldName(world, homeData.getString("world-name"));

                // /home lowercases what players type, and names are limited like /sethome
                String homeName = essentialsName.toLowerCase(Locale.ROOT);
                if (homeName.length() > MAX_NAME_LENGTH) continue;

                double x = homeData.getDouble("x");
                double y = homeData.getDouble("y");
                double z = homeData.getDouble("z");
                float yaw = (float) homeData.getDouble("yaw");
                float pitch = (float) homeData.getDouble("pitch");

                // Skip if home already exists
                if (database.getHome(ownerUUID, homeName) != null) continue;

                Home home = new Home(ownerUUID, homeName, world, x, y, z, yaw, pitch);
                database.saveHome(home);
                count++;
            }
        }

        return count;
    }

    /**
     * The world name of an EssentialsX home. Since EssentialsX 2.19 {@code world} is the world's
     * UUID and {@code world-name} its name; older files have the name in {@code world}. Like
     * EssentialsX: the loaded world with that UUID, else {@code world-name}, else {@code world}.
     */
    static String essentialsWorldName(String world, String worldName) {
        UUID worldId;
        try {
            worldId = UUID.fromString(world);
        } catch (IllegalArgumentException e) {
            return world; // older format: already a name
        }
        World loaded = Bukkit.getWorld(worldId);
        if (loaded != null) return loaded.getName();
        return worldName != null && !worldName.isEmpty() ? worldName : world;
    }
}
