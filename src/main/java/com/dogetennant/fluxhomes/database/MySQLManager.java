package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.models.Home;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** MySQL / MariaDB from the {@code mysql} section of the config. */
public class MySQLManager extends DatabaseManager {

    public MySQLManager(FluxHomes plugin) {
        super(plugin);
    }

    @Override
    public void initialize() throws Exception {
        String host = plugin.getConfig().getString("mysql.host", "localhost");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database", "minecraft");

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&characterEncoding=UTF-8&useUnicode=true");
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikari.setUsername(plugin.getConfig().getString("mysql.username", "root"));
        hikari.setPassword(plugin.getConfig().getString("mysql.password", ""));
        // all database work runs on one thread; the second connection covers a reconnect
        hikari.setMaximumPoolSize(2);
        hikari.setConnectionTimeout(10000);
        // well below MySQL's wait_timeout, so the server never closes a connection we still use
        hikari.setMaxLifetime(1800000);
        hikari.setKeepaliveTime(300000);
        hikari.setPoolName("FluxHomes-MySQL");

        dataSource = new HikariDataSource(hikari);
        createTable();
        plugin.getLogger().info("Connected to MySQL database.");
    }

    @Override
    protected String quotedTable() {
        return "`" + table + "`";
    }

    @Override
    protected void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS `%s` (
                    owner_uuid VARCHAR(36) NOT NULL,
                    name VARCHAR(32) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT NOT NULL,
                    pitch FLOAT NOT NULL,
                    PRIMARY KEY (owner_uuid, name)
                );
                """.formatted(table);
        try (Connection con = connection(); Statement stmt = con.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public void saveHome(Home home) {
        String sql = """
                INSERT INTO `%s` (owner_uuid, name, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    world = VALUES(world),
                    x = VALUES(x),
                    y = VALUES(y),
                    z = VALUES(z),
                    yaw = VALUES(yaw),
                    pitch = VALUES(pitch);
                """.formatted(table);
        try (Connection con = connection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            bindHome(stmt, home);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save home: " + e.getMessage());
        }
    }
}
