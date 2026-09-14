package com.comze_instancelabs.bowbash;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * The single built-in loadout every player gets. Replaces the original's Vault/permission-gated
 * shop of purchasable kits, which needs an economy plugin BowBash no longer depends on.
 */
public class Kit {

	private final Main plugin;

	public Kit(Main plugin) {
		this.plugin = plugin;
	}

	public void giveKit(Player p, Team team) {
		p.getInventory().clear();

		ItemStack bow = new ItemStack(Material.BOW);
		ItemMeta bowMeta = bow.getItemMeta();
		bowMeta.addEnchant(Enchantment.INFINITY, 1, true);
		bowMeta.addEnchant(Enchantment.KNOCKBACK, 2, true);
		bow.setItemMeta(bowMeta);

		ItemStack stick = new ItemStack(Material.STICK);
		ItemMeta stickMeta = stick.getItemMeta();
		stickMeta.addEnchant(Enchantment.KNOCKBACK, 4, true);
		stick.setItemMeta(stickMeta);

		p.getInventory().addItem(bow, stick, new ItemStack(Material.ARROW, 1));
		giveArmor(p, team);
	}

	public void giveArmor(Player p, Team team) {
		if (!plugin.getConfig().getBoolean("config.automatically_add_colored_bowbash_armor", true)) {
			return;
		}
		p.getInventory().setHelmet(coloredLeather(Material.LEATHER_HELMET, team));
		p.getInventory().setChestplate(coloredLeather(Material.LEATHER_CHESTPLATE, team));
		p.getInventory().setLeggings(coloredLeather(Material.LEATHER_LEGGINGS, team));
		p.getInventory().setBoots(coloredLeather(Material.LEATHER_BOOTS, team));
		p.updateInventory();
	}

	public void removeArmor(Player p) {
		p.getInventory().setHelmet(null);
		p.getInventory().setChestplate(null);
		p.getInventory().setLeggings(null);
		p.getInventory().setBoots(null);
	}

	private ItemStack coloredLeather(Material material, Team team) {
		ItemStack item = new ItemStack(material);
		LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
		meta.setColor(team.armorColor());
		item.setItemMeta(meta);
		return item;
	}

	/** Empties the inventory and resets vitals for a player sitting in the lobby. */
	static void clearAndGiveLobbyState(Player p) {
		p.getInventory().clear();
		p.getInventory().setArmorContents(null);
		p.setHealth(20D);
		p.setFoodLevel(20);
	}
}
