package com.comze_instancelabs.bowbash;

import org.bukkit.ChatColor;
import org.bukkit.Color;

/**
 * The two teams players are auto-balanced into when they join a lobby.
 */
public enum Team {

	RED(ChatColor.RED, Color.RED, 0),
	BLUE(ChatColor.BLUE, Color.BLUE, 1);

	private final ChatColor chatColor;
	private final Color armorColor;
	private final int spawnIndex;

	Team(ChatColor chatColor, Color armorColor, int spawnIndex) {
		this.chatColor = chatColor;
		this.armorColor = armorColor;
		this.spawnIndex = spawnIndex;
	}

	public ChatColor chatColor() {
		return chatColor;
	}

	public Color armorColor() {
		return armorColor;
	}

	/** Index into Arena's spawn list (0 = red, 1 = blue). */
	public int spawnIndex() {
		return spawnIndex;
	}

	public Team other() {
		return this == RED ? BLUE : RED;
	}
}
