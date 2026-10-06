package com.dogetennant.fluxhomes.managers;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.TestPlugin;
import com.dogetennant.fluxhomes.database.SQLiteManager;
import com.dogetennant.fluxhomes.models.Home;
import com.dogetennant.fluxhomes.models.SetHomeResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Home limits, blocked worlds and the EssentialsX import, on a real SQLite database. */
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

    @BeforeAll
    static void scheduler() {
        TestPlugin.installImmediateScheduler();
    }

    @BeforeEach
    void setUp() {
        plugin = TestPlugin.mockPlugin(dataFolder, pluginsFolder);
        when(plugin.getConfigUtil().getMaxHomes("default")).thenReturn(2);
        when(plugin.getConfigUtil().getBlockedWorlds()).thenReturn(List.of("resource_world"));
        db = new SQLiteManager(plugin);
        db.initialize();
        homes = new HomeManager(plugin, db, new CooldownManager());
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
        return player;
    }

    //
    // /sethome
    //

    @Test
    void setHomeStoresWhereThePlayerStands() {
        assertThat(homes.setHome(alexIn("world"), "base")).isEqualTo(SetHomeResult.SUCCESS);

        Home base = db.getHome(ALEX, "base");
        assertThat(base).usingRecursiveComparison().isEqualTo(new Home(ALEX, "base", "world", 10.5, 70, -3.25, 45f, 10f));
    }

    @Test
    void theLimitStopsNewHomesButNotMovingAnOldOne() {
        Player alex = alexIn("world");
        homes.setHome(alex, "one");
        homes.setHome(alex, "two");

        assertThat(homes.setHome(alex, "three")).isEqualTo(SetHomeResult.LIMIT_REACHED);
        assertThat(homes.setHome(alex, "two")).isEqualTo(SetHomeResult.SUCCESS);
        assertThat(db.getHomeCount(ALEX)).isEqualTo(2);
    }

    @Test
    void blockedWorldsAreRefusedOnlyWhenBlockingIsOn() {
        assertThat(homes.setHome(alexIn("resource_world"), "mine")).isEqualTo(SetHomeResult.SUCCESS);

        when(plugin.getConfigUtil().isWorldBlockingEnabled()).thenReturn(true);
        db.deleteAllHomes(ALEX);

        assertThat(homes.setHome(alexIn("resource_world"), "mine")).isEqualTo(SetHomeResult.WORLD_BLOCKED);
        assertThat(db.getHomeCount(ALEX)).isZero();
        assertThat(homes.setHome(alexIn("world"), "base")).isEqualTo(SetHomeResult.SUCCESS);
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
    void unlimitedOnlyCountsWithoutANumberedPermission() {
        permissions.add("fluxhomes.homes.unlimited");
        assertThat(homes.getMaxHomes(alexIn("world"))).isEqualTo(Integer.MAX_VALUE);

        permissions.add("fluxhomes.homes.3");
        assertThat(homes.getMaxHomes(alexIn("world"))).isEqualTo(3);
    }

    //
    // /delhome
    //

    @Test
    void deleteHomeSaysWhetherTheHomeExisted() {
        homes.setHome(alexIn("world"), "base");

        assertThat(homes.deleteHome(ALEX, "base")).isTrue();
        assertThat(homes.deleteHome(ALEX, "base")).isFalse();
        assertThat(homes.getHome(ALEX, "base")).isNull();
    }

    //
    // EssentialsX import
    //

    private Path essentialsUser(UUID player, String yaml) throws IOException {
        Path userdata = Files.createDirectories(pluginsFolder.resolve("Essentials/userdata"));
        return Files.writeString(userdata.resolve(player + ".yml"), yaml);
    }

    @Test
    void importWithoutEssentialsReportsMinusOne() {
        assertThat(homes.importFromEssentialsX()).isEqualTo(-1);
    }

    @Test
    void importTakesEveryHomeWithItsWorldName() throws IOException {
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

        assertThat(homes.importFromEssentialsX()).isEqualTo(2);
        assertThat(db.getHome(ALEX, "base")).usingRecursiveComparison()
                .isEqualTo(new Home(ALEX, "base", "world", 1.5, 64, -2.5, 90f, 0f));
        assertThat(db.getHome(ALEX, "farm").getWorld()).isEqualTo("world_nether");
    }

    @Test
    void importKeepsHomesThatAlreadyExistAndSkipsBadFiles() throws IOException {
        db.saveHome(new Home(ALEX, "base", "world", 99, 99, 99, 0f, 0f));
        essentialsUser(ALEX, """
                homes:
                  base:
                    world: world
                    x: 1.0
                    y: 2.0
                    z: 3.0
                  nowhere:
                    x: 1.0
                """);
        Files.writeString(pluginsFolder.resolve("Essentials/userdata/not-a-uuid.yml"), "homes: {}");

        assertThat(homes.importFromEssentialsX()).isZero();
        assertThat(db.getHome(ALEX, "base").getX()).isEqualTo(99);
        assertThat(db.getHome(ALEX, "nowhere")).isNull();
    }

    /** Documents current behaviour: see docs/problems/fluxhomes-essentials-import-world-uuid.md. */
    @Test
    void importOfTheNewerEssentialsFormatStoresTheWorldUuid() throws IOException {
        essentialsUser(ALEX, """
                homes:
                  base:
                    world: 5f3c1a2b-0000-4000-8000-00000000c0de
                    world-name: world
                    x: 1.0
                    y: 64.0
                    z: 2.0
                    yaw: 0.0
                    pitch: 0.0
                """);

        assertThat(homes.importFromEssentialsX()).isEqualTo(1);
        assertThat(db.getHome(ALEX, "base").getWorld()).isEqualTo("5f3c1a2b-0000-4000-8000-00000000c0de");
    }
}
