LootableBodies is a Minecraft Bukkit plugin designed to enhance the gameplay experience by introducing a looting mechanic for player deaths. When a player dies, their items are stored in a chest at the location of their death, allowing for other players to loot the chest unless restricted by specific permissions. This plugin adds an immersive and strategic element to player versus player (PvP) encounters and survival gameplay.

Features
Death Chests:

Upon a player's death, their inventory items are automatically transferred to a chest at their death location.
The player's inventory is cleared, simulating the looting process.
Owner Protection:

Only the owner of the chest (the player who died) can open and loot their chest.
Other players attempting to open the chest will be denied access and receive a message indicating they are not allowed to loot it.
Chest Removal:

Chests have a configurable despawn time (default is 600 seconds).
After the despawn time expires, the chest is automatically removed along with its contents.
If the owner breaks the chest before it despawns, it is removed from the plugin's tracking and the database.
Database Integration:

Chests and their owner information are stored in a SQLite database.
On server restart, the plugin reloads all chests from the database and schedules their removal if their despawn time has not yet expired.
Block Placement Protection:

Prevents players from placing lava near loot chests, protecting the chest and its contents from being destroyed.
Configurable Options:

Configurable delay for chest removal (body-removal-delay), allowing server admins to set how long chests remain in the world before being automatically removed.
Installation
Download the plugin jar file from the releases section.
Place the jar file into the plugins directory of your Bukkit/Spigot server.
Start or Restart your server to generate the configuration file and database.
Configure the plugin by editing the config.yml file located in the plugins/LootableBodies directory.
Configuration
The config.yml file includes the following configurable option:

yaml
Kopier kode
body-removal-delay: 600  # Time in seconds before loot chests are automatically removed
Usage
On Player Death: A chest is created at the death location containing the player's inventory items.
Opening Chests: Only the owner of the chest can open it. Other players will receive a message denying access.
Chest Removal: Chests are automatically removed after the configured delay, or immediately if broken by the owner.
Block Placement: Placing lava near loot chests is prevented to protect the chest.
Development
Main Class (Main.java)
Manages chest creation, owner protection, chest removal, and block event handling.
Uses Bukkit events to respond to player deaths, inventory interactions, block breaking, and block placing.
Interacts with the DatabaseManager class to store and retrieve chest information.
DatabaseManager Class (DatabaseManager.java)
Manages the SQLite database connection and CRUD operations for chest data.
Ensures the chests table exists and stores chest locations, owner UUIDs, and despawn times.
License
This plugin is licensed under the MIT License. See the LICENSE file for more details.
