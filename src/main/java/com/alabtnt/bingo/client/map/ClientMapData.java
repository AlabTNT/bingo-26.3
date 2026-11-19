package com.alabtnt.bingo.client.map;

import com.alabtnt.bingo.client.ClientBingoState;
import com.alabtnt.bingo.game.GamePhase;
import com.alabtnt.bingo.network.BingoStatePayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side terrain cache for the self-built minimap / world map.
 *
 * <p>Colors are sampled from loaded client chunks and cached per block column.
 * The cache key includes the dimension and the round's arena seed, so every
 * fresh Bingo world automatically gets a fresh map — no manual map deletion.</p>
 */
public final class ClientMapData {
	private static final Map<Long, Integer> COLORS = new HashMap<>();
	private static String key = "";

	private ClientMapData() {
	}

	public static void ensureKey(ClientLevel level, BingoStatePayload state) {
		if (level == null) return;
		String dimension = level.dimension().identifier().toString();
		long seed = 0L;
		if (state != null && (state.phase() == GamePhase.PLAYING.ordinal()
				|| state.phase() == GamePhase.COUNTDOWN.ordinal())) {
			seed = state.arenaSeed();
		}
		String next = dimension + ":" + seed;
		if (!next.equals(key)) {
			key = next;
			COLORS.clear();
		}
	}

	public static void clear() {
		COLORS.clear();
		key = "";
	}

	/** Samples missing columns around the given center. */
	public static void ensure(ClientLevel level, int centerX, int centerZ, int radius, int step) {
		if (level == null) return;
		int safeStep = Math.max(1, step);
		for (int x = centerX - radius; x <= centerX + radius; x += safeStep) {
			for (int z = centerZ - radius; z <= centerZ + radius; z += safeStep) {
				long packed = pack(x, z);
				if (COLORS.containsKey(packed)) continue;
				if (!level.hasChunk(x >> 4, z >> 4)) continue;
				COLORS.put(packed, sample(level, x, z));
			}
		}
	}

	public static int colorAt(ClientLevel level, int x, int z, int step) {
		if (level == null) return 0xFF202020;
		int grid = Math.max(1, step);
		int gx = Math.floorDiv(x, grid) * grid;
		int gz = Math.floorDiv(z, grid) * grid;
		Integer color = COLORS.get(pack(gx, gz));
		if (color == null) {
			if (!level.hasChunk(gx >> 4, gz >> 4)) return 0xFF101418;
			color = sample(level, gx, gz);
			COLORS.put(pack(gx, gz), color);
		}
		return color;
	}

	private static int sample(ClientLevel level, int x, int z) {
		try {
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			int guard = 0;
			while ((state.isAir()) && guard++ < 4 && y > level.getMinY()) {
				y--;
				state = level.getBlockState(new BlockPos(x, y, z));
			}
			MapColor mapColor = state.getMapColor(level, new BlockPos(x, y, z));
			int rgb = mapColor == null || mapColor == MapColor.NONE ? 0x777777 : mapColor.col;
			// Simple height shading makes hills readable without textures.
			float shade = 0.78f + Math.max(0f, Math.min(0.35f, (y - 64) / 220f));
			int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * shade);
			int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * shade);
			int b = (int) Math.min(255, (rgb & 0xFF) * shade);
			return 0xFF000000 | (r << 16) | (g << 8) | b;
		} catch (Exception ignored) {
			return 0xFF202020;
		}
	}

	private static long pack(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}
}
