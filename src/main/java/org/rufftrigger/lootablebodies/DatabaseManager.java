package org.rufftrigger.lootablebodies;

import java.sql.Connection;
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
                            "despawn_time BIGINT NOT NULL, " +
                            "xp INT NOT NULL)")) {
                statement.executeUpdate();
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

    public void addChest(String location, UUID ownerUuid, long despawnTime) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO chests (location, owner_uuid, despawn_time, xp) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, location);
            statement.setString(2, ownerUuid.toString());
            statement.setLong(3, despawnTime);
            statement.setInt(4, 0); // Initialize XP to 0
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addChestXP(String location, int xp) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE chests SET xp = ? WHERE location = ?")) {
            statement.setInt(1, xp);
            statement.setString(2, location);
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

    public int getChestXP(String location) {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT xp FROM chests WHERE location = ?");
            statement.setString(1, location);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getInt("xp");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
