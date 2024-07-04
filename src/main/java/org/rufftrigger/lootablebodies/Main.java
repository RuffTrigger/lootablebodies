package org.rufftrigger.lootablebodies;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {

    // Map to store chest locations and their owners
    private final Map<Location, UUID> chestOwners = new HashMap<>();
    private FileConfiguration config;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // Ensure the config file exists with default values
        saveDefaultConfig();
        config = getConfig();

        databaseManager = new DatabaseManager(this);
        databaseManager.openConnection();

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("LootableBodies has been enabled!");

        loadChestsFromDatabase();
    }

    @Override
    public void onDisable() {
        getLogger().info("LootableBodies has been disabled!");
        databaseManager.closeConnection();
    }

    private void loadChestsFromDatabase() {
        ResultSet rs = databaseManager.getChests();
        try {
            while (rs != null && rs.next()) {
                String locStr = rs.getString("location");
                UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
                long despawnTime = rs.getLong("despawn_time");
                int playerLevel = rs.getInt("player_level");
                double playerXP = rs.getDouble("player_xp");

                Location location = deserializeLocation(locStr);
                if (location.getBlock().getType() == Material.CHEST) {
                    chestOwners.put(location, ownerUUID);
                    protectChest(location, despawnTime);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ItemStack[] items = player.getInventory().getContents();

        // Store player's level and XP
        int playerLevel = player.getLevel();
        double playerXP = player.getExp();

        // Clear player's inventory to simulate looting
        player.getInventory().clear();

        // Get the location of the player's death
        Location deathLocation = player.getLocation();
        Block block = deathLocation.getBlock();
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();

        // Transfer items to the chest
        for (ItemStack item : items) {
            if (item != null) {
                chest.getBlockInventory().addItem(item);
            }
        }
        // Prevent XP orbs from spawning
        event.setDroppedExp(0);
        event.getDrops().clear();
        // Store chest owner, level, and XP
        chestOwners.put(chest.getLocation(), player.getUniqueId());
        int delay = config.getInt("body-removal-delay", 600);  // Default to 600 seconds if not specified in config
        long despawnTime = System.currentTimeMillis() + delay * 1000L;
        databaseManager.addChest(serializeLocation(chest.getLocation()), player.getUniqueId(), despawnTime, playerLevel, playerXP);

        protectChest(chest.getLocation(), despawnTime);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (chest != null && chest.getBlock().getType() == Material.CHEST) {
                    chest.getBlock().setType(Material.AIR);
                    chestOwners.remove(chest.getLocation());
                    databaseManager.removeChest(serializeLocation(chest.getLocation()));
                }
            }
        }.runTaskLater(this, delay * 20L);  // Convert seconds to ticks (20 ticks = 1 second)
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.CHEST) {
            Location location = event.getInventory().getLocation();
            if (location != null && chestOwners.containsKey(location)) {
                UUID ownerUUID = chestOwners.get(location);
                Player player = (Player) event.getPlayer();
                if (!player.getUniqueId().equals(ownerUUID)) {
                    event.setCancelled(true);
                    player.sendMessage("You are not allowed to loot this chest.");
                } else {
                    // Restore player's level and XP
                    int playerLevel = 0;
                    double playerXP = 0.0;

                    // Retrieve from database
                    ResultSet rs = databaseManager.getChests();
                    try {
                        while (rs != null && rs.next()) {
                            String locStr = rs.getString("location");
                            if (locStr.equals(serializeLocation(location))) {
                                playerLevel = rs.getInt("player_level");
                                playerXP = rs.getDouble("player_xp");
                                break;
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    // Set player's level and XP
                    player.setLevel(playerLevel);
                    player.setExp((float) playerXP);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        if (chestOwners.containsKey(location)) {
            UUID ownerUUID = chestOwners.get(location);
            Player player = event.getPlayer();
            if (!player.getUniqueId().equals(ownerUUID)) {
                event.setCancelled(true);
                player.sendMessage("You are not allowed to break this loot chest.");
            } else {
                // Remove chest from tracking if owner breaks it
                chestOwners.remove(location);
                databaseManager.removeChest(serializeLocation(location));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.CHEST) {
            Location location = event.getInventory().getLocation();
            if (location != null && chestOwners.containsKey(location)) {
                UUID ownerUUID = chestOwners.get(location);
                Player player = (Player) event.getWhoClicked();
                if (!player.getUniqueId().equals(ownerUUID)) {
                    event.setCancelled(true);
                    player.sendMessage("You are not allowed to loot this chest.");
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.LAVA || block.getType() == Material.LAVA_BUCKET ||
                block.getType() == Material.FIRE || block.getType() == Material.TNT) {
            // Check surrounding blocks
            for (Block adjacentBlock : getAdjacentBlocks(block)) {
                if (chestOwners.containsKey(adjacentBlock.getLocation())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("You cannot place lava, fire, or TNT near a loot chest.");
                    break;
                }
            }
        }
    }

    private Block[] getAdjacentBlocks(Block block) {
        return new Block[]{
                block.getRelative(BlockFace.NORTH),
                block.getRelative(BlockFace.SOUTH),
                block.getRelative(BlockFace.EAST),
                block.getRelative(BlockFace.WEST),
                block.getRelative(BlockFace.UP),
                block.getRelative(BlockFace.DOWN)
        };
    }

    private void protectChest(Location location, long despawnTime) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (chestOwners.containsKey(location)) {
                    Chest chest = (Chest) location.getBlock().getState();
                    if (chest != null && chest.getBlock().getType() == Material.CHEST) {
                        // Check for lava, fire, or TNT near the chest location
                        if (isBlockNearby(location, Material.LAVA) || isBlockNearby(location, Material.FIRE) || isBlockNearby(location, Material.TNT)) {
                            // Schedule another protection check
                            protectChest(location, despawnTime);
                        } else {
                            // Chest is safe, no lava, fire, or TNT nearby
                            return;
                        }
                    }
                }
            }
        }.runTaskLater(this, 20L); // Check every 1 second (20 ticks)
    }

    private boolean isBlockNearby(Location location, Material material) {
        for (BlockFace face : BlockFace.values()) {
            Block relative = location.getBlock().getRelative(face);
            if (relative.getType() == material) {
                return true;
            }
        }
        return false;
    }

    // Serialize location to string
    private String serializeLocation(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    // Deserialize location from string
    private Location deserializeLocation(String locStr) {
        String[] parts = locStr.split(",");
        return new Location(Bukkit.getWorld(parts[0]), Double.parseDouble(parts[1]) + 0.5, Double.parseDouble(parts[2]), Double.parseDouble(parts[3]) + 0.5);
    }
}
