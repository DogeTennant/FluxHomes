package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.models.Home;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SQLiteManager extends DatabaseManager {

    private final FluxHomes plugin;
    private Connection connection;

    public SQLiteManager(FluxHomes plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTable();
            plugin.getLogger().info("Connected to SQLite database.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to SQLite: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close SQLite connection: " + e.getMessage());
        }
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS homes (
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
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public void saveHome(Home home) {
        String sql = """
                INSERT INTO homes (owner_uuid, name, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner_uuid, name) DO UPDATE SET
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    yaw = excluded.yaw,
                    pitch = excluded.pitch;
                """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, home.getOwnerUUID().toString());
            stmt.setString(2, home.getName());
            stmt.setString(3, home.getWorld());
            stmt.setDouble(4, home.getX());
            stmt.setDouble(5, home.getY());
            stmt.setDouble(6, home.getZ());
            stmt.setFloat(7, home.getYaw());
            stmt.setFloat(8, home.getPitch());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save home: " + e.getMessage());
        }
    }

    @Override
    public void deleteHome(UUID ownerUUID, String name) {
        String sql = "DELETE FROM homes WHERE owner_uuid = ? AND name = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete home: " + e.getMessage());
        }
    }

    @Override
    public void deleteAllHomes(UUID ownerUUID) {
        String sql = "DELETE FROM homes WHERE owner_uuid = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete all homes: " + e.getMessage());
        }
    }

    @Override
    public Home getHome(UUID ownerUUID, String name) {
        String sql = "SELECT * FROM homes WHERE owner_uuid = ? AND name = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            stmt.setString(2, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapHome(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get home: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<Home> getHomes(UUID ownerUUID) {
        String sql = "SELECT * FROM homes WHERE owner_uuid = ?;";
        List<Home> homes = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                homes.add(mapHome(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get homes: " + e.getMessage());
        }
        return homes;
    }

    @Override
    public int getHomeCount(UUID ownerUUID) {
        String sql = "SELECT COUNT(*) FROM homes WHERE owner_uuid = ?;";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to count homes: " + e.getMessage());
        }
        return 0;
    }

    private Home mapHome(ResultSet rs) throws SQLException {
        return new Home(
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch")
        );
    }
}