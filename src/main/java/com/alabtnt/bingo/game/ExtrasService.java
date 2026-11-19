package com.alabtnt.bingo.game;

import com.alabtnt.bingo.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.clock.WorldClock;

import java.util.List;
import java.util.Random;

/** Random events, supply drops, weather/daylight and hunter tracking. */
public final class ExtrasService {
	private static int eventTicks;
	private static int supplyTicks;
	private static int trackTicks;
	private static final Random RANDOM = new Random();

	private ExtrasService() {
	}

	public static void reset() {
		eventTicks = 0;
		supplyTicks = 0;
		trackTicks = 0;
	}

	public static void applyWorldRules(BingoGame game) {
		ServerConfig config = game.config();
		ServerLevel level = game.arena().level();
		MinecraftServer server = game.server();
		var rules = level.getGameRules();
		rules.set(GameRules.KEEP_INVENTORY, config.game.keepInventory, server);
		boolean forcedDay = !config.world.daylight.equals("cycle");
		rules.set(GameRules.ADVANCE_TIME, !forcedDay, server);
		rules.set(GameRules.ADVANCE_WEATHER, config.world.weather.equals("cycle"), server);

		try {
			ServerClockManager clocks = server.clockManager();
			Holder<WorldClock> clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
					.getOrThrow(WorldClocks.OVERWORLD);
			if (forcedDay) {
				clocks.setPaused(clock, true);
				clocks.setTotalTicks(clock, config.world.daylight.equals("night") ? 18000 : 1000);
			} else {
				clocks.setPaused(clock, false);
			}
		} catch (Exception ignored) {
			// The clock API is optional; gamerules still work.
		}

		WeatherData weather = level.getWeatherData();
		switch (config.world.weather) {
			case "clear" -> {
				weather.setRaining(false);
				weather.setThundering(false);
				weather.setClearWeatherTime(Integer.MAX_VALUE);
			}
			case "rain" -> {
				weather.setRaining(true);
				weather.setThundering(false);
				weather.setRainTime(Integer.MAX_VALUE);
			}
			case "thunder" -> {
				weather.setRaining(true);
				weather.setThundering(true);
				weather.setRainTime(Integer.MAX_VALUE);
				weather.setThunderTime(Integer.MAX_VALUE);
			}
			default -> level.resetWeatherCycle();
		}
	}

	public static void tick(BingoGame game) {
		if (game.phase() != GamePhase.PLAYING) return;
		ServerConfig config = game.config();
		eventTicks++;
		supplyTicks++;
		trackTicks++;

		if (config.world.randomEvents && eventTicks >= config.world.eventIntervalSeconds * 20) {
			eventTicks = 0;
			trigger(game, "random");
		}
		if (config.world.supplyDrops && supplyTicks >= config.world.supplyDropIntervalSeconds * 20) {
			supplyTicks = 0;
			supplyDrop(game, false);
		}
		if (config.extras.hunterMode && config.extras.huntersTrackRunners && trackTicks >= 20) {
			trackTicks = 0;
			trackHunters(game);
		}
	}

	public static void trigger(BingoGame game, String type) {
		if (game.phase() != GamePhase.PLAYING) return;
		String chosen = type == null || type.equals("random")
				? List.of("mobs", "rain", "teleport", "effects").get(RANDOM.nextInt(4))
				: type;
		Component label = Component.translatableWithFallback("bingo.event." + chosen,
				"Random event: %s", chosen);
		game.message(label);
		switch (chosen) {
			case "mobs" -> mobWave(game);
			case "rain" -> itemRain(game);
			case "teleport" -> randomTeleport(game);
			case "effects" -> randomEffects(game);
			default -> {
			}
		}
	}

	private static void mobWave(BingoGame game) {
		ServerLevel level = game.arena().level();
		for (ServerPlayer player : game.server().getPlayerList().getPlayers()) {
			if (player.level() != level) continue;
			for (int i = 0; i < 3; i++) {
				BlockPos pos = player.blockPosition().offset(RANDOM.nextInt(9) - 4, 1, RANDOM.nextInt(9) - 4);
				if (!level.getBlockState(pos).isAir()) continue;
				EntityType<?> type = i == 0 ? EntityTypes.SKELETON : EntityTypes.ZOMBIE;
				type.spawn(level, pos, EntitySpawnReason.EVENT);
			}
		}
	}

