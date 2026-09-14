package com.comze_instancelabs.bowbash;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BlockIterator;

/**
 * All of BowBash's actual gameplay: falling out eliminates you, arrows/eggs/snowballs eat away at
 * the map, and blocks broken near a spawn platform hand back the material instead of just vanishing.
 *
 * This is a straight port of the original mechanics off the pre-1.13 numeric block-id/data-value API
 * (which no longer exists) onto modern {@link Material}/{@link org.bukkit.block.data.BlockData}.
 */
public class GameListener implements Listener {

	private final Main plugin;

	public GameListener(Main plugin) {
		this.plugin = plugin;
	}

	private Optional<Arena> arenaOf(Player p) {
		return plugin.getArenaManager().findArenaOf(p);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onMove(PlayerMoveEvent event) {
		Player p = event.getPlayer();
		arenaOf(p).ifPresent(a -> {
			if (a.isInGame() && p.getLocation().getY() < 0) {
				a.onPlayerFell(p);
			}
		});
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player p = event.getEntity();
		arenaOf(p).ifPresent(a -> {
			if (a.isInGame()) {
				p.setHealth(20D);
			}
		});
	}

	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player p)) {
			return;
		}
		arenaOf(p).ifPresent(a -> {
			if (a.isInGame()) {
				p.setHealth(20D);
			}
		});
	}

	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player p)) {
			return;
		}
		Optional<Arena> arenaOpt = arenaOf(p);
		if (arenaOpt.isEmpty() || !arenaOpt.get().isInGame()) {
			return;
		}
		Arena a = arenaOpt.get();
		Team victimTeam = a.getTeam(p.getUniqueId());

		Player attacker = null;
		if (event.getDamager() instanceof Player dp) {
			attacker = dp;
		} else if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player sp) {
			attacker = sp;
		}

		if (attacker != null && victimTeam != null && victimTeam == a.getTeam(attacker.getUniqueId())) {
			event.setCancelled(true);
		}
		if (attacker != null) {
			attacker.setHealth(20D);
		}
		Bukkit.getScheduler().runTaskLater(plugin, () -> p.setHealth(20D), 5L);
	}

	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		// nothing dropped by an arena player is ever supposed to leave their inventory: the lobby
		// ready item is pinned in place, and in-game kit items shouldn't be discardable either.
		if (arenaOf(event.getPlayer()).isPresent()) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onReadyItemUse(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		Player p = event.getPlayer();
		if (!ReadyItem.isReadyItem(plugin, event.getItem())) {
			return;
		}
		arenaOf(p).ifPresent(a -> {
			if (a.getState() == ArenaState.WAITING || a.getState() == ArenaState.STARTING) {
				event.setCancelled(true);
				a.toggleReady(p);
			}
		});
	}

	@EventHandler
	public void onPickup(EntityPickupItemEvent event) {
		if (!(event.getEntity() instanceof Player p)) {
			return;
		}
		Optional<Arena> arenaOpt = arenaOf(p);
		if (arenaOpt.isEmpty() || !arenaOpt.get().isInGame()) {
			return;
		}
		Item item = event.getItem();
		if (item.getItemStack().getType() == Material.POTION) {
			p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 1));
			p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
			event.setCancelled(true);
			item.remove();
		}
		for (Entity e : p.getNearbyEntities(3D, 3D, 3D)) {
			if (e instanceof Chicken) {
				e.remove();
			}
		}
	}

	@EventHandler
	public void onPlace(BlockPlaceEvent event) {
		Player p = event.getPlayer();
		arenaOf(p).ifPresent(a -> {
			if (a.isInGame() && isProtected(a, event.getBlock().getLocation())) {
				event.setCancelled(true);
			}
		});
	}

	@EventHandler
	public void onClick(PlayerInteractEvent event) {
		if (!event.hasBlock() || (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
			return;
		}
		Player p = event.getPlayer();
		arenaOf(p).ifPresent(a -> {
			if (!a.isInGame()) {
				return;
			}
			Block clicked = event.getClickedBlock();
			Team farmOwner = farmGlassOwner(clicked.getType());
			if (farmOwner != null) {
				// a team's own farm block already hands out free copies via onBreak below; clicking
				// it shouldn't ALSO give one, and the other team shouldn't get anything from it at all
				return;
			}
			if (isProtected(a, clicked.getLocation()) && isStainedGlass(clicked.getType())) {
				p.getInventory().addItem(new ItemStack(clicked.getType(), 1));
				p.updateInventory();
			}
		});
	}

	@EventHandler
	public void onBreak(BlockBreakEvent event) {
		Player p = event.getPlayer();
		arenaOf(p).ifPresent(a -> {
			if (!a.isInGame()) {
				return;
			}
			Block block = event.getBlock();
			event.setCancelled(true);

			Team farmOwner = farmGlassOwner(block.getType());
			if (farmOwner != null) {
				// an infinite farming block: it never actually breaks/disappears, and only its own
				// team can harvest it - the other team gets nothing (and can't deplete it either)
				if (a.getTeam(p.getUniqueId()) == farmOwner) {
					p.getInventory().addItem(new ItemStack(block.getType(), 1));
					p.updateInventory();
				}
				return;
			}

			if (isProtected(a, block.getLocation())) {
				return;
			}
			a.getRegen().record(block.getLocation());
			Material reward = isStainedGlass(block.getType()) ? block.getType() : Material.WHITE_STAINED_GLASS;
			p.getInventory().addItem(new ItemStack(reward, 1));
			p.updateInventory();
			block.setType(Material.AIR);
			a.onBlockBroken(p.getUniqueId());
		});
	}

	@EventHandler
	public void onEgg(PlayerEggThrowEvent event) {
		if (arenaOf(event.getPlayer()).isPresent()) {
			event.setHatching(false);
		}
	}

	@EventHandler
	public void onProjectileLand(ProjectileHitEvent event) {
		if (!(event.getEntity().getShooter() instanceof Player p)) {
			return;
		}
		Optional<Arena> arenaOpt = arenaOf(p);
		if (arenaOpt.isEmpty() || !arenaOpt.get().isInGame()) {
			return;
		}
		Arena a = arenaOpt.get();

		BlockIterator bi = new BlockIterator(event.getEntity().getWorld(), event.getEntity().getLocation().toVector(), event.getEntity().getVelocity().normalize(), 0.0D, 4);
		Block hit = null;
		while (bi.hasNext()) {
			hit = bi.next();
			if (hit.getType() != Material.AIR) {
				break;
			}
		}
		if (hit == null) {
			return;
		}

		boolean mega;
		if (event.getEntity() instanceof Egg) {
			mega = false;
		} else if (event.getEntity() instanceof Snowball) {
			mega = true;
		} else {
			// arrow (or any other projectile): peel away exactly the one block it hit
			try {
				if (isProtected(a, hit.getLocation())) {
					event.getEntity().remove();
					return;
				}
				boolean brokeToAir = degrade(a, hit);
				if (brokeToAir) {
					a.onBlockBroken(p.getUniqueId());
				}
			} finally {
				event.getEntity().remove();
			}
			return;
		}

		// egg/snowball: area-clear stained glass around the hit point, then always detonate
		try {
			Location l = hit.getLocation();
			if (isStainedGlass(hit.getType()) && farmGlassOwner(hit.getType()) == null) {
				int radius = mega ? 2 : 1;
				for (int x = -radius; x <= radius; x++) {
					for (int z = -radius; z <= radius; z++) {
						Block b = l.getWorld().getBlockAt(l.getBlockX() + x, l.getBlockY(), l.getBlockZ() + z);
						if (!isProtected(a, b.getLocation()) && isStainedGlass(b.getType()) && farmGlassOwner(b.getType()) == null) {
							a.getRegen().record(b.getLocation());
							b.setType(Material.AIR);
						}
					}
				}
			}
			l.getWorld().createExplosion(l.getX(), l.getY(), l.getZ(), 2F, false, false);
		} finally {
			event.getEntity().remove();
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		arenaOf(event.getPlayer()).ifPresent(a -> a.leave(event.getPlayer(), true));
	}

	/**
	 * Degrades a single block one step along its destruction chain (e.g. stone -&gt; cobblestone
	 * -&gt; air). Returns true if the block ended up as air on this hit.
	 */
	private boolean degrade(Arena a, Block hit) {
		Material type = hit.getType();
		if (farmGlassOwner(type) != null) {
			// an infinite farming block: arrows just bounce off it, mining is the only way to harvest it
			return false;
		}
		a.getRegen().record(hit.getLocation());

		if (isStainedGlass(type)) {
			hit.setType(Material.AIR);
			return true;
		} else if (type == Material.STONE) {
			hit.setType(Material.COBBLESTONE);
		} else if (type == Material.COBBLESTONE) {
			hit.setType(Material.AIR);
			return true;
		} else if (type == Material.STONE_BRICKS) {
			hit.setType(Material.CRACKED_STONE_BRICKS);
		} else if (type == Material.CRACKED_STONE_BRICKS) {
			hit.setType(Material.AIR);
			return true;
		} else if (type == Material.GRASS_BLOCK) {
			hit.setType(Material.DIRT);
		} else if (type == Material.OAK_PLANKS) {
			hit.setType(Material.OAK_SLAB);
			Slab slab = (Slab) hit.getBlockData();
			slab.setType(Slab.Type.BOTTOM);
			hit.setBlockData(slab);
		} else if (hit.getBlockData() instanceof Slab slab) {
			if (slab.getType() == Slab.Type.DOUBLE) {
				slab.setType(Slab.Type.BOTTOM);
				hit.setBlockData(slab);
			} else {
				hit.setType(Material.AIR);
				return true;
			}
		}
		return false;
	}

	private boolean isStainedGlass(Material m) {
		return m.name().endsWith("_STAINED_GLASS");
	}

	/**
	 * Light blue / orange stained glass are each a team's permanent, infinite block-farming
	 * resource: never actually destroyed by anything (mining, arrows, eggs/snowballs), and only
	 * mineable (for an item copy) by the team that owns that colour.
	 *
	 * @return the owning team, or {@code null} if {@code m} isn't a farm-glass material
	 */
	private Team farmGlassOwner(Material m) {
		if (m == Material.LIGHT_BLUE_STAINED_GLASS) {
			return Team.BLUE;
		}
		if (m == Material.ORANGE_STAINED_GLASS) {
			return Team.RED;
		}
		return null;
	}

	private boolean isProtected(Arena a, Location l) {
		int radius = plugin.getConfig().getInt("config.unlimited_glass_radius", 2);
		for (Team team : Team.values()) {
			Location spawn = a.getSpawn(team);
			if (spawn == null || !spawn.getWorld().equals(l.getWorld())) {
				continue;
			}
			if (Math.abs(spawn.getBlockX() - l.getBlockX()) < radius && Math.abs(spawn.getBlockZ() - l.getBlockZ()) < radius) {
				return true;
			}
		}
		return false;
	}
}
