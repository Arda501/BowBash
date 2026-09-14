package com.comze_instancelabs.bowbash;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * A single BowBash arena: its configured locations plus the live state of whatever match (if any)
 * is currently running in it.
 */
public class Arena {

	private final Main plugin;
	private final String name;

	private String worldName;
	private Location lobby;
	private final Location[] spawns = new Location[2]; // 0 = red, 1 = blue

	private int minPlayers;
	private int maxPlayers;
	private int defaultScore;

	private ArenaState state = ArenaState.WAITING;

	private final LinkedHashSet<UUID> players = new LinkedHashSet<>();
	private final Map<UUID, Team> playerTeam = new LinkedHashMap<>();
	private final Map<UUID, Integer> blocksBrokenThisGame = new LinkedHashMap<>();
	private final Set<Team> teamWasAtOne = EnumSet.noneOf(Team.class);
	private boolean nextJoinRed = true;

	private int redScore;
	private int blueScore;

	private BukkitTask countdownTask;
	private BukkitTask powerupTask;
	private int countdownSecondsRemaining;

	private final BlockRegen regen = new BlockRegen();

	public Arena(Main plugin, String name) {
		this.plugin = plugin;
		this.name = name;
		this.minPlayers = plugin.getConfig().getInt("config.default_min_players", 4);
		this.maxPlayers = plugin.getConfig().getInt("config.default_max_players", 8);
		this.defaultScore = plugin.getConfig().getInt("config.default_score", 4);
	}

	// --- basic getters/setters -------------------------------------------------

	public String getName() {
		return name;
	}

	public String getWorldName() {
		return worldName;
	}

	public void setWorldName(String worldName) {
		this.worldName = worldName;
	}

	public Location getLobby() {
		return lobby;
	}

	public void setLobby(Location lobby) {
		this.lobby = lobby;
	}

	public Location getSpawn(Team team) {
		return spawns[team.spawnIndex()];
	}

	public void setSpawn(Team team, Location location) {
		spawns[team.spawnIndex()] = location;
	}

	public boolean isFullyConfigured() {
		return lobby != null && spawns[0] != null && spawns[1] != null;
	}

	public int getMinPlayers() {
		return minPlayers;
	}

