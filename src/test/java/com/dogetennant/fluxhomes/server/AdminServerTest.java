package com.dogetennant.fluxhomes.server;

import com.dogetennant.fluxhomes.database.SQLiteManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.dogetennant.fluxhomes.server.TestServer.messages;
import static org.assertj.core.api.Assertions.assertThat;

/** /homesadmin on a running server: other players' homes, the import, reload. */
class AdminServerTest {

    private TestServer mc;
    private World world;
    private PlayerMock admin;
    private PlayerMock alex;

    @BeforeEach
    void setUp() {
        mc = TestServer.start();
        world = mc.server.getWorld("world");
        admin = mc.admin();
        admin.setLocation(new Location(world, 0.5, 64, 0.5));
        alex = mc.player("Alex");
        alex.setLocation(new Location(world, 100.5, 64, 100.5));
        alex.performCommand("sethome base");
        alex.performCommand("sethome mine");
        mc.settle();
        messages(admin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anAdminListsAndTeleportsToTheHomesOfAPlayerOnline() {
        admin.performCommand("ha list Alex");
        mc.settle();
        admin.performCommand("ha Alex mine");
        mc.settle();

        assertThat(messages(admin)).containsExactly("[FluxHomes] Homes for Alex:", " - base", " - mine",
                "[FluxHomes] Teleported to Alex's home mine.");
        assertThat(admin.getLocation().getX()).isEqualTo(100.5);
    }

    @Test
    void anAdminDeletesAHomeOfAPlayerWhoIsOffline() {
        alex.disconnect();

        admin.performCommand("ha del Alex base");
        mc.settle();
        admin.performCommand("ha list Alex");
        mc.settle();

        assertThat(messages(admin)).containsExactly("[FluxHomes] Deleted home base for Alex.",
                "[FluxHomes] Homes for Alex:", " - mine");
    }

    @Test
    void clearingTheHomesOfAPlayerOnlineEmptiesWhatTheySee() {
        admin.performCommand("ha clear Alex");
        mc.settle();
        messages(alex);

        alex.performCommand("homes");

        assertThat(messages(admin)).containsExactly("[FluxHomes] Cleared all homes for Alex.");
        assertThat(messages(alex)).containsExactly("[FluxHomes] You have no homes set.");
    }

    @Test
    void theConsoleGetsPlainAnswers() {
        ConsoleCommandSenderMock console = mc.server.getConsoleSender();
        mc.server.dispatchCommand(console, "ha list Alex");
        mc.settle();

        assertThat(console.nextMessage()).isEqualTo("Homes for Alex:");
        assertThat(console.nextMessage()).isEqualTo(" - base");
    }

    @Test
    void theEssentialsImportAddsHomesAPlayerOnlineSeesAtOnce() throws IOException {
        Path userdata = mc.server.getPluginsFolder().toPath().resolve("Essentials/userdata");
        Files.createDirectories(userdata);
        Files.writeString(userdata.resolve(alex.getUniqueId() + ".yml"), """
                homes:
                  Farm:
                    world: world
                    x: 5.5
                    y: 70.0
                    z: 5.5
                    yaw: 0.0
                    pitch: 0.0
                  base:
                    world: world
                    x: 1.0
                    y: 1.0
                    z: 1.0
                    yaw: 0.0
                    pitch: 0.0
                """);

        admin.performCommand("ha import essentialsx");
        mc.settle();
        messages(alex);
        alex.performCommand("homes");

        assertThat(messages(admin)).containsExactly("[FluxHomes] Starting EssentialsX import, please wait...",
                "[FluxHomes] Import complete! Imported 1 homes.");
        assertThat(messages(alex)).containsExactly("[FluxHomes] Your homes:", " - base", " - farm", " - mine");
    }

    /** docs/problems/fluxhomes-reload-silent-about-storage-changes.md */
    @Test
    void aReloadAfterAStorageChangeSaysItNeedsARestart() throws IOException {
        admin.performCommand("ha reload");
        assertThat(messages(admin)).containsExactly("[FluxHomes] Configuration and translations reloaded.");
        mc.replace("config.yml", "storage-type: sqlite", "storage-type: mysql");

        admin.performCommand("ha reload");

        assertThat(messages(admin)).containsExactly("[FluxHomes] Configuration and translations reloaded.",
                "[FluxHomes] The storage settings (storage-type, table-prefix, mysql) changed. FluxHomes keeps"
                        + " using the old storage until the server restarts.");
        assertThat(mc.plugin.getDatabase()).isInstanceOf(SQLiteManager.class);
    }
}
