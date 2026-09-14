package com.comze_instancelabs.bowbash;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * The random floating pickups that spawn above a random in-game player: a jump/speed potion,
 * a bomb egg, a stack of glass, or a rare enchanted bow.
 */
public final class Powerups {

	private Powerups() {
	}

	public static NamespacedKey key(Main plugin) {
		return new NamespacedKey(plugin, "powerup");
	}

	public static void spawn(Main plugin, Location location) {
		ItemStack stack = roll();
		Item item = location.getWorld().dropItem(location, stack);
		item.setVelocity(new Vector(0, 0, 0));
		item.setGravity(false);
		item.setGlowing(true);
		item.setUnlimitedLifetime(true);
		item.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
	}

	private static ItemStack roll() {
		double roll = Math.random() * 100;
		if (roll <= 20) { // ~20%: speed + jump boost potion
			return new ItemStack(Material.POTION, 1);
		} else if (roll <= 50) { // ~30%: bomb
			return new ItemStack(Material.EGG, 1);
		} else if (roll <= 90) { // ~40%: extra glass to shoot with
			return new ItemStack(Material.WHITE_STAINED_GLASS, 32);
		} else { // ~10%: a very good bow
			ItemStack bow = new ItemStack(Material.BOW);
			ItemMeta meta = bow.getItemMeta();
			meta.addEnchant(Enchantment.KNOCKBACK, 5, true);
			meta.addEnchant(Enchantment.INFINITY, 1, true);
			bow.setItemMeta(meta);
			return bow;
		}
	}

	public static boolean isPowerup(Main plugin, Item item) {
		return item.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
	}
}
