package com.dogetennant.fluxhomes.server;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;

import static com.dogetennant.fluxhomes.server.TestServer.messages;
import static org.assertj.core.api.Assertions.assertThat;

/** What players do with their homes, on a running server with the real config and database. */
class HomesServerTest {

    private TestServer mc;
    private World world;

    @BeforeEach
    void setUp() {
        mc = TestServer.start();
        world = mc.server.getWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock standingAt(String name, double x, double y, double z) {
        PlayerMock player = mc.player(name);
        player.setLocation(new Location(world, x, y, z));
        return player;
    }

    @Test
    void aHomeIsSetAndTheWarmupTakesThePlayerThere() {
        PlayerMock alex = standingAt("Alex", 100.5, 64, -20.5);
        alex.performCommand("sethome base");
        alex.setLocation(new Location(world, 0.5, 64, 0.5));
        messages(alex);

        alex.performCommand("home base");
        assertThat(messages(alex)).containsExactly("[FluxHomes] Teleporting in 3 seconds, don't move.");
        mc.waitOutTheWarmup();

        assertThat(alex.getLocation().getX()).isEqualTo(100.5);
        assertThat(alex.getLocation().getZ()).isEqualTo(-20.5);
        assertThat(messages(alex)).containsExactly("[FluxHomes] Teleported to base.");
    }

    @Test
    void homesAreListedAndKeptOverARestart() {
        PlayerMock alex = standingAt("Alex", 10, 64, 10);
        alex.performCommand("sethome Base");
        alex.performCommand("sethome mine");
        mc.settle();

        mc.restart();
        mc.settle(); // the homes of players online are loaded again
        messages(alex);
        alex.performCommand("homes");

        assertThat(messages(alex)).containsExactly("[FluxHomes] Your homes:", " - base", " - mine");
        assertThat(mc.server.getCommandTabComplete(alex, "home m")).containsExactly("mine");
    }

    @Test
    void homesAreReadFromTheDatabaseWhenThePlayerJoinsAgain() {
        PlayerMock alex = standingAt("Alex", 10, 64, 10);
        alex.performCommand("sethome base");
        mc.settle();
        alex.disconnect();

        alex.reconnect();
        messages(alex);
        alex.performCommand("homes");

        assertThat(messages(alex)).containsExactly("[FluxHomes] Your homes:", " - base");
    }

    @Test
    void theFourthHomeIsRefusedWithTheDefaultLimit() {
        PlayerMock alex = standingAt("Alex", 10, 64, 10);
        alex.performCommand("sethome a");
        alex.performCommand("sethome b");
        alex.performCommand("sethome c");
        messages(alex);

        alex.performCommand("sethome d");
        alex.performCommand("sethome a"); // moving an old one is fine

        assertThat(messages(alex)).containsExactly(
                "[FluxHomes] You have reached your home limit of 3.",
                "[FluxHomes] Home a has been set.");
    }

    @Test
    void delhomeAsksFirstAndDeletesOnTheSecondTime() {
        PlayerMock alex = standingAt("Alex", 10, 64, 10);
        alex.performCommand("sethome base");
        messages(alex);

        alex.performCommand("delhome base");
        alex.performCommand("delhome base");
        alex.performCommand("home base");

        assertThat(messages(alex)).containsExactly(
                "[FluxHomes] Are you sure you want to delete base? Run the command again to confirm.",
                "[FluxHomes] Home base has been deleted.",
                "[FluxHomes] Home base not found.");
    }

    @Test
    void movingToAnotherBlockDuringTheWarmupCancelsTheTeleport() {
        PlayerMock alex = standingAt("Alex", 100, 64, 100);
        alex.performCommand("sethome base");
        alex.setLocation(new Location(world, 0.5, 64, 0.5));
        alex.performCommand("home base");
        messages(alex);

        alex.setLocation(new Location(world, 3.5, 64, 0.5));
        mc.waitOutTheWarmup();

        assertThat(alex.getLocation().getX()).isEqualTo(3.5);
        assertThat(messages(alex)).containsExactly("[FluxHomes] Teleportation cancelled because you moved.");
    }

    @Test
    void theCooldownStopsASecondTeleportRightAfter() {
        PlayerMock alex = standingAt("Alex", 100, 64, 100);
        alex.performCommand("sethome base");
        alex.performCommand("home base");
        mc.waitOutTheWarmup();
        messages(alex);

        alex.performCommand("home base");

        assertThat(messages(alex)).singleElement().asString().startsWith("[FluxHomes] You must wait ");
    }

    @Test
    void homesCannotBeSetInABlockedWorld() throws IOException {
        mc.replace("config.yml", "blocked-worlds:\n  enabled: false", "blocked-worlds:\n  enabled: true");
        mc.replace("config.yml", "    - world_example", "    - world");
        PlayerMock admin = mc.admin();
        admin.performCommand("ha reload");
        PlayerMock alex = standingAt("Alex", 10, 64, 10);

        alex.performCommand("sethome base");

        assertThat(messages(alex)).containsExactly("[FluxHomes] You cannot set homes in this world.");
    }

    @Test
    void withRespawnAtHomeADeadPlayerRespawnsAtTheHomeNamedHome() throws IOException {
        mc.replace("config.yml", "respawn-at-home:\n  enabled: false", "respawn-at-home:\n  enabled: true");
        mc.admin().performCommand("ha reload");
        PlayerMock alex = standingAt("Alex", 100.5, 64, 100.5);
        alex.performCommand("sethome");

        PlayerRespawnEvent respawn = respawn(alex, PlayerRespawnEvent.RespawnReason.DEATH);

        assertThat(respawn.getRespawnLocation().getX()).isEqualTo(100.5);
    }

    /** docs/problems/fluxhomes-respawn-at-home-after-the-end.md */
    @Test
    void withRespawnAtHomeLeavingTheEndKeepsTheNormalRespawn() throws IOException {
        mc.replace("config.yml", "respawn-at-home:\n  enabled: false", "respawn-at-home:\n  enabled: true");
        mc.admin().performCommand("ha reload");
        PlayerMock alex = standingAt("Alex", 100.5, 64, 100.5);
        alex.performCommand("sethome");

        PlayerRespawnEvent respawn = respawn(alex, PlayerRespawnEvent.RespawnReason.END_PORTAL);

        assertThat(respawn.getRespawnLocation()).isEqualTo(world.getSpawnLocation());
    }

    /** The respawn the server fires, to the world spawn. */
    private PlayerRespawnEvent respawn(PlayerMock player, PlayerRespawnEvent.RespawnReason reason) {
        PlayerRespawnEvent respawn = new PlayerRespawnEvent(player, world.getSpawnLocation(),
                false, false, false, reason);
        mc.server.getPluginManager().callEvent(respawn);
        return respawn;
    }

    /** docs/problems/fluxhomes-stopped-teleport-reported-as-done.md */
    @Test
    void aTeleportAnotherPluginStopsSaysSoAndStartsNoCooldown() {
        PlayerMock alex = standingAt("Alex", 100.5, 64, 100.5);
        alex.performCommand("sethome base");
        alex.setLocation(new Location(world, 0.5, 64, 0.5));
        stopEveryTeleport(); // a combat-log or region plugin
        alex.performCommand("home base");
        messages(alex);

        mc.waitOutTheWarmup();
        alex.performCommand("home base");

        assertThat(alex.getLocation().getX()).isEqualTo(0.5); // still where they were
        assertThat(messages(alex)).containsExactly("[FluxHomes] The teleport to base was stopped.",
                "[FluxHomes] Teleporting in 3 seconds, don't move."); // no cooldown
    }

    /** docs/problems/fluxhomes-stopped-teleport-reported-as-done.md */
    @Test
    void anAdminTeleportAnotherPluginStopsSaysSo() {
        PlayerMock alex = standingAt("Alex", 100.5, 64, 100.5);
        alex.performCommand("sethome base");
        PlayerMock admin = mc.admin();
        admin.setLocation(new Location(world, 0.5, 64, 0.5));
        stopEveryTeleport();
        messages(admin);

        admin.performCommand("ha tp Alex base");
        mc.settle();

        assertThat(admin.getLocation().getX()).isEqualTo(0.5);
        assertThat(messages(admin)).containsExactly("[FluxHomes] The teleport to Alex's home base was stopped.");
    }

    private void stopEveryTeleport() {
        Plugin other = MockBukkit.createMockPlugin("Guard");
        mc.server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onTeleport(PlayerTeleportEvent event) {
                event.setCancelled(true);
            }
        }, other);
    }
}
