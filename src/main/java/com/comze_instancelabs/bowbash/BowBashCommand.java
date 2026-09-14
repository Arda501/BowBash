package com.comze_instancelabs.bowbash;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Handles /bb (or /bowbash): everything from joining a game to setting one up.
 */
public class BowBashCommand implements CommandExecutor, TabCompleter {

	private final Main plugin;

	public BowBashCommand(Main plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sendHelp(sender);
			return true;
		}
		String sub = args[0].toLowerCase();

		switch (sub) {
			case "join" -> join(sender, args);
			case "leave" -> leave(sender);
			case "list" -> list(sender);
			case "create" -> admin(sender, args, this::create);
			case "remove", "delete" -> admin(sender, args, this::remove);
			case "setlobby" -> admin(sender, args, this::setLobby);
			case "setspawn" -> admin(sender, args, this::setSpawn);
			case "setdefaultscore" -> admin(sender, args, this::setDefaultScore);
			case "stop" -> admin(sender, args, this::forceStop);
			default -> sendHelp(sender);
		}
		return true;
	}

	private interface PlayerSubcommand {
		void run(Player p, String[] args);
	}

	private void admin(CommandSender sender, String[] args, PlayerSubcommand handler) {
		if (!(sender instanceof Player p)) {
			sender.sendMessage(ChatColor.RED + "Only players can use that.");
			return;
		}
		if (!p.hasPermission("bowbash.admin")) {
			p.sendMessage(ChatColor.RED + "You don't have permission to do that.");
			return;
		}
		handler.run(p, args);
	}

	private void join(CommandSender sender, String[] args) {
		if (!(sender instanceof Player p)) {
			sender.sendMessage(ChatColor.RED + "Only players can join.");
			return;
		}
		if (args.length < 2) {
			p.sendMessage(ChatColor.RED + "Usage: /bb join <arena> [red|blue]");
			return;
		}
		Arena arena = plugin.getArenaManager().get(args[1]);
		if (arena == null) {
			p.sendMessage(ChatColor.RED + "No such arena: " + args[1]);
			return;
		}
		Team team = null;
		if (args.length > 2) {
			if (args[2].equalsIgnoreCase("red")) {
				team = Team.RED;
			} else if (args[2].equalsIgnoreCase("blue")) {
				team = Team.BLUE;
			} else {
				p.sendMessage(ChatColor.RED + "Team must be 'red' or 'blue'.");
				return;
			}
		}
		String error = arena.join(p, team);
		if (error != null) {
			p.sendMessage(error);
		}
	}

	private void leave(CommandSender sender) {
		if (!(sender instanceof Player p)) {
			sender.sendMessage(ChatColor.RED + "Only players can leave.");
			return;
		}
		plugin.getArenaManager().findArenaOf(p).ifPresentOrElse(a -> a.leave(p, false), () -> p.sendMessage(ChatColor.RED + "You're not in an arena."));
	}

	private void list(CommandSender sender) {
		if (plugin.getArenaManager().getAll().isEmpty()) {
			sender.sendMessage(ChatColor.YELLOW + "There are no arenas set up yet.");
			return;
		}
		String names = plugin.getArenaManager().getAll().stream()
				.map(a -> a.getName() + ChatColor.GRAY + "(" + a.countTeam(Team.RED) + " red / " + a.countTeam(Team.BLUE) + " blue)" + ChatColor.RESET)
				.collect(Collectors.joining(ChatColor.GRAY + ", " + ChatColor.WHITE));
		sender.sendMessage(ChatColor.AQUA + "Arenas: " + ChatColor.WHITE + names);
	}

	private void create(Player p, String[] args) {
		if (args.length < 2) {
			p.sendMessage(ChatColor.RED + "Usage: /bb create <arena>");
			return;
		}
		if (plugin.getArenaManager().get(args[1]) != null) {
			p.sendMessage(ChatColor.RED + "An arena with that name already exists.");
			return;
		}
		plugin.getArenaManager().create(args[1], p.getWorld());
		p.sendMessage(ChatColor.GREEN + "Created arena '" + args[1] + "'. Now set its lobby and both spawns:");
		p.sendMessage(ChatColor.GRAY + "/bb setlobby " + args[1] + "  |  /bb setspawn " + args[1] + " red  |  /bb setspawn " + args[1] + " blue");
	}

	private void remove(Player p, String[] args) {
		if (args.length < 2) {
			p.sendMessage(ChatColor.RED + "Usage: /bb remove <arena>");
			return;
		}
		if (plugin.getArenaManager().remove(args[1])) {
			p.sendMessage(ChatColor.GREEN + "Removed arena '" + args[1] + "'.");
		} else {
			p.sendMessage(ChatColor.RED + "No such arena: " + args[1]);
		}
	}

	private void setLobby(Player p, String[] args) {
		withArena(p, args, arena -> {
			arena.setLobby(p.getLocation());
			plugin.getArenaManager().save();
			p.sendMessage(ChatColor.GREEN + "Lobby for '" + arena.getName() + "' set to your location.");
		});
	}

	private void setSpawn(Player p, String[] args) {
		if (args.length < 3 || (!args[2].equalsIgnoreCase("red") && !args[2].equalsIgnoreCase("blue"))) {
			p.sendMessage(ChatColor.RED + "Usage: /bb setspawn <arena> <red|blue>");
			return;
		}
		withArena(p, args, arena -> {
			Team team = Team.valueOf(args[2].toUpperCase());
			arena.setSpawn(team, p.getLocation());
			plugin.getArenaManager().save();
			p.sendMessage(ChatColor.GREEN + team.name() + " spawn for '" + arena.getName() + "' set to your location.");
		});
	}

	private void setDefaultScore(Player p, String[] args) {
		withIntArg(p, args, "setdefaultscore", (arena, n) -> {
			arena.setDefaultScore(n);
			plugin.getArenaManager().save();
			p.sendMessage(ChatColor.GREEN + "Default score for '" + arena.getName() + "' set to " + n + ".");
		});
	}

	private void forceStop(Player p, String[] args) {
		withArena(p, args, arena -> {
			arena.forceStop();
			p.sendMessage(ChatColor.YELLOW + "Arena '" + arena.getName() + "' stopped and reset.");
		});
	}

	private interface ArenaConsumer {
		void accept(Arena arena);
	}

	private void withArena(Player p, String[] args, ArenaConsumer consumer) {
		if (args.length < 2) {
			p.sendMessage(ChatColor.RED + "Usage: /bb " + args[0] + " <arena>");
			return;
		}
		Arena arena = plugin.getArenaManager().get(args[1]);
		if (arena == null) {
			p.sendMessage(ChatColor.RED + "No such arena: " + args[1]);
			return;
		}
		consumer.accept(arena);
	}

	private interface ArenaIntConsumer {
		void accept(Arena arena, int value);
	}

	private void withIntArg(Player p, String[] args, String usageName, ArenaIntConsumer consumer) {
		if (args.length < 3) {
			p.sendMessage(ChatColor.RED + "Usage: /bb " + usageName + " <arena> <number>");
			return;
		}
		Arena arena = plugin.getArenaManager().get(args[1]);
		if (arena == null) {
			p.sendMessage(ChatColor.RED + "No such arena: " + args[1]);
			return;
		}
		try {
			consumer.accept(arena, Integer.parseInt(args[2]));
		} catch (NumberFormatException e) {
			p.sendMessage(ChatColor.RED + "'" + args[2] + "' isn't a number.");
		}
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "BowBash");
		sender.sendMessage(ChatColor.GRAY + "/bb join <arena> [red|blue]" + ChatColor.DARK_GRAY + " - join a game (omit the team to auto-balance)");
		sender.sendMessage(ChatColor.GRAY + "/bb leave" + ChatColor.DARK_GRAY + " - leave your current game");
		sender.sendMessage(ChatColor.GRAY + "/bb list" + ChatColor.DARK_GRAY + " - list arenas");
		sender.sendMessage(ChatColor.DARK_GRAY + "Right-click your item in the lobby to ready up - the game starts once both teams are equal size and everyone's ready.");
		if (sender.hasPermission("bowbash.admin")) {
			sender.sendMessage(ChatColor.GRAY + "/bb create <arena>");
			sender.sendMessage(ChatColor.GRAY + "/bb remove <arena>");
			sender.sendMessage(ChatColor.GRAY + "/bb setlobby <arena>");
			sender.sendMessage(ChatColor.GRAY + "/bb setspawn <arena> <red|blue>");
			sender.sendMessage(ChatColor.GRAY + "/bb setdefaultscore <arena> <n>");
			sender.sendMessage(ChatColor.GRAY + "/bb stop <arena>");
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> options = new ArrayList<>();
		if (args.length == 1) {
			options.addAll(List.of("join", "leave", "list", "help"));
			if (sender.hasPermission("bowbash.admin")) {
				options.addAll(List.of("create", "remove", "setlobby", "setspawn", "setdefaultscore", "stop"));
			}
		} else if (args.length == 2) {
			for (Arena arena : plugin.getArenaManager().getAll()) {
				options.add(arena.getName());
			}
		} else if (args.length == 3 && (args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("join"))) {
			options.addAll(List.of("red", "blue"));
		}
		String current = args[args.length - 1].toLowerCase();
		return options.stream().filter(o -> o.toLowerCase().startsWith(current)).collect(Collectors.toList());
	}
}
