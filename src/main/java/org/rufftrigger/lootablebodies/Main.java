package org.rufftrigger.lootablebodies;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

                Location location = deserializeLocation(locStr);
                if (location.getBlock().getType() == Material.CHEST) {
                    chestOwners.put(location, ownerUUID);
                    long delay = (despawnTime - System.currentTimeMillis()) / 1000;
                    if (delay > 0) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                location.getBlock().setType(Material.AIR);
                                chestOwners.remove(location);
                                databaseManager.removeChest(locStr);
                            }
                        }.runTaskLater(this, delay * 20L); // Convert seconds to ticks (20 ticks = 1 second)
                    } else {
                        location.getBlock().setType(Material.AIR);
                        chestOwners.remove(location);
                        databaseManager.removeChest(locStr);
                    }
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

        // Capture player's current XP
        int xp = player.getTotalExperience();

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

        // Store captured XP in the database
        databaseManager.addChestXP(serializeLocation(chest.getLocation()), xp);

        // Prevent XP orbs from spawning at the death location
        event.setDroppedExp(0);

        // Store chest owner and despawn time in the database
        chestOwners.put(chest.getLocation(), player.getUniqueId());
        long delay = config.getInt("body-removal-delay", 600);  // Default to 600 seconds if not specified in config
        long despawnTime = System.currentTimeMillis() + delay * 1000L;
        databaseManager.addChest(serializeLocation(chest.getLocation()), player.getUniqueId(), despawnTime);

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
                    // Give stored XP to the player
                    int xp = databaseManager.getChestXP(serializeLocation(location));
                    player.giveExp(xp);
                    databaseManager.removeChestXP(serializeLocation(location));
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
        if (block.getType() == Material.LAVA || block.getType() == Material.LAVA_BUCKET) {
            // Check surrounding blocks
            for (Block adjacentBlock : getAdjacentBlocks(block)) {
                if (chestOwners.containsKey(adjacentBlock.getLocation())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("You cannot place lava near a loot chest.");
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

    // Serialize location to string
    private String serializeLocation(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    // Deserialize location from string
    private Location deserializeLocation(String locStr) {
        String[] parts = locStr.split(",");
        World world = Bukkit.getWorld(parts[0]);
        double x = Double.parseDouble(parts[1]) + 0.5;
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]) + 0.5;
        return new Location(world, x, y, z);
    }
}
