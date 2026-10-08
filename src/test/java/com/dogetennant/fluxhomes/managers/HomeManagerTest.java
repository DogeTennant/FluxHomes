package com.dogetennant.fluxhomes.managers;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.TestPlugin;
import com.dogetennant.fluxhomes.database.SQLiteManager;
import com.dogetennant.fluxhomes.models.Home;
import com.dogetennant.fluxhomes.models.SetHomeResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Homes, the cache, limits, blocked worlds and the EssentialsX import, on a real SQLite database.
 * The database thread and the main thread run tasks right away here, so results can be checked
 * directly; {@link #writesReachTheDatabaseInOrder} uses a real background thread.
 */
class HomeManagerTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");

    @TempDir
    Path dataFolder;
    @TempDir
    Path pluginsFolder;

    private FluxHomes plugin;
    private SQLiteManager db;
    private HomeManager homes;
    private final Set<String> permissions = new HashSet<>();
    /** Permissions set to false (negated), e.g. by a group that takes them away. */
    private final Set<String> negated = new HashSet<>();
    private final Set<UUID> online = new HashSet<>();

    @BeforeEach
    void setUp() throws Exception {
        TestPlugin.bukkitServer(); // the import resolves world UUIDs through Bukkit
        plugin = TestPlugin.mockPlugin(dataFolder, pluginsFolder);
        when(plugin.getConfigUtil().getMaxHomes("default")).thenReturn(2);
        when(plugin.getConfigUtil().getBlockedWorlds()).thenReturn(List.of("resource_world"));
        db = new SQLiteManager(plugin);
        db.initialize();
        homes = new HomeManager(plugin, db, new CooldownManager(), Runnable::run, Runnable::run, online::contains);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private Player alexIn(String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ALEX);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 10.5, 70, -3.25, 45f, 10f));
        when(player.hasPermission(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0, String.class)));
        when(player.getEffectivePermissions()).thenAnswer(call -> Stream.concat(
                permissions.stream().map(p -> new PermissionAttachmentInfo(player, p, null, true)),
                negated.stream().map(p -> new PermissionAttachmentInfo(player, p, null, false)))
                .collect(Collectors.toSet()));
        return player;
    }

    private SetHomeResult setHome(Player player, String name) {
        AtomicReference<SetHomeResult> result = new AtomicReference<>();
        homes.setHome(player, name, result::set);
        return result.get();
    }

    private Home getHome(UUID owner, String name) {
        AtomicReference<Home> result = new AtomicReference<>();
        homes.getHome(owner, name, result::set);
        return result.get();
    }

    private Boolean deleteHome(UUID owner, String name) {
        AtomicReference<Boolean> result = new AtomicReference<>();
        homes.deleteHome(owner, name, result::set);
        return result.get();
    }

    //
    // /sethome
    //

    @Test
    void setHomeStoresWhereThePlayerStands() {
        assertThat(setHome(alexIn("world"), "base")).isEqualTo(SetHomeResult.SUCCESS);

        assertThat(db.getHome(ALEX, "base")).usingRecursiveComparison()
                .isEqualTo(new Home(ALEX, "base", "world", 10.5, 70, -3.25, 45f, 10f));
    }

    @Test
    void theLimitStopsNewHomesButNotMovingAnOldOne() {
        Player alex = alexIn("world");
        setHome(alex, "one");
        setHome(alex, "two");

        assertThat(setHome(alex, "three")).isEqualTo(SetHomeResult.LIMIT_REACHED);
        assertThat(setHome(alex, "two")).isEqualTo(SetHomeResult.SUCCESS);
        assertThat(db.getHomeCount(ALEX)).isEqualTo(2);
    }

    @Test
    void theLimitCountsTheCachedHomesOfAnOnlinePlayer() {
        online.add(ALEX);
        homes.loadBlocking(ALEX);
        Player alex = alexIn("world");

        setHome(alex, "one");
        setHome(alex, "two");

        assertThat(setHome(alex, "three")).isEqualTo(SetHomeResult.LIMIT_REACHED);
        assertThat(homes.cachedHomes(ALEX).orElseThrow()).extracting(Home::getName).containsExactly("one", "two");
    }

    @Test
    void blockedWorldsAreRefusedOnlyWhenBlockingIsOn() {
        assertThat(setHome(alexIn("resource_world"), "mine")).isEqualTo(SetHomeResult.SUCCESS);

        when(plugin.getConfigUtil().isWorldBlockingEnabled()).thenReturn(true);
        homes.deleteAllHomes(ALEX);

        assertThat(setHome(alexIn("resource_world"), "mine")).isEqualTo(SetHomeResult.WORLD_BLOCKED);
        assertThat(db.getHomeCount(ALEX)).isZero();
        assertThat(setHome(alexIn("world"), "base")).isEqualTo(SetHomeResult.SUCCESS);
    }

    @Test
    void namesLongerThan32CharactersAreRefused() {
        assertThat(setHome(alexIn("world"), "a".repeat(33))).isEqualTo(SetHomeResult.NAME_TOO_LONG);
        assertThat(setHome(alexIn("world"), "a".repeat(32))).isEqualTo(SetHomeResult.SUCCESS);
        assertThat(db.getHomeCount(ALEX)).isEqualTo(1);
    }

    //
    // Home limit
    //

    @Test
    void withoutPermissionsTheConfigDefaultApplies() {
        assertThat(homes.getMaxHomes(alexIn("world"))).isEqualTo(2);
    }

    @Test
    void theHighestNumberedPermissionWins() {
        permissions.add("fluxhomes.homes.5");
        permissions.add("fluxhomes.homes.12");

        assertThat(homes.getMaxHomes(alexIn("world"))).isEqualTo(12);
    }

    @Test
    void aNumberAbove100CountsToo() {
        permissions.add("fluxhomes.homes.150");
        permissions.add("fluxhomes.homes.20");

        assertThat(homes.getMaxHomes(alexIn("world"))).isEqualTo(150);
    }

    @Test
    void aNegatedOrNonNumberPermissionDoesNotCount() {
        permissions.add("fluxhomes.homes.4");
        negated.add("fluxhomes.homes.50");
        permissions.add("fluxhomes.homes.lots");

        assertThat(homes.getMaxHomes(alexIn("world"))).isEqualTo(4);
    }

    /** Operators have every numbered permission; unlimited must still win (problem fluxhomes-op-limited-to-100-homes). */
    @Test
    void unlimitedWinsOverNumberedPermissions() {
        permissions.add("fluxhomes.homes.unlimited");
        permissions.add("fluxhomes.homes.100");
        permissions.add("fluxhomes.homes.3");

        assertThat(homes.getMaxHomes(alexIn("world"))).isEqualTo(Integer.MAX_VALUE);
    }

    //
    // Reading and deleting
    //

    @Test
    void deleteHomeSaysWhetherTheHomeExisted() {
        setHome(alexIn("world"), "base");

        assertThat(deleteHome(ALEX, "base")).isTrue();
        assertThat(deleteHome(ALEX, "base")).isFalse();
        assertThat(getHome(ALEX, "base")).isNull();
        assertThat(db.getHome(ALEX, "base")).isNull();
    }

    @Test
    void deleteAllHomesEmptiesCacheAndDatabase() {
        online.add(ALEX);
        homes.loadBlocking(ALEX);
        setHome(alexIn("world"), "one");
        setHome(alexIn("world"), "two");

        homes.deleteAllHomes(ALEX);

        assertThat(homes.cachedHomes(ALEX).orElseThrow()).isEmpty();
        assertThat(db.getHomeCount(ALEX)).isZero();
    }

    @Test
    void homesOfAnOfflinePlayerComeFromTheDatabaseAndAreNotKept() {
        db.saveHome(new Home(ALEX, "base", "world", 1, 2, 3, 0f, 0f));

        assertThat(getHome(ALEX, "base")).isNotNull();
        assertThat(homes.cachedHomes(ALEX)).isEmpty();
    }

    //
    // Cache
    //

    @Test
    void homesAreLoadedAtLoginAndDroppedAtQuit() {
        db.saveHome(new Home(ALEX, "base", "world", 1, 2, 3, 0f, 0f));

        homes.loadBlocking(ALEX);
        assertThat(homes.cachedHome(ALEX, "base")).isPresent();

        homes.unload(ALEX);
        assertThat(homes.cachedHomes(ALEX)).isEmpty();
    }

    @Test
    void anOnlinePlayersHomesAreCachedOnFirstUse() {
        online.add(ALEX);
        db.saveHome(new Home(ALEX, "base", "world", 1, 2, 3, 0f, 0f));

        homes.loadInBackground(ALEX);

        assertThat(homes.cachedHome(ALEX, "base")).isPresent();
    }

    @Test
    void writesReachTheDatabaseInOrder() throws Exception {
        ExecutorService databaseThread = Executors.newSingleThreadExecutor();
        HomeManager threaded = new HomeManager(plugin, db, new CooldownManager(), databaseThread, Runnable::run, online::contains);
        online.add(ALEX);
        threaded.loadBlocking(ALEX);
        Player alex = alexIn("world");

        for (int i = 0; i < 50; i++) {
            threaded.setHome(alex, "base", result -> { });
            threaded.deleteHome(ALEX, "base", deleted -> { });
        }
        threaded.setHome(alex, "last", result -> { });
        databaseThread.shutdown();
        assertThat(databaseThread.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(db.getHomes(ALEX)).extracting(Home::getName).containsExactly("last");
    }

    //
    // EssentialsX import
    //

    private void essentialsUser(UUID player, String yaml) throws IOException {
        Path userdata = Files.createDirectories(pluginsFolder.resolve("Essentials/userdata"));
        Files.writeString(userdata.resolve(player + ".yml"), yaml);
    }

    private int importEssentials() {
        AtomicReference<Integer> count = new AtomicReference<>();
        homes.importFromEssentialsX(count::set);
        return count.get();
    }

    @Test
    void importWithoutEssentialsReportsMinusOne() {
        assertThat(importEssentials()).isEqualTo(-1);
    }

    @Test
    void importOfTheOlderFormatTakesTheWorldName() throws IOException {
        essentialsUser(ALEX, """
                homes:
                  base:
                    world: world
                    x: 1.5
                    y: 64.0
                    z: -2.5
                    yaw: 90.0
                    pitch: 0.0
                  farm:
                    world: world_nether
                    x: 10.0
                    y: 70.0
                    z: 20.0
                    yaw: 0.0
                    pitch: 0.0
                """);

        assertThat(importEssentials()).isEqualTo(2);
        assertThat(db.getHome(ALEX, "base")).usingRecursiveComparison()
                .isEqualTo(new Home(ALEX, "base", "world", 1.5, 64, -2.5, 90f, 0f));
        assertThat(db.getHome(ALEX, "farm").getWorld()).isEqualTo("world_nether");
    }

    @Test
    void importOfTheNewerFormatUsesTheWorldName() throws IOException {
        essentialsUser(ALEX, """
                homes:
                  base:
                    world: 5f3c1a2b-0000-4000-8000-00000000c0de
                    world-name: survival
                    x: 1.0
                    y: 64.0
                    z: 2.0
                    yaw: 0.0
                    pitch: 0.0
                """);

        assertThat(importEssentials()).isEqualTo(1);
        assertThat(db.getHome(ALEX, "base").getWorld()).isEqualTo("survival");
    }

    @Test
    void importPrefersTheLoadedWorldWithThatUuid() throws IOException {
        UUID worldId = UUID.fromString("5f3c1a2b-0000-4000-8000-00000000beef");
        World renamed = mock(World.class);
        when(renamed.getName()).thenReturn("survival_2");
        when(TestPlugin.bukkitServer().getWorld(worldId)).thenReturn(renamed);
        essentialsUser(ALEX, """
                homes:
                  base:
                    world: 5f3c1a2b-0000-4000-8000-00000000beef
                    world-name: survival
                    x: 1.0
                    y: 64.0
                    z: 2.0
                """);

        importEssentials();

        assertThat(db.getHome(ALEX, "base").getWorld()).isEqualTo("survival_2");
    }

    @Test
    void importKeepsExistingHomesAndSkipsWhatItCannotUse() throws IOException {
        db.saveHome(new Home(ALEX, "base", "world", 99, 99, 99, 0f, 0f));
        essentialsUser(ALEX, """
                homes:
                  base:
                    world: world
                    x: 1.0
                  nowhere:
                    x: 1.0
                  %s:
                    world: world
                    x: 1.0
                """.formatted("a".repeat(33)));
        Files.writeString(pluginsFolder.resolve("Essentials/userdata/not-a-uuid.yml"), "homes: {}");

        assertThat(importEssentials()).isZero();
        assertThat(db.getHome(ALEX, "base").getX()).isEqualTo(99);
        assertThat(db.getHomeCount(ALEX)).isEqualTo(1);
    }

    @Test
    void importedNamesAreLowercasedLikeWhatPlayersType() throws IOException {
        essentialsUser(ALEX, """
                homes:
                  MyBase:
                    world: world
                    x: 1.0
                """);

        importEssentials();

        assertThat(db.getHome(ALEX, "mybase")).isNotNull();
    }

    @Test
    void importRefreshesTheHomesOfLoadedPlayers() throws IOException {
        online.add(ALEX);
        homes.loadBlocking(ALEX);
        essentialsUser(ALEX, """
                homes:
                  base:
                    world: world
                    x: 1.0
                """);

        importEssentials();

        assertThat(homes.cachedHome(ALEX, "base")).isPresent();
    }
}
