package com.dogetennant.fluxhomes.managers;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.TestPlugin;
import com.dogetennant.fluxhomes.database.DatabaseManager;
import com.dogetennant.fluxhomes.models.Home;
import com.dogetennant.fluxhomes.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code /home}: the warmup, the cooldown and the teleport itself. */
class HomeTeleportTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");

    @TempDir
    Path dataFolder;
    @TempDir
    Path pluginsFolder;

    private final Server server = TestPlugin.bukkitServer();
    private final World world = mock(World.class);
    private final MessageUtil messages = mock(MessageUtil.class);
    /** Running repeating tasks by id. */
    private final Map<Integer, Runnable> tasks = new LinkedHashMap<>();
    private final AtomicReference<Location> standing = new AtomicReference<>();
    private int nextTaskId = 1;
    private FluxHomes plugin;
    private Player alex;
    private HomeManager homes;

    @BeforeEach
    void setUp() {
        plugin = TestPlugin.mockPlugin(dataFolder, pluginsFolder);
        when(plugin.getMessageUtil()).thenReturn(messages);
        when(plugin.getConfigUtil().isCooldownEnabled()).thenReturn(true);
        when(plugin.getConfigUtil().getCooldownSeconds()).thenReturn(5);
        when(plugin.getConfigUtil().isWarmupEnabled()).thenReturn(true);
        when(plugin.getConfigUtil().getWarmupSeconds()).thenReturn(3);

        when(world.getName()).thenReturn("world");
        when(server.getWorld("world")).thenReturn(world);
        when(server.getWorld("gone")).thenReturn(null);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            int id = nextTaskId++;
            tasks.put(id, call.getArgument(1));
            return task(id);
        });
        doAnswer(call -> tasks.remove(call.<Integer>getArgument(0))).when(scheduler).cancelTask(anyInt());
        when(server.getScheduler()).thenReturn(scheduler);

        alex = mock(Player.class);
        when(alex.getUniqueId()).thenReturn(ALEX);
        when(alex.isOnline()).thenReturn(true);
        standing.set(new Location(world, 0.5, 64, 0.5));
        when(alex.getLocation()).thenAnswer(call -> standing.get());
        when(alex.teleport(any(Location.class))).thenAnswer(call -> {
            standing.set(call.getArgument(0));
            return true;
        });

        homes = new HomeManager(plugin, mock(DatabaseManager.class), new CooldownManager(),
                Runnable::run, Runnable::run, uuid -> true);
    }

    private static BukkitTask task(int id) {
        return new BukkitTask() {
            @Override public int getTaskId() { return id; }
            @Override public Plugin getOwner() { return null; }
            @Override public boolean isSync() { return true; }
            @Override public boolean isCancelled() { return false; }
            @Override public void cancel() { }
        };
    }

    /** Runs every running repeating task {@code times} times, one server tick each. */
    private void tick(int times) {
        for (int i = 0; i < times; i++) {
            for (Runnable task : List.copyOf(tasks.values())) task.run();
        }
    }

    private static Home home(String name, String world, double x) {
        return new Home(ALEX, name, world, x, 70, 5, 90f, 0f);
    }

    @Test
    void withoutAWarmupTheTeleportIsAtOnceAndStartsTheCooldown() {
        when(plugin.getConfigUtil().isWarmupEnabled()).thenReturn(false);

        homes.teleportHome(alex, home("base", "world", 100));
        homes.teleportHome(alex, home("farm", "world", 200));

        verify(alex).teleport(new Location(world, 100, 70, 5, 90f, 0f));
        verify(messages).send(alex, "teleport-success", "{home}", "base");
        verify(messages).send(alex, "cooldown", "{seconds}", "5");
        verify(alex, times(1)).teleport(any(Location.class));
    }

    @Test
    void theWarmupTeleportsAfterItsSecondsStandingStill() {
        homes.teleportHome(alex, home("base", "world", 100));
        verify(messages).send(alex, "warmup-start", "{seconds}", "3");

        tick(59);
        verify(alex, never()).teleport(any(Location.class));
        tick(1);
        verify(alex).teleport(new Location(world, 100, 70, 5, 90f, 0f));
        assertThat(tasks).isEmpty();
    }

    @Test
    void lookingAroundOrShiftingInsideTheBlockDoesNotCancel() {
        homes.teleportHome(alex, home("base", "world", 100));
        tick(10);
        standing.set(new Location(world, 0.9, 64.2, 0.1, 180f, 30f));

        tick(50);

        verify(alex).teleport(any(Location.class));
    }

    @Test
    void movingToAnotherBlockCancelsTheWarmup() {
        homes.teleportHome(alex, home("base", "world", 100));
        tick(10);
        standing.set(new Location(world, 1.5, 64, 0.5));

        tick(60);

        verify(messages).send(alex, "warmup-cancelled");
        verify(alex, never()).teleport(any(Location.class));
        assertThat(tasks).isEmpty();
    }

    @Test
    void leavingTheServerCancelsTheWarmup() {
        homes.teleportHome(alex, home("base", "world", 100));
        when(alex.isOnline()).thenReturn(false);

        tick(60);

        verify(alex, never()).teleport(any(Location.class));
        assertThat(tasks).isEmpty();
    }

    @Test
    void aHomeInAMissingWorldIsNotUsedAndStartsNoCooldown() {
        when(plugin.getConfigUtil().isWarmupEnabled()).thenReturn(false);

        homes.teleportHome(alex, home("old", "gone", 100));
        homes.teleportHome(alex, home("base", "world", 100));

        verify(messages).send(alex, "world-not-found");
        verify(alex).teleport(new Location(world, 100, 70, 5, 90f, 0f));
        verify(messages, never()).send(eq(alex), eq("cooldown"), anyString(), anyString());
    }

    @Test
    void aNewHomeCommandDuringTheWarmupReplacesTheOldOne() {
        homes.teleportHome(alex, home("base", "world", 100));
        tick(20);
        homes.teleportHome(alex, home("farm", "world", 200));

        tick(60);

        verify(alex, never()).teleport(new Location(world, 100, 70, 5, 90f, 0f));
        verify(alex).teleport(new Location(world, 200, 70, 5, 90f, 0f));
        verify(messages, never()).send(alex, "warmup-cancelled");
        assertThat(tasks).isEmpty();
    }
}
