package com.comze_instancelabs.bowbash;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The "leave" item every waiting-lobby player holds alongside their {@link ReadyItem}: a barrier
 * block, right-clicking it runs {@code /bb leave}. Pinned to its hotbar slot the same way the
 * ready item is (see {@link Arena#enforceReadyItems}) - can't be dropped (covered by
 * {@link GameListener#onPlayerDropItem}, same as every other arena item) or moved out of place
 * for long, and a barrier can't actually be placed as a block anyway (on top of which
 * {@link GameListener#onPlace} also refuses it explicitly, just in case).
 */
public final class LeaveItem {

	public static final int SLOT = 8;

	private LeaveItem() {
	}

	private static NamespacedKey key(Main plugin) {
		return new NamespacedKey(plugin, "leave_item");
	}

	public static ItemStack build(Main plugin) {
		ItemStack stack = new ItemStack(Material.BARRIER);
		ItemMeta meta = stack.getItemMeta();
		meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Leave");
		meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
		stack.setItemMeta(meta);
		return stack;
	}

	public static boolean isLeaveItem(Main plugin, ItemStack stack) {
		if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
			return false;
		}
		return stack.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
	}
}
