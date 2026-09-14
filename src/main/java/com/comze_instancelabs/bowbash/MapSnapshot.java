package com.comze_instancelabs.bowbash;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * A block-for-block snapshot of an arena's whole bounding box (its two configured corners),
 * captured fresh the moment a round starts and restored exactly once it ends - covers everything
 * that changed, broken or placed, not just individually tracked changes. Mirrors the
 * save-the-structure/reset-to-baseline approach from the user's fabric-example-mod-26.2 gamemode.
 */
public class MapSnapshot {

	private final Map<Location, BlockData> blocks = new HashMap<>();

	/** Captures every block between {@code pos1} and {@code pos2} (inclusive, any corner order). */
	public void capture(Location pos1, Location pos2) {
		blocks.clear();
		World world = pos1.getWorld();
		int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
		int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
		int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
		int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
		int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
		int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					Location loc = new Location(world, x, y, z);
					blocks.put(loc, loc.getBlock().getBlockData().clone());
				}
			}
		}
	}

	/** Puts every captured block back exactly as it was, then forgets the snapshot. */
	public void restore() {
		for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
			Location loc = entry.getKey();
			if (loc.isWorldLoaded()) {
				loc.getBlock().setBlockData(entry.getValue(), false);
			}
		}
		blocks.clear();
	}

	public boolean isEmpty() {
		return blocks.isEmpty();
	}
}
