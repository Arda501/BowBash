package com.comze_instancelabs.bowbash;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The "ready up" indicator every waiting-lobby player holds in hotbar slot 0: right-clicking it
 * toggles readiness. A gray dye means not ready, a lime dye means ready.
 */
public final class ReadyItem {

	public static final int SLOT = 0;

	private ReadyItem() {
	}

	private static NamespacedKey key(Main plugin) {
		return new NamespacedKey(plugin, "ready_item");
	}

	public static ItemStack notReady(Main plugin) {
		return build(plugin, Material.GRAY_DYE, ChatColor.RED + "" + ChatColor.BOLD + "Not Ready");
	}

	public static ItemStack ready(Main plugin) {
		return build(plugin, Material.LIME_DYE, ChatColor.GREEN + "" + ChatColor.BOLD + "Ready");
	}

	private static ItemStack build(Main plugin, Material material, String name) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.setDisplayName(name);
		meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
		stack.setItemMeta(meta);
		return stack;
	}

	public static boolean isReadyItem(Main plugin, ItemStack stack) {
		if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
			return false;
		}
		return stack.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
	}
}
