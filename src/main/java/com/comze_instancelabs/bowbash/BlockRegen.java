package com.comze_instancelabs.bowbash;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

/**
 * Records the original state of every block an arena's game logic changes so the whole map can be
 * put back exactly as it was found once the game ends. Replaces the old "SmartReset" system.
 */
public class BlockRegen {

	private final Map<Location, BlockData> changed = new LinkedHashMap<>();

	/**
	 * Remembers the block's current state, if it isn't already recorded, before it gets modified.
	 * Call this BEFORE mutating the block.
	 */
	public void record(Location location) {
		Location key = location.getBlock().getLocation();
		if (!changed.containsKey(key)) {
			changed.put(key, key.getBlock().getBlockData().clone());
		}
	}

	/** Restores every recorded block to its original state and clears the record. */
	public void revertAll() {
		for (Map.Entry<Location, BlockData> entry : changed.entrySet()) {
			Location loc = entry.getKey();
			if (loc.isWorldLoaded()) {
				loc.getBlock().setBlockData(entry.getValue(), false);
			}
		}
		changed.clear();
	}

	public int size() {
		return changed.size();
	}
}
