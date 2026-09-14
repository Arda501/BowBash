package com.comze_instancelabs.bowbash;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * A block-for-block snapshot of an arena's whole bounding box (its two configured corners),
 * explicitly captured by an admin ({@code /bb savemap}) and persisted to disk so it survives a
 * restart - restored exactly at the end of every round from then on, covering everything that
 * changed, broken or placed. Mirrors the save-the-structure/reset-to-baseline approach from the
 * user's fabric-example-mod-26.2 gamemode (and its own Recored Paper port).
 */
public class MapSnapshot {

	private Region region;
	private final List<BlockData> blocks = new ArrayList<>();

	private record Region(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	}

	public boolean isCaptured() {
		return region != null && !blocks.isEmpty();
	}

	/** Captures every block between {@code pos1} and {@code pos2} (inclusive, any corner order), in a fixed order also used by {@link #restore}. */
	public void capture(org.bukkit.Location pos1, org.bukkit.Location pos2) {
		World world = pos1.getWorld();
		int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
		int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
		int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
		int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
		int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
		int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
		region = new Region(world, minX, minY, minZ, maxX, maxY, maxZ);

		blocks.clear();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					blocks.add(world.getBlockAt(x, y, z).getBlockData().clone());
				}
			}
		}
	}

	/** Puts every captured block back exactly as it was. A no-op if nothing was ever captured/loaded. */
	public void restore() {
		if (!isCaptured()) {
			return;
		}
		int i = 0;
		for (int x = region.minX(); x <= region.maxX(); x++) {
			for (int y = region.minY(); y <= region.maxY(); y++) {
				for (int z = region.minZ(); z <= region.maxZ(); z++) {
					Block block = region.world().getBlockAt(x, y, z);
					block.setBlockData(blocks.get(i++), false);
				}
			}
		}
	}

	public void save(File file) throws IOException {
		if (!isCaptured()) {
			return;
		}
		Files.createDirectories(file.getParentFile().toPath());
		try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			writer.write(region.world().getName() + "," + region.minX() + "," + region.minY() + "," + region.minZ()
					+ "," + region.maxX() + "," + region.maxY() + "," + region.maxZ());
			writer.newLine();
			for (BlockData data : blocks) {
				writer.write(data.getAsString());
				writer.newLine();
			}
		}
	}

	/** @return {@code false} if the file doesn't exist or its world isn't loaded (loads nothing either way). */
	public boolean load(File file) throws IOException {
		if (!file.exists()) {
			return false;
		}
		try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			String header = reader.readLine();
			if (header == null) {
				return false;
			}
			String[] parts = header.split(",");
			World world = Bukkit.getWorld(parts[0]);
			if (world == null) {
				return false;
			}
			region = new Region(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
					Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
			blocks.clear();
			String line;
			while ((line = reader.readLine()) != null) {
				blocks.add(Bukkit.createBlockData(line));
			}
			return true;
		}
	}
}
