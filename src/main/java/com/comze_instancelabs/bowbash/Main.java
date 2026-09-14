package com.comze_instancelabs.bowbash;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

	private ArenaManager arenaManager;
	private BowBashScoreboard scoreboardManager;
	private Kit kit;
	private Stats stats;

	@Override
	public void onEnable() {
		saveDefaultConfig();

		this.arenaManager = new ArenaManager(this);
		this.scoreboardManager = new BowBashScoreboard();
		this.kit = new Kit(this);
		this.stats = new Stats(this);

		arenaManager.load();

		Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
		Bukkit.getPluginManager().registerEvents(new SignListener(this), this);

		BowBashCommand command = new BowBashCommand(this);
		getCommand("bb").setExecutor(command);
		getCommand("bb").setTabCompleter(command);

		getLogger().info("BowBash enabled.");
	}

	@Override
	public void onDisable() {
		if (arenaManager != null) {
			arenaManager.stopAll();
		}
	}

	public ArenaManager getArenaManager() {
		return arenaManager;
	}

	public BowBashScoreboard getScoreboardManager() {
		return scoreboardManager;
	}

	public Kit getKit() {
		return kit;
	}

	public Stats getStats() {
		return stats;
	}
}
