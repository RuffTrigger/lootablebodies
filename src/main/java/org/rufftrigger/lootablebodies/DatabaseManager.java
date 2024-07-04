package org.rufftrigger.lootablebodies;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseManager {

    private final Main plugin;
    private Connection connection;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
    }

    public void openConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/chests.db");

            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS chests (" +
                            "location TEXT PRIMARY KEY, " +
                            "owner_uuid TEXT NOT NULL, " +
                            "despawn_time BIGINT NOT NULL)")) {
                statement.executeUpdate();
            }

            // Check if 'xp' column exists, and add it if it doesn't
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet rs = meta.getColumns(null, null, "chests", "xp");
            if (!rs.next()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "ALTER TABLE chests ADD COLUMN xp INT NOT NULL DEFAULT 0")) {
                    statement.executeUpdate();
                }
            }

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addChest(String location, UUID ownerUuid, long despawnTime, int xp) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO chests (location, owner_uuid, despawn_time, xp) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, location);
            statement.setString(2, ownerUuid.toString());
            statement.setLong(3, despawnTime);
            statement.setInt(4, xp);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeChest(String location) {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM chests WHERE location = ?")) {
            statement.setString(1, location);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ResultSet getChests() {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM chests");
            return statement.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
