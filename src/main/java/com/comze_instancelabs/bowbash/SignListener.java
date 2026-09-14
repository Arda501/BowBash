package com.comze_instancelabs.bowbash;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Lets players join/leave an arena by right-clicking a sign whose first line reads "[BowBash]" and
 * second line names the arena, instead of typing a command.
 */
public class SignListener implements Listener {

	private static final String HEADER = "[BowBash]";

	private final Main plugin;

	public SignListener(Main plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.hasBlock()) {
			return;
		}
		Block block = event.getClickedBlock();
		if (!(block.getState() instanceof Sign sign)) {
			return;
		}
		String line0 = ChatColor.stripColor(plainText(sign, 0));
		if (!HEADER.equalsIgnoreCase(line0)) {
			return;
		}
		String arenaName = ChatColor.stripColor(plainText(sign, 1));
		Arena arena = plugin.getArenaManager().get(arenaName);
		Player p = event.getPlayer();
		if (arena == null) {
			p.sendMessage(ChatColor.RED + "That arena no longer exists.");
			return;
		}

		if (arena.getPlayers().contains(p.getUniqueId())) {
			arena.leave(p, false);
		} else {
			String error = arena.join(p, null);
			if (error != null) {
				p.sendMessage(error);
			}
		}
	}

	@EventHandler
	public void onBreak(BlockBreakEvent event) {
		if (!(event.getBlock().getState() instanceof Sign sign)) {
			return;
		}
		String line0 = ChatColor.stripColor(plainText(sign, 0));
		if (HEADER.equalsIgnoreCase(line0) && !event.getPlayer().hasPermission("bowbash.admin")) {
			event.setCancelled(true);
		}
	}

	private String plainText(Sign sign, int line) {
		return PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(line));
	}
}