	private static final List<ItemStack> LOOT = List.of(
			new ItemStack(Items.GOLDEN_APPLE, 2),
			new ItemStack(Items.IRON_INGOT, 8),
			new ItemStack(Items.DIAMOND, 2),
			new ItemStack(Items.ARROW, 32),
			new ItemStack(Items.FIREWORK_ROCKET, 16),
			new ItemStack(Items.ENDER_PEARL, 4),
			new ItemStack(Items.COOKED_BEEF, 16),
			new ItemStack(Items.EXPERIENCE_BOTTLE, 8)
	);

	private static void itemRain(BingoGame game) {
		ServerLevel level = game.arena().level();
		for (ServerPlayer player : game.server().getPlayerList().getPlayers()) {
			if (player.level() != level) continue;
			for (int i = 0; i < 5; i++) {
				ItemStack stack = LOOT.get(RANDOM.nextInt(LOOT.size())).copy();
				double x = player.getX() + RANDOM.nextDouble() * 8 - 4;
				double y = player.getY() + 6 + RANDOM.nextInt(4);
				double z = player.getZ() + RANDOM.nextDouble() * 8 - 4;
				level.addFreshEntity(new ItemEntity(level, x, y, z, stack));
			}
		}
	}

	private static void randomTeleport(BingoGame game) {
		ServerLevel level = game.arena().level();
		BlockPos center = game.arena().center();
		for (ServerPlayer player : game.server().getPlayerList().getPlayers()) {
			if (player.level() != level) continue;
			int x = center.getX() + RANDOM.nextInt(400) - 200;
			int z = center.getZ() + RANDOM.nextInt(400) - 200;
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
			player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		}
	}

	private static void randomEffects(BingoGame game) {
		List<Holder<net.minecraft.world.effect.MobEffect>> effects = List.of(
				MobEffects.SPEED, MobEffects.JUMP_BOOST, MobEffects.REGENERATION,
				MobEffects.NIGHT_VISION, MobEffects.SLOW_FALLING);
		for (ServerPlayer player : game.server().getPlayerList().getPlayers()) {
			player.addEffect(new MobEffectInstance(effects.get(RANDOM.nextInt(effects.size())),
					20 * 30, 1, false, true));
		}
	}

	public static void supplyDrop(BingoGame game, boolean force) {
		if (game.phase() != GamePhase.PLAYING && !force) return;
		ServerLevel level = game.arena().level();
		if (level == null) return;
		int drops = 0;
		for (BingoTeam team : game.teams()) {
			List<ServerPlayer> players = game.onlineMembers(team);
			if (players.isEmpty()) continue;
			ServerPlayer anchor = players.get(RANDOM.nextInt(players.size()));
			int x = anchor.blockPosition().getX() + RANDOM.nextInt(60) - 30;
			int z = anchor.blockPosition().getZ() + RANDOM.nextInt(60) - 30;
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
			BlockPos pos = new BlockPos(x, y, z);
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				for (int i = 0; i < 6; i++) {
					chest.setItem(RANDOM.nextInt(chest.getContainerSize()),
							LOOT.get(RANDOM.nextInt(LOOT.size())).copy());
				}
			}
			game.message(Component.translatableWithFallback("bingo.event.supply",
					"Supply drop at %s %s %s", x, y, z));
			drops++;
		}
		if (drops == 0) game.message(Component.translatableWithFallback("bingo.event.supply.none",
				"No players to drop supplies near."));
	}

	private static void trackHunters(BingoGame game) {
		ServerLevel level = game.arena().level();
		for (BingoTeam team : game.teams()) {
			if (!team.role.equals("HUNTER")) continue;
			for (ServerPlayer hunter : game.onlineMembers(team)) {
				ServerPlayer nearest = null;
				double best = Double.MAX_VALUE;
				for (BingoTeam other : game.teams()) {
					if (other.role.equals("HUNTER")) continue;
					for (ServerPlayer runner : game.onlineMembers(other)) {
						if (runner.level() != level) continue;
						double distance = runner.distanceToSqr(hunter);
						if (distance < best) {
							best = distance;
							nearest = runner;
						}
					}
				}
				if (nearest != null) {
					int dx = nearest.blockPosition().getX() - hunter.blockPosition().getX();
					int dz = nearest.blockPosition().getZ() - hunter.blockPosition().getZ();
					String direction = (dz < 0 ? "N" : "S") + (dx > 0 ? "E" : "W");
					hunter.sendOverlayMessage(Component.translatableWithFallback(
							"bingo.hunter.track", "Nearest runner: %s blocks %s",
							(int) Math.sqrt(best), direction));
				}
			}
		}
	}
}
