package com.comze_instancelabs.bowbash;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

	/** How often (in ticks) each arena's lobby/countdown logic re-checks itself. */
	private static final int LOBBY_TICK_INTERVAL = 5;

	private ArenaManager arenaManager;
	private BowBashScoreboard scoreboardManager;
	private Kit kit;
	private Stats stats;

	@Override
	public void onEnable() {
		saveDefaultConfig();

		// So the respawn-delay mechanic (Arena#beginRespawnDelay) works the same with or without
		// Recored also installed: no item drops on death (we reissue a kit anyway), and the death
		// screen doesn't auto-respawn before Arena's own delayed, click-free respawn gets to it.
		for (World world : Bukkit.getWorlds()) {
			world.setGameRule(GameRules.KEEP_INVENTORY, true);
			world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
		}

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

		Bukkit.getScheduler().runTaskTimer(this, () -> arenaManager.tickAll(LOBBY_TICK_INTERVAL), LOBBY_TICK_INTERVAL, LOBBY_TICK_INTERVAL);

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			new BowBashPlaceholders(this).register();
			getLogger().info("PlaceholderAPI found - %bowbash_...% placeholders registered.");
		}

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
