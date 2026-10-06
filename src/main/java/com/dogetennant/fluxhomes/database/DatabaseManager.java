package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.models.Home;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage of homes in {@code <table-prefix>homes}. Every method blocks on the database, so callers
 * run them off the main thread ({@link com.dogetennant.fluxhomes.managers.HomeManager} uses one
 * database thread). Only the upsert and the table definition differ between the dialects.
 */
public abstract class DatabaseManager {

    protected final FluxHomes plugin;
    /** Connection pool, opened by {@link #initialize()}. */
    protected HikariDataSource dataSource;
    /** {@code <table-prefix>homes}. */
    protected final String table;

    protected DatabaseManager(FluxHomes plugin) {
        this.plugin = plugin;
        String prefix = plugin.getConfigUtil().getTablePrefix();
        this.table = (prefix == null ? "" : prefix) + "homes";
    }

    /** Opens the pool and creates the table. Throws when the database cannot be reached. */
    public abstract void initialize() throws Exception;

    /** The table name quoted for this dialect. */
    protected abstract String quotedTable();

    /** Creates the table if it does not exist. */
    protected abstract void createTable() throws SQLException;

    /** Inserts the home, or moves it when the owner already has a home with that name. */
    public abstract void saveHome(Home home);

    /** Closes the pool. */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    protected Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    protected void bindHome(PreparedStatement stmt, Home home) throws SQLException {
        stmt.setString(1, home.getOwnerUUID().toString());
        stmt.setString(2, home.getName());
        stmt.setString(3, home.getWorld());
        stmt.setDouble(4, home.getX());
        stmt.setDouble(5, home.getY());
        stmt.setDouble(6, home.getZ());
        stmt.setFloat(7, home.getYaw());
        stmt.setFloat(8, home.getPitch());
    }

    public void deleteHome(UUID ownerUUID, String name) {
        String sql = "DELETE FROM " + quotedTable() + " WHERE owner_uuid = ? AND name = ?;";
        try (Connection con = connection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete home: " + e.getMessage());
        }
    }

    public void deleteAllHomes(UUID ownerUUID) {
        String sql = "DELETE FROM " + quotedTable() + " WHERE owner_uuid = ?;";
        try (Connection con = connection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete all homes: " + e.getMessage());
        }
    }

    public Home getHome(UUID ownerUUID, String name) {
        String sql = "SELECT * FROM " + quotedTable() + " WHERE owner_uuid = ? AND name = ?;";
        try (Connection con = connection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            stmt.setString(2, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapHome(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get home: " + e.getMessage());
        }
        return null;
    }

    public List<Home> getHomes(UUID ownerUUID) {
        String sql = "SELECT * FROM " + quotedTable() + " WHERE owner_uuid = ?;";
        List<Home> homes = new ArrayList<>();
        try (Connection con = connection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    homes.add(mapHome(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get homes: " + e.getMessage());
        }
        return homes;
    }

    public int getHomeCount(UUID ownerUUID) {
        String sql = "SELECT COUNT(*) FROM " + quotedTable() + " WHERE owner_uuid = ?;";
        try (Connection con = connection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to count homes: " + e.getMessage());
        }
        return 0;
    }

    private static Home mapHome(ResultSet rs) throws SQLException {
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
