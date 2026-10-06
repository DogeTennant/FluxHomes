package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.models.Home;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite file {@code data.db} in the plugin folder. */
public class SQLiteManager extends DatabaseManager {

    public SQLiteManager(FluxHomes plugin) {
        super(plugin);
    }

    @Override
    public void initialize() throws Exception {
        File folder = plugin.getDataFolder();
        folder.mkdirs();

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:sqlite:" + new File(folder, "data.db").getAbsolutePath());
        hikari.setDriverClassName("org.sqlite.JDBC");
        // SQLite has one writer at a time
        hikari.setMaximumPoolSize(1);
        hikari.setConnectionTimeout(30000);
        hikari.setPoolName("FluxHomes-SQLite");

        dataSource = new HikariDataSource(hikari);
        createTable();
        plugin.getLogger().info("Connected to SQLite database.");
    }

    @Override
    protected String quotedTable() {
        return "\"" + table + "\"";
    }

    @Override
    protected void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS "%s" (
                    owner_uuid TEXT NOT NULL,
                    name TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    PRIMARY KEY (owner_uuid, name)
                );
                """.formatted(table);
        try (Connection con = connection(); Statement stmt = con.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute(sql);
        }
    }

    @Override
    public void saveHome(Home home) {
        String sql = """
                INSERT INTO "%s" (owner_uuid, name, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner_uuid, name) DO UPDATE SET
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    yaw = excluded.yaw,
                    pitch = excluded.pitch;
                """.formatted(table);
        try (Connection con = connection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            bindHome(stmt, home);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save home: " + e.getMessage());
        }
    }
}
