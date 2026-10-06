package com.dogetennant.fluxhomes;

import com.dogetennant.fluxhomes.database.DatabaseManager;
import com.dogetennant.fluxhomes.database.MySQLManager;
import com.dogetennant.fluxhomes.utils.ConfigUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test doubles: a mocked plugin with what the tested classes use, and an H2 "MySQL". */
public final class TestPlugin {

    private static Server server;

    private TestPlugin() {
    }

    /**
     * A plugin whose data folder is {@code dataFolder}, whose server's plugins folder is
     * {@code pluginsFolder}, whose tables use {@code prefix}, and whose {@link ConfigUtil} is a
     * mock (stub what a test needs).
     */
    public static FluxHomes mockPlugin(Path dataFolder, Path pluginsFolder, String prefix) {
        Server pluginServer = mock(Server.class);
        when(pluginServer.getPluginsFolder()).thenReturn(pluginsFolder.toFile());

        FluxHomes plugin = mock(FluxHomes.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FluxHomes-test"));
        when(plugin.getServer()).thenReturn(pluginServer);
        ConfigUtil config = mock(ConfigUtil.class);
        when(config.getTablePrefix()).thenReturn(prefix);
        when(plugin.getConfigUtil()).thenReturn(config);
        return plugin;
    }

    public static FluxHomes mockPlugin(Path dataFolder, Path pluginsFolder) {
        return mockPlugin(dataFolder, pluginsFolder, "");
    }

    /**
     * A MySQLManager on a fresh in-memory H2 database in MySQL mode, with its table created.
     * {@code initialize()} is skipped because it opens a {@code jdbc:mysql:} URL; the pool is set
     * through reflection instead.
     */
    public static MySQLManager h2MySql(FluxHomes plugin) throws Exception {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:h2:mem:fh" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        hikari.setMaximumPoolSize(2);

        MySQLManager manager = new MySQLManager(plugin);
        Field dataSource = DatabaseManager.class.getDeclaredField("dataSource");
        dataSource.setAccessible(true);
        dataSource.set(manager, new HikariDataSource(hikari));
        Method createTable = MySQLManager.class.getDeclaredMethod("createTable");
        createTable.setAccessible(true);
        createTable.invoke(manager);
        return manager;
    }

    /**
     * The mocked Bukkit server behind {@code Bukkit.getWorld(...)} and friends, installed once per
     * test JVM. Stub what a test needs (e.g. {@code getWorld(UUID)}).
     */
    public static synchronized Server bukkitServer() {
        if (server == null) {
            server = mock(Server.class);
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
        return server;
    }
}
