package com.comze_instancelabs.bowbash;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
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
	/** The arena's bounding box corners, used to snapshot/restore the whole map each round - see {@link #snapshot}. */
	private Location pos1;
	private Location pos2;

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

	/** Ticks since the current round went INGAME - drives the scoreboard's elapsed-time line. Uncapped. */
	private int roundElapsedTicks = 0;

	/** Ticks left before a dead player is force-respawned; see {@link #beginRespawnDelay}. */
	private final Map<UUID, Integer> pendingRespawns = new LinkedHashMap<>();

	private BukkitTask powerupTask;

	private final MapSnapshot snapshot = new MapSnapshot();

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

	public Location getPos1() {
		return pos1;
	}

	public void setPos1(Location pos1) {
		this.pos1 = pos1;
	}

	public Location getPos2() {
		return pos2;
	}

	public void setPos2(Location pos2) {
		this.pos2 = pos2;
	}

	public boolean isFullyConfigured() {
		return lobby != null && spawns[0] != null && spawns[1] != null && pos1 != null && pos2 != null && snapshot.isCaptured();
	}

	/** Captures the current pos1/pos2 box as this arena's reset baseline and writes it to disk - {@code /bb savemap}. */
	public boolean saveMapBaseline() throws IOException {
		if (pos1 == null || pos2 == null) {
			return false;
		}
		snapshot.capture(pos1, pos2);
		snapshot.save(snapshotFile());
		return true;
	}

	/** Loads a previously saved baseline from disk, if any - called once by {@link ArenaManager#load()}. */
	public void loadMapBaseline() throws IOException {
		snapshot.load(snapshotFile());
	}

	/** Removes this arena's saved baseline file, if any - called by {@link ArenaManager#remove}. */
	public void deleteMapBaseline() {
		snapshotFile().delete();
	}

	private File snapshotFile() {
		return new File(plugin.getDataFolder(), "maps/" + name + ".snapshot");
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

	/** "M:SS" elapsed since the round went INGAME. */
	public String getElapsedTimeFormatted() {
		int totalSeconds = roundElapsedTicks / 20;
		return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
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
		p.setGameMode(GameMode.ADVENTURE);
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
		pendingRespawns.remove(id);
		blocksBrokenThisGame.remove(id);
		plugin.getKit().removeArmor(p);
		p.getInventory().clear();
		p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
		p.setGameMode(GameMode.ADVENTURE);

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
		if (state == ArenaState.INGAME) {
			tickPendingRespawns(tickIntervalTicks);
			roundElapsedTicks += tickIntervalTicks;
			if (roundElapsedTicks % 20 < tickIntervalTicks) {
				// once a second, roughly - refreshes the "M:SS" line
				plugin.getScoreboardManager().updateInGame(this);
			}
			return;
		}
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
		roundElapsedTicks = 0;
		pendingRespawns.clear();

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
			p.setGameMode(GameMode.SURVIVAL);
			plugin.getKit().giveKit(p, team);
			blocksBrokenThisGame.put(id, 0);
		}

		broadcast(ChatColor.GREEN + "Go! Shoot the ground out from under the other team!");
		plugin.getScoreboardManager().updateInGame(this);

		int intervalTicks = Math.max(1, plugin.getConfig().getInt("config.powerup_interval_seconds", 3)) * 20;
		powerupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::maybeSpawnPowerup, intervalTicks, intervalTicks);
	}

	/**
	 * Called by the game listener whenever a rostered player actually dies (from whatever cause -
	 * BowBash doesn't decide that, see {@link GameListener}). Starts a short countdown, after which
	 * {@link #tickPendingRespawns} forces their actual respawn itself - no button click needed -
	 * landing them at their team spawn with a fresh kit via {@code GameListener#onRespawn}.
	 */
	public void beginRespawnDelay(Player p) {
		int delayTicks = (int) Math.round(plugin.getConfig().getDouble("config.respawn_delay_seconds", 1.0) * 20.0);
		pendingRespawns.put(p.getUniqueId(), delayTicks);
	}

	private void tickPendingRespawns(int tickIntervalTicks) {
		if (pendingRespawns.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Integer>> it = pendingRespawns.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			Player p = Bukkit.getPlayer(entry.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			int ticksLeft = entry.getValue() - tickIntervalTicks;
			if (ticksLeft <= 0) {
				it.remove();
				if (p.isDead()) {
					p.spigot().respawn();
				}
				continue;
			}
			entry.setValue(ticksLeft);
		}
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

		playSoundToArena(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2F);

		if (getScore(fallenTeam) < 1) {
			end(winningSideIfEliminated);
			return;
		}

		plugin.getScoreboardManager().updateInGame(this);
	}

	private void playSoundToArena(Sound sound, float pitch) {
		for (UUID id : players) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) {
				p.playSound(p.getLocation(), sound, SoundCategory.MASTER, 1.0F, pitch);
			}
		}
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

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			reset();
			playVictoryCelebration(winner);
		}, 100L);
	}

	/** Restores the map to its pre-round snapshot, sends everyone back to the lobby, and re-arms for the next round. */
	private void reset() {
		snapshot.restore();
		for (UUID id : new LinkedHashSet<>(players)) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) {
				plugin.getKit().removeArmor(p);
				p.getInventory().clear();
				p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
				p.setGameMode(GameMode.ADVENTURE);
				p.teleport(p.getWorld().getSpawnLocation());
			}
		}
		players.clear();
		playerTeam.clear();
		readyPlayers.clear();
		belowVoid.clear();
		pendingRespawns.clear();
		blocksBrokenThisGame.clear();
		teamWasAtOne.clear();
		state = ArenaState.WAITING;
	}

	/**
	 * A victory sound for every connected player (centred on each of them individually, so it's
	 * heard clearly wherever they are), plus a handful of real firework rockets launched over the
	 * arena's lobby, in the winning team's colour - everyone's already standing there by the time
	 * this runs, via {@link #reset}.
	 */
	private void playVictoryCelebration(Team winner) {
		for (Player online : Bukkit.getOnlinePlayers()) {
			online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0F, 1.0F);
		}
		if (lobby == null || !lobby.isWorldLoaded()) {
			return;
		}
		World world = lobby.getWorld();
		for (int i = 0; i < 6; i++) {
			Bukkit.getScheduler().runTaskLater(plugin, () -> launchFirework(world, winner), i * 8L);
		}
	}

	private void launchFirework(World world, Team winner) {
		Location loc = lobby.clone().add((Math.random() * 6) - 3, 1, (Math.random() * 6) - 3);
		org.bukkit.entity.Firework firework = world.spawn(loc, org.bukkit.entity.Firework.class);
		org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();
		meta.addEffect(org.bukkit.FireworkEffect.builder()
				.with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
				.withColor(winner.armorColor())
				.withFade(org.bukkit.Color.WHITE)
				.withTrail()
				.withFlicker()
				.build());
		meta.setPower(1);
		firework.setFireworkMeta(meta);
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
		snapshot.restore();
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
