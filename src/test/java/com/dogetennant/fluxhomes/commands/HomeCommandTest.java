package com.dogetennant.fluxhomes.commands;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.TestPlugin;
import com.dogetennant.fluxhomes.database.SQLiteManager;
import com.dogetennant.fluxhomes.managers.CooldownManager;
import com.dogetennant.fluxhomes.managers.HomeManager;
import com.dogetennant.fluxhomes.models.Home;
import com.dogetennant.fluxhomes.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code /sethome}, {@code /home} and {@code /delhome} with its confirmation. */
class HomeCommandTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");

    @TempDir
    Path dataFolder;
    @TempDir
    Path pluginsFolder;

    private final Server server = TestPlugin.bukkitServer();
    private final MessageUtil messages = mock(MessageUtil.class);
    /** Delayed tasks (the confirmation timeouts), in the order they were scheduled. */
    private final List<Runnable> later = new ArrayList<>();
    private final List<Integer> cancelled = new ArrayList<>();
    private FluxHomes plugin;
    private SQLiteManager db;
    private HomeManager homes;
    private HomeCommand command;
    private Player alex;

    @BeforeEach
    void setUp() throws Exception {
        plugin = TestPlugin.mockPlugin(dataFolder, pluginsFolder);
        when(plugin.getMessageUtil()).thenReturn(messages);
        when(plugin.getConfigUtil().getMaxHomes("default")).thenReturn(5);
        when(plugin.getConfigUtil().isConfirmDeletionEnabled()).thenReturn(true);
        when(plugin.getConfigUtil().getConfirmDeletionTimeout()).thenReturn(30);
        db = new SQLiteManager(plugin);
        db.initialize();
        homes = new HomeManager(plugin, db, new CooldownManager(), Runnable::run, Runnable::run, uuid -> true);
        when(plugin.getHomeManager()).thenReturn(homes);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(call -> {
            later.add(call.getArgument(1));
            return task(later.size());
        });
        doAnswer(call -> cancelled.add(call.<Integer>getArgument(0)))
                .when(scheduler).cancelTask(anyInt());
        when(server.getScheduler()).thenReturn(scheduler);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        alex = mock(Player.class);
        when(alex.getUniqueId()).thenReturn(ALEX);
        when(alex.isOnline()).thenReturn(true);
        when(alex.getWorld()).thenReturn(world);
        when(alex.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        when(alex.hasPermission(anyString())).thenAnswer(call -> !call.getArgument(0, String.class)
                .startsWith("fluxhomes.homes.") && call.getArgument(0, String.class).startsWith("fluxhomes."));

        command = new HomeCommand(plugin);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private BukkitTask task(int id) {
        return new BukkitTask() {
            @Override public int getTaskId() { return id; }
            @Override public Plugin getOwner() { return null; }
            @Override public boolean isSync() { return true; }
            @Override public boolean isCancelled() { return cancelled.contains(id); }
            @Override public void cancel() { cancelled.add(id); }
        };
    }

    private void run(String name, String... args) {
        Command cmd = mock(Command.class);
        when(cmd.getName()).thenReturn(name);
        command.onCommand(alex, cmd, name, args);
    }

    /** Runs the n-th delayed task (1 = the first scheduled), unless it was cancelled. */
    private void runLater(int n) {
        if (!cancelled.contains(n)) later.get(n - 1).run();
    }

    private Optional<Home> home(String name) {
        return homes.cachedHome(ALEX, name);
    }

    @Test
    void sethomeWithoutANameSetsHomeAndNamesIgnoreCase() {
        run("sethome");
        run("sethome", "Base");

        assertThat(home("home")).isPresent();
        assertThat(home("base")).isPresent();
        verify(messages).send(alex, "home-set", "{home}", "base");
    }

    @Test
    void theFirstDelhomeAsksAndTheSecondDeletes() {
        run("sethome", "base");

        run("delhome", "base");
        verify(messages).send(alex, "home-delete-confirm", "{home}", "base");
        assertThat(home("base")).isPresent();

        run("delhome", "BASE");
        verify(messages).send(alex, "home-deleted", "{home}", "base");
        assertThat(home("base")).isEmpty();
    }

    @Test
    void theConfirmationRunsOut() {
        run("sethome", "base");
        run("delhome", "base");

        runLater(1);
        run("delhome", "base");

        verify(messages).send(alex, "home-delete-cancelled");
        verify(messages, times(2)).send(alex, "home-delete-confirm", "{home}", "base");
        assertThat(home("base")).isPresent();
    }

    @Test
    void anotherHomeNeedsItsOwnConfirmation() {
        run("sethome", "base");
        run("sethome", "farm");

        run("delhome", "base");
        run("delhome", "farm");
        run("delhome", "base");

        assertThat(home("base")).isPresent();
        assertThat(home("farm")).isPresent();
        verify(messages, never()).send(alex, "home-deleted", "{home}", "base");
    }

    @Test
    void anOldTimeoutDoesNotCutANewConfirmationShort() {
        run("sethome", "base");
        run("delhome", "base");
        run("delhome", "base");                           // deleted; its timeout is still pending
        run("sethome", "base");
        run("delhome", "base");                           // asks again, with a new timeout

        runLater(1);                                      // the first timeout ends
        run("delhome", "base");

        verify(messages, never()).send(alex, "home-delete-cancelled");
        assertThat(home("base")).isEmpty();
    }

    @Test
    void withoutConfirmationDelhomeDeletesAtOnce() {
        when(plugin.getConfigUtil().isConfirmDeletionEnabled()).thenReturn(false);
        run("sethome", "base");

        run("delhome", "base");

        assertThat(home("base")).isEmpty();
        verify(messages).send(alex, "home-deleted", "{home}", "base");
    }

    @Test
    void deletingAHomeThatDoesNotExistSaysSo() {
        run("delhome", "nowhere");

        verify(messages).send(alex, "home-not-found", "{home}", "nowhere");
        assertThat(later).isEmpty();
    }
}
