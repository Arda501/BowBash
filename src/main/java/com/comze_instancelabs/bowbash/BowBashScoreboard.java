package com.comze_instancelabs.bowbash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * A simple per-arena sidebar scoreboard, shared by every player currently in that arena.
 */
public class BowBashScoreboard {

	private final Map<String, Scoreboard> boards = new HashMap<>();
	private final Map<String, Objective> objectives = new HashMap<>();

	public void updateLobby(Arena arena) {
		List<String> lines = new ArrayList<>();
		lines.add(ChatColor.GRAY + "Players: " + ChatColor.WHITE + arena.getPlayers().size() + "/" + arena.getMaxPlayers());
		lines.add(ChatColor.GRAY + "State: " + ChatColor.WHITE + arena.getState().name());
		render(arena, ChatColor.AQUA + "" + ChatColor.BOLD + arena.getName(), lines);
	}

	public void updateInGame(Arena arena) {
		List<String> lines = new ArrayList<>();
		lines.add(Team.RED.chatColor() + "Red: " + ChatColor.WHITE + Math.max(0, arena.getRedScore()));
		lines.add(Team.BLUE.chatColor() + "Blue: " + ChatColor.WHITE + Math.max(0, arena.getBlueScore()));
		render(arena, ChatColor.AQUA + "" + ChatColor.BOLD + arena.getName(), lines);
	}

	public void remove(Player p) {
		p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}

	private void render(Arena arena, String title, List<String> lines) {
		Scoreboard board = boards.computeIfAbsent(arena.getName(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
		Objective objective = objectives.get(arena.getName());
		if (objective == null) {
			objective = board.registerNewObjective(arena.getName(), Criteria.DUMMY, LegacyComponentSerializer.legacySection().deserialize(clamp(title, 32)));
			objective.setDisplaySlot(DisplaySlot.SIDEBAR);
			objectives.put(arena.getName(), objective);
		}
		objective.displayName(LegacyComponentSerializer.legacySection().deserialize(clamp(title, 32)));

		for (String entry : new ArrayList<>(board.getEntries())) {
			board.resetScores(entry);
		}

		int score = lines.size();
		for (String line : lines) {
			// pad with an invisible colour code so otherwise-identical lines stay distinct entries
			String entry = clamp(line, 38) + ChatColor.values()[score % ChatColor.values().length];
			objective.getScore(entry).setScore(score);
			score--;
		}

		for (var id : arena.getPlayers()) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) {
				p.setScoreboard(board);
			}
		}
	}

	private String clamp(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}
}
