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
 *
 * <p>The lobby doesn't wait for a fixed player count. Anyone can join either team at any time
 * (no maximum); once both teams are non-empty and the same size, and every rostered player has
 * right-clicked their {@link ReadyItem ready item}, a short countdown starts automatically. The
 * countdown aborts immediately back to {@link ArenaState#WAITING} if a team becomes uneven or
 * unready again before it finishes - see {@link #tick()}.
 */
public class Arena {

	private final Main plugin;
	private final String name;

	private String worldName;
	private Location lobby;
	private final Location[] spawns = new Location[2]; // 0 = red, 1 = blue

	private int defaultScore;

	private ArenaState state = ArenaState.WAITING;

	private final LinkedHashSet<UUID> players = new LinkedHashSet<>();
	private final Map<UUID, Team> playerTeam = new LinkedHashMap<>();
	private final Set<UUID> readyPlayers = new LinkedHashSet<>();
	private final Map<UUID, Integer> blocksBrokenThisGame = new LinkedHashMap<>();
	private final Set<Team> teamWasAtOne = EnumSet.noneOf(Team.class);

	private int redScore;
	private int blueScore;

	/** Ticks remaining in the ready countdown, counted down by {@link #tick()}; -1 = not counting down. */
	private int countdownTicksRemaining = -1;

	private BukkitTask powerupTask;

	private final BlockRegen regen = new BlockRegen();

	public Arena(Main plugin, String name) {
		this.plugin = plugin;
		this.name = name;
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

	public boolean isReady(UUID uuid) {
		return readyPlayers.contains(uuid);
	}

	public int countTeam(Team team) {
		int n = 0;
		for (Team t : playerTeam.values()) {
			if (t == team) {
				n++;
			}
		}
		return n;
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

	public int getCountdownSecondsRemaining() {
		return countdownTicksRemaining < 0 ? -1 : countdownTicksRemaining / 20;
	}

	public BlockRegen getRegen() {
		return regen;
	}

	public boolean isInGame() {
		return state == ArenaState.INGAME;
	}

	// --- join / leave ------------------------------------------------------

	/**
	 * @param preferredTeam team to join, or {@code null} to auto-pick whichever team currently has
	 *                      fewer players (red on a tie)
	 * @return an error message to show the player, or {@code null} on success
	 */
	public String join(Player p, Team preferredTeam) {
		if (!isFullyConfigured()) {
			return ChatColor.RED + "This arena isn't fully set up yet.";
		}
		if (state == ArenaState.INGAME || state == ArenaState.ENDING) {
			return ChatColor.RED + "That game has already started.";
		}
		if (players.contains(p.getUniqueId())) {
			return ChatColor.RED + "You're already in that arena.";
		}

		Team team = preferredTeam != null ? preferredTeam : (countTeam(Team.RED) <= countTeam(Team.BLUE) ? Team.RED : Team.BLUE);

		players.add(p.getUniqueId());
		playerTeam.put(p.getUniqueId(), team);
		blocksBrokenThisGame.put(p.getUniqueId(), 0);

		p.teleport(lobby);
		giveReadyItem(p);
		broadcast(team.chatColor() + p.getName() + ChatColor.GRAY + " joined " + team.chatColor() + team.name() + ChatColor.GRAY
				+ " (" + countTeam(Team.RED) + " red / " + countTeam(Team.BLUE) + " blue). Right-click your item when ready!");
		plugin.getScoreboardManager().updateLobby(this);
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
		readyPlayers.remove(id);
		belowVoid.remove(id);
		blocksBrokenThisGame.remove(id);
		plugin.getKit().removeArmor(p);
		p.getInventory().clear();
		p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

		if (!silent) {
			p.teleport(p.getWorld().getSpawnLocation());
			if (team != null) {
				broadcast(team.chatColor() + p.getName() + ChatColor.GRAY + " left.");
			}
		}

		if (state == ArenaState.WAITING || state == ArenaState.STARTING) {
			recheckLobby();
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
				readyPlayers.remove(id);
			}
		}
	}

	// --- ready up ------------------------------------------------------------

	private void giveReadyItem(Player p) {
		p.getInventory().clear();
		p.getInventory().setItem(ReadyItem.SLOT, ReadyItem.notReady(plugin));
	}

	/** Right-clicking the ready item calls this. */
	public void toggleReady(Player p) {
		UUID id = p.getUniqueId();
		if (!playerTeam.containsKey(id)) {
			return;
		}
		if (readyPlayers.remove(id)) {
			p.getInventory().setItem(ReadyItem.SLOT, ReadyItem.notReady(plugin));
			if (state == ArenaState.STARTING) {
				abortCountdown(p.getName() + " is no longer ready");
			}
		} else {
			readyPlayers.add(id);
			p.getInventory().setItem(ReadyItem.SLOT, ReadyItem.ready(plugin));
			recheckLobby();
		}
		plugin.getScoreboardManager().updateLobby(this);
	}

	/** Self-healing: keeps the ready item pinned to its slot for everyone currently waiting. */
	private void enforceReadyItems() {
		for (UUID id : players) {
			Player p = Bukkit.getPlayer(id);
			if (p == null) {
				continue;
			}
			var inv = p.getInventory();
			boolean ready = readyPlayers.contains(id);
			for (int slot = 0; slot < inv.getSize(); slot++) {
				if (slot == ReadyItem.SLOT) {
					continue;
				}
				if (ReadyItem.isReadyItem(plugin, inv.getItem(slot))) {
					inv.setItem(slot, null);
				}
			}
			var current = inv.getItem(ReadyItem.SLOT);
			boolean correct = ReadyItem.isReadyItem(plugin, current) && current.getType() == (ready ? org.bukkit.Material.LIME_DYE : org.bukkit.Material.GRAY_DYE);
			if (!correct) {
				inv.setItem(ReadyItem.SLOT, ready ? ReadyItem.ready(plugin) : ReadyItem.notReady(plugin));
			}
		}
	}

	private boolean allReady() {
		for (UUID id : players) {
			if (!readyPlayers.contains(id)) {
				return false;
			}
		}
		return true;
	}

	private boolean teamsBalancedAndReady() {
		int red = countTeam(Team.RED);
		int blue = countTeam(Team.BLUE);
		return red >= 1 && red == blue && allReady();
	}

	/**
	 * Checked whenever someone readies up, leaves, or disconnects: if both teams are non-empty and
	 * even, and everyone's ready, kicks off the countdown. If the teams are non-empty but uneven,
	 * says so instead of silently doing nothing.
	 */
	private void recheckLobby() {
		if (state != ArenaState.WAITING) {
			return;
		}
		int red = countTeam(Team.RED);
		int blue = countTeam(Team.BLUE);
		if (red == 0 && blue == 0) {
			return;
		}
		if (red != blue) {
			broadcast(ChatColor.RED + "Teams must be the same size to start (currently " + Team.RED.chatColor() + "Red " + red
					+ ChatColor.RED + " / " + Team.BLUE.chatColor() + "Blue " + blue + ChatColor.RED + ").");
			return;
		}
		if (!allReady()) {
			return;
		}
		state = ArenaState.STARTING;
		countdownTicksRemaining = plugin.getConfig().getInt("config.lobby_countdown_seconds", 10) * 20;
		broadcast(ChatColor.GREEN + "All ready - starting in " + (countdownTicksRemaining / 20) + "s!");
		plugin.getScoreboardManager().updateLobby(this);
	}

	private void abortCountdown(String reason) {
		if (state != ArenaState.STARTING) {
			return;
		}
		state = ArenaState.WAITING;
		countdownTicksRemaining = -1;
		broadcast(ChatColor.RED + "Countdown cancelled - " + reason);
		plugin.getScoreboardManager().updateLobby(this);
	}

	/**
	 * Called on a fixed schedule (every few ticks, regardless of arena state) by {@link ArenaManager#tickAll()}.
	 * Re-validates the countdown's conditions every call so it aborts immediately (not just at the
	 * next second boundary) the moment a team becomes uneven or someone un-readies.
	 */
	public void tick(int tickIntervalTicks) {
		if (state != ArenaState.WAITING && state != ArenaState.STARTING) {
			return;
		}
		enforceReadyItems();
		if (state != ArenaState.STARTING) {
			return;
		}
		if (!teamsBalancedAndReady()) {
			abortCountdown("the team/ready conditions are no longer met");
			return;
		}
		if (countdownTicksRemaining <= 0) {
			countdownTicksRemaining = -1;
			start();
			return;
		}
		if (countdownTicksRemaining % 20 == 0) {
			broadcast(ChatColor.YELLOW + Integer.toString(countdownTicksRemaining / 20) + "...");
		}
		countdownTicksRemaining -= tickIntervalTicks;
	}

	// --- game lifecycle --------------------------------------------------------

	private void start() {
		state = ArenaState.INGAME;
		teamWasAtOne.clear();
		readyPlayers.clear();

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

	/**
	 * Players currently below Y=0, so a fall only docks a point once per excursion instead of once
	 * per movement tick they spend down there - see {@link #onPlayerMove(Player)}. Actually dying
	 * and respawning is intentionally left to whatever the server does with that on its own; BowBash
	 * only cares about the points.
	 */
	private final Set<UUID> belowVoid = new LinkedHashSet<>();

	/** Called by the game listener on every move of a player in this arena. */
	public void onPlayerMove(Player p) {
		if (state != ArenaState.INGAME) {
			return;
		}
		UUID id = p.getUniqueId();
		if (p.getLocation().getY() < 0) {
			if (belowVoid.add(id)) {
				onPlayerFell(p);
			}
		} else {
			belowVoid.remove(id);
		}
	}

	/** One point for falling out of the arena; nothing else about actual death/respawn is handled here. */
	private void onPlayerFell(Player faller) {
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
		readyPlayers.clear();
		belowVoid.clear();
		blocksBrokenThisGame.clear();
		teamWasAtOne.clear();
		state = ArenaState.WAITING;
	}

	/** Force-stops the arena immediately, e.g. from an admin command or plugin shutdown. */
	public void forceStop() {
		if (powerupTask != null) {
			powerupTask.cancel();
			powerupTask = null;
		}
		countdownTicksRemaining = -1;
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
