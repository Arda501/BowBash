package com.comze_instancelabs.bowbash;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Tiny persisted all-time stat: total blocks destroyed with a bow, across every game. Replaces the
 * original's full achievement/stats framework, which needed the dead MinigamesLib to run.
 */
public class Stats {

	private final Main plugin;
	private final File file;
	private final YamlConfiguration config;

	public Stats(Main plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "stats.yml");
		this.config = YamlConfiguration.loadConfiguration(file);
	}

	/** Adds this game's broken-block count to the player's all-time total and saves it. */
	public void flushGameBlocks(UUID player, int gameCount) {
		if (gameCount <= 0) {
			return;
		}
		String path = player.toString() + ".blocks_destroyed";
		int before = config.getInt(path, 0);
		int after = before + gameCount;
		config.set(path, after);
		save();

		if (before < 1000 && after >= 1000) {
			org.bukkit.entity.Player p = Bukkit.getPlayer(player);
			if (p != null) {
				p.sendMessage(ChatColor.GOLD + "Achievement: destroyed 1000 blocks with your bow, all-time!");
			}
		}
	}

	public int getAllTimeBlocksDestroyed(UUID player) {
		return config.getInt(player.toString() + ".blocks_destroyed", 0);
	}

	private void save() {
		try {
			config.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("Could not save stats.yml: " + e.getMessage());
		}
	}
}
