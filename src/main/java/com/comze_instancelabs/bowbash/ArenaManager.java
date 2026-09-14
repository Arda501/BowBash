package com.comze_instancelabs.bowbash;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Loads/saves arenas.yml and holds the live {@link Arena} instances.
 */
public class ArenaManager {

	private final Main plugin;
	private final File file;
	private final Map<String, Arena> arenas = new LinkedHashMap<>();

	public ArenaManager(Main plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "arenas.yml");
	}

	public void load() {
		if (!file.exists()) {
			return;
		}
		YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
		if (!config.isConfigurationSection("arenas")) {
			return;
		}
		for (String name : config.getConfigurationSection("arenas").getKeys(false)) {
			String base = "arenas." + name + ".";
			Arena arena = new Arena(plugin, name);
			arena.setWorldName(config.getString(base + "world"));
			arena.setLobby(stringToLoc(config.getString(base + "lobby")));
			arena.setSpawn(Team.RED, stringToLoc(config.getString(base + "spawn_red")));
			arena.setSpawn(Team.BLUE, stringToLoc(config.getString(base + "spawn_blue")));
			arena.setPos1(stringToLoc(config.getString(base + "pos1")));
			arena.setPos2(stringToLoc(config.getString(base + "pos2")));
			arena.setDefaultScore(config.getInt(base + "default_score", arena.getDefaultScore()));
			arenas.put(name.toLowerCase(), arena);
		}
		plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
	}

	public void save() {
		YamlConfiguration config = new YamlConfiguration();
		for (Arena arena : arenas.values()) {
			String base = "arenas." + arena.getName() + ".";
			config.set(base + "world", arena.getWorldName());
			config.set(base + "lobby", locToString(arena.getLobby()));
			config.set(base + "spawn_red", locToString(arena.getSpawn(Team.RED)));
			config.set(base + "spawn_blue", locToString(arena.getSpawn(Team.BLUE)));
			config.set(base + "pos1", locToString(arena.getPos1()));
			config.set(base + "pos2", locToString(arena.getPos2()));
			config.set(base + "default_score", arena.getDefaultScore());
		}
		try {
			config.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("Could not save arenas.yml: " + e.getMessage());
		}
	}

	public Arena create(String name, World world) {
		Arena arena = new Arena(plugin, name);
		arena.setWorldName(world.getName());
		arenas.put(name.toLowerCase(), arena);
		save();
		return arena;
	}

	public boolean remove(String name) {
		Arena arena = arenas.remove(name.toLowerCase());
		if (arena == null) {
			return false;
		}
		arena.forceStop();
		save();
		return true;
	}

	public Arena get(String name) {
		return arenas.get(name.toLowerCase());
	}

	public Collection<Arena> getAll() {
		return arenas.values();
	}

	public Optional<Arena> findArenaOf(Player p) {
		UUID id = p.getUniqueId();
		return arenas.values().stream().filter(a -> a.getPlayers().contains(id)).findFirst();
	}

	public void stopAll() {
		for (Arena arena : arenas.values()) {
			arena.forceStop();
		}
	}

	/** Drives every arena's lobby/countdown logic; see {@link Arena#tick(int)}. */
	public void tickAll(int tickIntervalTicks) {
		for (Arena arena : arenas.values()) {
			arena.tick(tickIntervalTicks);
		}
	}

	static String locToString(Location loc) {
		if (loc == null || loc.getWorld() == null) {
			return null;
		}
		return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
	}

	static Location stringToLoc(String s) {
		if (s == null) {
			return null;
		}
		String[] parts = s.split(",");
		if (parts.length < 6) {
			return null;
		}
		World world = Bukkit.getWorld(parts[0]);
		if (world == null) {
			return null;
		}
		return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
	}
}