	public void setMinPlayers(int minPlayers) {
		this.minPlayers = minPlayers;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public void setMaxPlayers(int maxPlayers) {
		this.maxPlayers = maxPlayers;
	}

	public int getDefaultScore() {
		return defaultScore;
	}

	public void setDefaultScore(int defaultScore) {
		this.defaultScore = defaultScore;
	}

	public ArenaState getState() {
		return state;
	}

	public Set<UUID> getPlayers() {
		return players;
	}

	public Team getTeam(UUID uuid) {
		return playerTeam.get(uuid);
	}

	public int getRedScore() {
		return redScore;
	}

	public int getBlueScore() {
		return blueScore;
	}

	public int getScore(Team team) {
		return team == Team.RED ? redScore : blueScore;
	}

	public BlockRegen getRegen() {
		return regen;
	}

	public boolean isInGame() {
		return state == ArenaState.INGAME;
	}

	// --- join / leave ------------------------------------------------------

	public boolean isFull() {
		return players.size() >= maxPlayers;
	}

	public String join(Player p) {
		if (!isFullyConfigured()) {
			return ChatColor.RED + "This arena isn't fully set up yet.";
		}
		if (state == ArenaState.INGAME || state == ArenaState.ENDING) {
			return ChatColor.RED + "That game has already started.";
		}
		if (isFull()) {
			return ChatColor.RED + "That arena is full.";
		}
		if (players.contains(p.getUniqueId())) {
			return ChatColor.RED + "You're already in that arena.";
		}

		players.add(p.getUniqueId());
		Team team = nextJoinRed ? Team.RED : Team.BLUE;
		nextJoinRed = !nextJoinRed;
		playerTeam.put(p.getUniqueId(), team);
		blocksBrokenThisGame.put(p.getUniqueId(), 0);

		p.teleport(lobby);
		Kit.clearAndGiveLobbyState(p);
		broadcast(team.chatColor() + p.getName() + ChatColor.GRAY + " joined (" + players.size() + "/" + maxPlayers + ")");
		plugin.getScoreboardManager().updateLobby(this);

		if (state == ArenaState.WAITING && players.size() >= minPlayers) {
			startCountdown();
		}
		return null;
	}

	public void leave(Player p, boolean silent) {
		UUID id = p.getUniqueId();
		if (!players.contains(id)) {
			return;
		}
		Team team = playerTeam.get(id);

		if (state == ArenaState.INGAME) {
			plugin.getStats().flushGameBlocks(id, blocksBrokenThisGame.getOrDefault(id, 0));
		}

		players.remove(id);
		playerTeam.remove(id);
		blocksBrokenThisGame.remove(id);
		plugin.getKit().removeArmor(p);
		p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

		if (!silent) {
			p.teleport(p.getWorld().getSpawnLocation());
			if (team != null) {
				broadcast(team.chatColor() + p.getName() + ChatColor.GRAY + " left.");
			}
		}

		if (state == ArenaState.WAITING || state == ArenaState.STARTING) {
			if (players.size() < minPlayers) {
				cancelCountdown();
			}
		}
		plugin.getScoreboardManager().updateLobby(this);
	}

	public void leaveAll() {
		for (UUID id : new LinkedHashSet<>(players)) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) {
				leave(p, false);
			} else {
				players.remove(id);
				playerTeam.remove(id);
			}
		}
	}

	// --- lobby countdown -----------------------------------------------------

	private void startCountdown() {
		if (state != ArenaState.WAITING) {
			return;
		}
		state = ArenaState.STARTING;
		countdownSecondsRemaining = plugin.getConfig().getInt("config.lobby_countdown_seconds", 30);
		countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (countdownSecondsRemaining <= 0) {
				start();
				return;
			}
			if (countdownSecondsRemaining <= 5 || countdownSecondsRemaining % 10 == 0) {
				broadcast(ChatColor.YELLOW + "Game starting in " + countdownSecondsRemaining + "...");
			}
			plugin.getScoreboardManager().updateLobby(this);
			countdownSecondsRemaining--;
		}, 0L, 20L);
	}

	private void cancelCountdown() {
		if (countdownTask != null) {
			countdownTask.cancel();
			countdownTask = null;
		}
		if (state == ArenaState.STARTING) {
			state = ArenaState.WAITING;
			broadcast(ChatColor.RED + "Not enough players, countdown cancelled.");
			plugin.getScoreboardManager().updateLobby(this);
		}
	}

	// --- game lifecycle --------------------------------------------------------

	private void start() {
		cancelCountdown();
		state = ArenaState.INGAME;
		teamWasAtOne.clear();

		int startingScore = defaultScore;
		redScore = startingScore;
		blueScore = startingScore;

		for (UUID id : players) {
			Player p = Bukkit.getPlayer(id);
			if (p == null) {
				continue;
			}
			Team team = playerTeam.get(id);
			p.teleport(spawns[team.spawnIndex()]);
			plugin.getKit().giveKit(p, team);
			blocksBrokenThisGame.put(id, 0);
		}

		broadcast(ChatColor.GREEN + "Go! Shoot the ground out from under the other team!");
		plugin.getScoreboardManager().updateInGame(this);

		int intervalTicks = Math.max(1, plugin.getConfig().getInt("config.powerup_interval_seconds", 3)) * 20;
		powerupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::maybeSpawnPowerup, intervalTicks, intervalTicks);
	}

	private void maybeSpawnPowerup() {
		if (state != ArenaState.INGAME) {
			return;
		}
		int chance = plugin.getConfig().getInt("config.powerup_spawn_percentage", 10);
		if (Math.random() * 100 > chance) {
			return;
		}
		java.util.List<UUID> alive = players.stream().filter(id -> Bukkit.getPlayer(id) != null).toList();
		if (alive.isEmpty()) {
			return;
		}
		Player target = Bukkit.getPlayer(alive.get((int) (Math.random() * alive.size())));
		if (target == null) {
			return;
		}
		Powerups.spawn(plugin, target.getLocation().clone().add(0, 5, 0));
	}

	/** Called by the game listener whenever a player falls out of the arena. */
	public void onPlayerFell(Player faller) {
		if (state != ArenaState.INGAME) {
			return;
		}
		Team fallenTeam = playerTeam.get(faller.getUniqueId());
		if (fallenTeam == null) {
			return;
		}
		Team winningSideIfEliminated = fallenTeam.other();

		if (fallenTeam == Team.RED) {
			blueScore++;
			redScore--;
			if (redScore == 1) {
				teamWasAtOne.add(Team.RED);
			}
		} else {
			redScore++;
			blueScore--;
			if (blueScore == 1) {
				teamWasAtOne.add(Team.BLUE);
			}
		}

		if (getScore(fallenTeam) < 1) {
			end(winningSideIfEliminated);
			return;
		}

		Player p = faller;
		p.teleport(spawns[fallenTeam.spawnIndex()]);
		plugin.getKit().giveArmor(p, fallenTeam);
		plugin.getScoreboardManager().updateInGame(this);
	}

	public void onBlockBroken(UUID player) {
		int count = blocksBrokenThisGame.merge(player, 1, Integer::sum);
		if (count == 100) {
			Player p = Bukkit.getPlayer(player);
			if (p != null) {
				p.sendMessage(ChatColor.GOLD + "Milestone: you've destroyed 100 blocks with your bow this game!");
			}
		}
	}

	private void end(Team winner) {
		state = ArenaState.ENDING;
		broadcast(winner.chatColor() + "" + ChatColor.BOLD + winner.name() + " TEAM WINS!");

		boolean comeback = teamWasAtOne.contains(winner);
		for (UUID id : players) {
			Player p = Bukkit.getPlayer(id);
			if (p == null) {
				continue;
			}
			plugin.getStats().flushGameBlocks(id, blocksBrokenThisGame.getOrDefault(id, 0));
			if (comeback && playerTeam.get(id) == winner) {
				p.sendMessage(ChatColor.GOLD + "Achievement: won with one life left!");
			}
		}

		if (powerupTask != null) {
			powerupTask.cancel();
			powerupTask = null;
		}

		Bukkit.getScheduler().runTaskLater(plugin, this::reset, 100L);
	}

	private void reset() {
		regen.revertAll();
		for (UUID id : new LinkedHashSet<>(players)) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) {
				plugin.getKit().removeArmor(p);
				p.getInventory().clear();
				p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
				p.teleport(p.getWorld().getSpawnLocation());
			}
		}
		players.clear();
		playerTeam.clear();
		blocksBrokenThisGame.clear();
		teamWasAtOne.clear();
		nextJoinRed = true;
		state = ArenaState.WAITING;
	}

	/** Force-stops the arena immediately, e.g. from an admin command or plugin shutdown. */
	public void forceStop() {
		if (countdownTask != null) {
			countdownTask.cancel();
			countdownTask = null;
		}
		if (powerupTask != null) {
			powerupTask.cancel();
			powerupTask = null;
		}
		boolean wasRunning = state == ArenaState.INGAME || state == ArenaState.STARTING;
		if (wasRunning) {
			broadcast(ChatColor.RED + "The game was stopped.");
		}
		leaveAll();
		regen.revertAll();
		state = ArenaState.WAITING;
	}

	private void broadcast(String message) {
		for (UUID id : players) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) {
				p.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "BowBash" + ChatColor.DARK_GRAY + "] " + message);
			}
		}
	}
}
