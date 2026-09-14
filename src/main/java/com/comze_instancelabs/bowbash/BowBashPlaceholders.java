package com.comze_instancelabs.bowbash;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI placeholders for BowBash - lets any PAPI-aware plugin (a sign plugin like
 * SignManager, a scoreboard plugin, chat formatting, ...) display live stats for whichever arena
 * the *viewing* player is currently in, without needing to know anything about BowBash itself.
 * BowBash is multi-arena, so these are always relative to the requesting player, not global -
 * a player not currently in any arena sees "-" for all of them. Registers only if PlaceholderAPI
 * is actually installed (a soft dependency - see {@code Main#onEnable}).
 *
 * <pre>
 * %bowbash_time%          "M:SS" elapsed since the viewer's round went INGAME, or "-"
 * %bowbash_red_score%     the viewer's arena's current red score, or "-"
 * %bowbash_blue_score%    the viewer's arena's current blue score, or "-"
 * %bowbash_map%           the viewer's arena's name, or "-"
 * </pre>
 */
public class BowBashPlaceholders extends PlaceholderExpansion {

	private final Main plugin;

	public BowBashPlaceholders(Main plugin) {
		this.plugin = plugin;
	}

	@Override
	public @NotNull String getIdentifier() {
		return "bowbash";
	}

	@Override
	public @NotNull String getAuthor() {
		return "InstanceLabs";
	}

	@Override
	public @NotNull String getVersion() {
		return plugin.getDescription().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
		if (player == null) {
			return "-";
		}
		Arena arena = plugin.getArenaManager().findArenaOf(player).orElse(null);
		if (arena == null) {
			return "-";
		}
		return switch (params.toLowerCase()) {
			case "time" -> arena.isInGame() ? arena.getElapsedTimeFormatted() : "-";
			case "red_score" -> String.valueOf(arena.getRedScore());
			case "blue_score" -> String.valueOf(arena.getBlueScore());
			case "map" -> arena.getName();
			default -> null;
		};
	}
}
