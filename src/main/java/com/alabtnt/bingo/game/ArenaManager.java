package com.alabtnt.bingo.game;

import com.alabtnt.bingo.BingoOurselves;
import com.alabtnt.bingo.config.ServerConfig;
import com.alabtnt.bingo.mixin.MinecraftServerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Owns the Bingo arena dimension. A new dimension with a random seed is created
 * for every round when possible; if the server refuses dynamic dimensions, the
 * arena falls back to a far-away random region of a fixed arena dimension.
 */
public final class ArenaManager {
	public static final Identifier ARENA_ID = Identifier.fromNamespaceAndPath("bingo_ourselves", "arena");
	public static final ResourceKey<Level> ARENA_KEY = ResourceKey.create(Registries.DIMENSION, ARENA_ID);
	private static final String FALLBACK_ID = "bingo_ourselves:arena_fallback";

	private final MinecraftServer server;
	private ServerConfig config;
	private ServerLevel level;
	private long seed;
	private BlockPos center = BlockPos.ZERO;
	private boolean dynamicSupported = true;
	private int roundCounter;

	public ArenaManager(MinecraftServer server, ServerConfig config) {
		this.server = server;
		this.config = config;
	}

	private ServerLevel sourceLevel() {
		String dimension = config.game.spawnDimension == null ? "" : config.game.spawnDimension;
		if (dimension.contains("the_nether")) {
			ServerLevel nether = server.getLevel(Level.NETHER);
			if (nether != null) return nether;
		}
		if (dimension.contains("the_end")) {
			ServerLevel end = server.getLevel(Level.END);
			if (end != null) return end;
		}
		return server.overworld();
	}

	public void setConfig(ServerConfig config) {
		this.config = config;
	}

	public ServerLevel level() {
		return level;
	}

	public long seed() {
		return seed;
	}

	public BlockPos center() {
		return center;
	}

	public boolean dynamicSupported() {
		return dynamicSupported;
	}

	/** Called once on server start; loads an existing arena or creates one. */
	public void initialize() {
		this.seed = new Random().nextLong();
		ServerLevel existing = server.getLevel(ARENA_KEY);
		if (existing != null) {
			this.level = existing;
			this.center = new BlockPos(0, 100, 0);
			return;
		}
		try {
			this.level = createLevel(ARENA_KEY, seed);
			BingoOurselves.LOGGER.info("[Bingo] Created arena dimension {} (seed {})", ARENA_KEY.identifier(), seed);
		} catch (Throwable t) {
			BingoOurselves.LOGGER.error("[Bingo] Dynamic dimension creation failed; falling back to a fixed region", t);
			dynamicSupported = false;
			loadFallback();
		}
	}

	private void loadFallback() {
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(FALLBACK_ID));
		ServerLevel existing = server.getLevel(key);
		if (existing != null) {
			level = existing;
			return;
		}
		try {
			level = createLevel(key, seed);
		} catch (Throwable t) {
			BingoOurselves.LOGGER.error("[Bingo] Could not create fallback arena; using the overworld", t);
			level = server.overworld();
			dynamicSupported = false;
		}
	}

	private ServerLevel createLevel(ResourceKey<Level> key, long seed) {
		MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
		ServerLevel source = sourceLevel();
		ServerLevel overworld = server.overworld();
		LevelStem stem = new LevelStem(source.dimensionTypeRegistration(),
				source.getChunkSource().getGenerator());
		DerivedLevelData data = new DerivedLevelData(server.getWorldData(), (net.minecraft.world.level.storage.ServerLevelData) overworld.getLevelData());
		ServerLevel created = new ServerLevel(server, accessor.bingo$executor(), accessor.bingo$storage(),
				data, key, stem, false, seed, List.of(), true);
		accessor.bingo$levels().put(key, created);
		created.setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(
				key, new BlockPos(0, 100, 0), 0f, 0f));
		return created;
	}

	/**
	 * Prepares a fresh arena for the next round. Returns true when the world seed
	 * changed (a new dimension was generated).
	 */
	public boolean newRound() {
		roundCounter++;
		if (!dynamicSupported || level == null) {
			pickFallbackCenter();
			return false;
		}
		BlockPos lobby = new BlockPos((int) config.lobby.x, (int) config.lobby.y, (int) config.lobby.z);
		// Pull everyone out before removing the level.
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (player.level() == level) {
				player.teleportTo(server.overworld(), lobby.getX() + 0.5, lobby.getY(), lobby.getZ() + 0.5,
						java.util.Set.of(), 0f, 0f, true);
			}
		}
		removeLevel();
		this.seed = new Random().nextLong();
		try {
			this.level = createLevel(ARENA_KEY, seed);
			this.center = new BlockPos(0, 100, 0);
			BingoOurselves.LOGGER.info("[Bingo] New arena seed {} (round {})", seed, roundCounter);
			return true;
		} catch (Throwable t) {
			BingoOurselves.LOGGER.error("[Bingo] Could not recreate arena; falling back to a random region", t);
			dynamicSupported = false;
			loadFallback();
			pickFallbackCenter();
			return false;
		}
	}

	private void removeLevel() {
		if (level == null || level == server.overworld()) return;
		MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
		accessor.bingo$levels().remove(level.dimension(), level);
		level.noSave = true;
		try {
			level.close();
		} catch (IOException e) {
			BingoOurselves.LOGGER.warn("[Bingo] Error closing old arena", e);
		}
		Path path = accessor.bingo$storage().getDimensionPath(level.dimension());
		try {
			if (Files.exists(path)) {
				try (var walk = Files.walk(path)) {
					walk.sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException ignored) {
						}
					});
				}
			}
		} catch (IOException e) {
			BingoOurselves.LOGGER.warn("[Bingo] Could not delete old arena files {}", path, e);
		}
		level = null;
	}

	private void pickFallbackCenter() {
		if (level == null) level = server.overworld();
		Random random = new Random();
		int distance = 20_000 + random.nextInt(80_000);
		int angle = random.nextInt(360);
		int x = (int) (Math.cos(Math.toRadians(angle)) * distance);
		int z = (int) (Math.sin(Math.toRadians(angle)) * distance);
		center = new BlockPos(x, 100, z);
	}

	/** Computes team spawn positions around the current centre. */
	public BlockPos[] spawnPositions(int teams, int distanceChunks) {
		BlockPos[] result = new BlockPos[teams];
		int radius = Math.max(32, distanceChunks * 16);
		for (int i = 0; i < teams; i++) {
			double angle = (Math.PI * 2 / teams) * i;
			int x = center.getX() + (int) (Math.cos(angle) * radius);
			int z = center.getZ() + (int) (Math.sin(angle) * radius);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
			result[i] = new BlockPos(x, y, z);
			level.getChunk(x >> 4, z >> 4);
		}
		return result;
	}

	public void teleport(ServerPlayer player, BlockPos pos) {
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
				java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		player.setGameMode(GameType.SURVIVAL);
	}
}
