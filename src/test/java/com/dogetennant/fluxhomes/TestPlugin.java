package com.dogetennant.fluxhomes;

import com.dogetennant.fluxhomes.database.MySQLManager;
import com.dogetennant.fluxhomes.utils.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test doubles: a mocked plugin with what the tested classes use, and an H2 "MySQL". */
public final class TestPlugin {

    private TestPlugin() {
    }

    /**
     * A plugin whose data folder is {@code dataFolder}, whose server's plugins folder is
     * {@code pluginsFolder}, and whose {@link ConfigUtil} is a mock (stub what a test needs).
     */
    public static FluxHomes mockPlugin(Path dataFolder, Path pluginsFolder) {
        Server server = mock(Server.class);
        when(server.getPluginsFolder()).thenReturn(pluginsFolder.toFile());

        FluxHomes plugin = mock(FluxHomes.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FluxHomes-test"));
        when(plugin.getServer()).thenReturn(server);
        ConfigUtil config = mock(ConfigUtil.class);
        when(plugin.getConfigUtil()).thenReturn(config);
        return plugin;
    }

    /**
     * A MySQLManager on a fresh in-memory H2 database in MySQL mode, with its table created.
     * {@code initialize()} is skipped because it opens a {@code jdbc:mysql:} URL; the private
     * connection is set through reflection instead.
     */
    public static MySQLManager h2MySql(FluxHomes plugin) throws Exception {
        Connection h2 = DriverManager.getConnection("jdbc:h2:mem:fh" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        MySQLManager manager = new MySQLManager(plugin);
        Field connection = MySQLManager.class.getDeclaredField("connection");
        connection.setAccessible(true);
        connection.set(manager, h2);
        Method createTable = MySQLManager.class.getDeclaredMethod("createTable");
        createTable.setAccessible(true);
        createTable.invoke(manager);
        return manager;
    }

    /**
     * Installs a mocked Bukkit server (once per test JVM) whose scheduler runs async tasks
     * immediately on the calling thread, so the async saves can be asserted on directly.
     */
    public static synchronized void installImmediateScheduler() {
        if (Bukkit.getServer() != null) {
            return;
        }
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenAnswer(call -> {
            call.getArgument(1, Runnable.class).run();
            return mock(BukkitTask.class);
        });
        Server server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getLogger()).thenReturn(Logger.getLogger("server-test"));
        // Bukkit.setServer() would ask Paper for server build info, which only a real server has
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot install the test server", e);
        }
    }
}
