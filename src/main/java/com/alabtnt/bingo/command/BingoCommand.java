package com.alabtnt.bingo.command;

import com.alabtnt.bingo.config.BoConfigs;
import com.alabtnt.bingo.config.ServerConfig;
import com.alabtnt.bingo.game.BingoGame;
import com.alabtnt.bingo.game.GamePhase;
import com.alabtnt.bingo.game.ObjectiveRegistry;
import com.alabtnt.bingo.network.BingoNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class BingoCommand {
	private BingoCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("bingo")
				.then(Commands.literal("join")
						.then(Commands.argument("team", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									for (String id : teamIds()) builder.suggest(id);
									return builder.buildFuture();
								})
								.executes(ctx -> join(ctx, StringArgumentType.getString(ctx, "team")))))
				.then(Commands.literal("leave").executes(BingoCommand::leave))
				.then(Commands.literal("ready").executes(BingoCommand::ready))
				.then(Commands.literal("start")
						.requires(admin())
						.executes(ctx -> start(ctx))
						.then(Commands.literal("force").executes(ctx -> forceStart(ctx))))
				.then(Commands.literal("end")
						.requires(admin())
						.executes(ctx -> end(ctx)))
				.then(Commands.literal("lobby")
						.requires(admin())
						.executes(ctx -> lobby(ctx)))
				.then(Commands.literal("teams")
						.requires(admin())
						.then(Commands.argument("count", IntegerArgumentType.integer(2, 8))
								.executes(ctx -> teams(ctx))))
				.then(Commands.literal("board")
						.executes(BingoCommand::board)
						.then(Commands.argument("index", IntegerArgumentType.integer(1, 8))
								.then(Commands.literal("difficulty")
										.then(Commands.argument("difficulty", StringArgumentType.word())
												.suggests((ctx, b) -> suggest(b, List.of("easy", "medium", "hard", "extreme", "custom")))
												.executes(BingoCommand::boardDifficulty)))))
				.then(Commands.literal("reroll")
						.requires(admin())
						.executes(ctx -> reroll(ctx)))
				.then(Commands.literal("difficulty")
						.requires(admin())
						.then(Commands.argument("difficulty", StringArgumentType.word())
								.suggests((ctx, b) -> suggest(b, List.of("easy", "medium", "hard", "extreme", "custom")))
								.executes(ctx -> difficulty(ctx))))
				.then(Commands.literal("boards")
						.requires(admin())
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 4))
								.executes(BingoCommand::boards)))
				.then(Commands.literal("size")
						.requires(admin())
						.then(Commands.argument("size", IntegerArgumentType.integer(3, 9))
								.executes(ctx -> size(ctx))))
				.then(Commands.literal("pool")
						.requires(admin())
						.then(Commands.argument("pool", StringArgumentType.word())
								.suggests((ctx, b) -> suggest(b, List.of("items", "advancements", "mixed")))
								.executes(ctx -> pool(ctx))))
				.then(Commands.literal("goal")
						.requires(admin())
						.then(Commands.argument("goal", StringArgumentType.word())
								.suggests((ctx, b) -> suggest(b, List.of("lines", "items", "cards")))
								.then(Commands.argument("value", IntegerArgumentType.integer(1, 49))
										.executes(ctx -> goal(ctx)))))
				.then(Commands.literal("distribution")
						.requires(admin())
						.then(Commands.argument("s", IntegerArgumentType.integer(0, 81))
								.then(Commands.argument("a", IntegerArgumentType.integer(0, 81))
										.then(Commands.argument("b", IntegerArgumentType.integer(0, 81))
												.then(Commands.argument("c", IntegerArgumentType.integer(0, 81))
														.then(Commands.argument("d", IntegerArgumentType.integer(0, 81))
																.executes(BingoCommand::distribution)))))))
				.then(Commands.literal("weather")
						.requires(admin())
						.then(Commands.argument("weather", StringArgumentType.word())
								.suggests((ctx, b) -> suggest(b, List.of("cycle", "clear", "rain", "thunder")))
								.executes(BingoCommand::weather)))
				.then(Commands.literal("daylight")
						.requires(admin())
						.then(Commands.argument("daylight", StringArgumentType.word())
								.suggests((ctx, b) -> suggest(b, List.of("cycle", "day", "night")))
								.executes(BingoCommand::daylight)))
				.then(Commands.literal("spawn")
						.requires(admin())
						.then(Commands.argument("dimension", net.minecraft.commands.arguments.IdentifierArgument.id())
								.suggests((ctx, b) -> suggest(b, List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")))
								.executes(BingoCommand::spawn)))
				.then(Commands.literal("kit")
						.requires(admin())
						.then(Commands.argument("kit", StringArgumentType.word())
								.suggests((ctx, b) -> suggest(b, List.of("none", "player", "team")))
								.executes(BingoCommand::kit)))
				.then(Commands.literal("time")
						.requires(admin())
						.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 180))
								.executes(ctx -> time(ctx))))
				.then(Commands.literal("lockout")
						.requires(admin())
						.then(Commands.literal("on").executes(ctx -> bool(ctx, "lockout", true)))
						.then(Commands.literal("off").executes(ctx -> bool(ctx, "lockout", false))))
				.then(Commands.literal("fog")
						.requires(admin())
						.then(Commands.literal("on").executes(ctx -> bool(ctx, "fog", true)))
						.then(Commands.literal("off").executes(ctx -> bool(ctx, "fog", false))))
				.then(Commands.literal("hunter")
						.requires(admin())
						.then(Commands.literal("on").executes(ctx -> bool(ctx, "hunter", true)))
						.then(Commands.literal("off").executes(ctx -> bool(ctx, "hunter", false))))
				.then(Commands.literal("chest").executes(BingoCommand::chest))
				.then(Commands.literal("supply")
						.requires(admin())
						.executes(ctx -> supply(ctx)))
				.then(Commands.literal("event")
						.requires(admin())
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((ctx, b) -> suggest(b, List.of("mobs", "rain", "teleport", "effects", "random")))
								.executes(ctx -> event(ctx))))
				.then(Commands.literal("preset")
						.requires(admin())
						.then(Commands.argument("preset", StringArgumentType.word())
								.suggests((ctx, b) -> suggest(b, List.of("classic", "missions", "mixed", "speedrun", "nether", "end", "hunter")))
								.executes(ctx -> preset(ctx))))
				.then(Commands.literal("stack").executes(BingoCommand::stackInfo))
				.then(Commands.literal("info").executes(BingoCommand::info))
				.then(Commands.literal("reload")
						.requires(admin())
						.executes(ctx -> reload(ctx))));
	}

	private static java.util.function.Predicate<CommandSourceStack> admin() {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	private static List<String> teamIds() {
		BingoGame game = BingoGame.get();
		return game == null ? List.of() : game.teams().stream().map(team -> team.id).toList();
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
			com.mojang.brigadier.suggestion.SuggestionsBuilder builder, List<String> values) {
		for (String value : values) builder.suggest(value);
		return builder.buildFuture();
	}

	public static void cycleTeam(ServerPlayer player) {
		BingoGame game = BingoGame.get();
		if (game == null) return;
		List<String> ids = teamIds();
		if (ids.isEmpty()) return;
		var current = game.teamOf(player);
		int index = current == null ? -1 : ids.indexOf(current.id);
		String next = ids.get(Math.floorMod(index + 1, ids.size()));
		game.joinTeam(player, next);
	}

	private static int join(CommandContext<CommandSourceStack> ctx, String team) {
		ServerPlayer player = ctx.getSource().getPlayer();
		BingoGame game = BingoGame.get();
		if (player == null || game == null) return 0;
		if (!game.joinTeam(player, team)) {
			ctx.getSource().sendFailure(Component.translatableWithFallback(
					"bingo.cmd.join.failed", "Can't join team '%s' right now.", team));
			return 0;
		}
		return 1;
	}

	private static int leave(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		BingoGame game = BingoGame.get();
		if (player == null || game == null) return 0;
		game.leaveTeam(player);
		return 1;
	}

	private static int ready(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		BingoGame game = BingoGame.get();
		if (player == null || game == null) return 0;
		game.ready(player);
		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		if (game.phase() == GamePhase.LOBBY) {
			game.startCountdown();
		} else {
			ctx.getSource().sendFailure(Component.translatableWithFallback("bingo.cmd.start.busy",
					"A round is already running."));
			return 0;
		}
		return 1;
	}

	private static int forceStart(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		game.forceStart();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.start.force", "Round force-started."), true);
		return 1;
	}

	private static int end(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		game.endRound(null, "command");
		return 1;
	}

	private static int lobby(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		game.toLobby();
		return 1;
	}

	private static int teams(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.lobby.teamCount = IntegerArgumentType.getInteger(ctx, "count");
		BoConfigs.saveServer();
		BingoGame game = BingoGame.get();
		if (game != null) game.setTeamCount(config.lobby.teamCount);
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.teams", "Team count set to %s.", config.lobby.teamCount), true);
		return 1;
	}

	private static int board(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		if (player != null) {
			BingoNetworking.openBoard(player);
			return 1;
		}
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("bingo.cmd.board.info",
				"Boards: %s x %s, goal %s %s, pool %s, difficulty %s",
				game.boards().size(), game.config().board.size, game.config().board.goal,
				game.config().board.goalValue, game.config().board.pool, game.config().board.difficulty), false);
		return 1;
	}

	private static int boardDifficulty(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
		String difficulty = StringArgumentType.getString(ctx, "difficulty");
		while (config.board.boardDifficulties.size() <= index) config.board.boardDifficulties.add("");
		config.board.boardDifficulties.set(index, difficulty);
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.board.difficulty", "Board %s difficulty set to %s.",
				index + 1, difficulty), true);
		return 1;
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		String dimension = net.minecraft.commands.arguments.IdentifierArgument
				.getId(ctx, "dimension").toString();
		config.game.spawnDimension = dimension;
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.spawn", "Spawn dimension set to %s (applies to the next round).", dimension), true);
		return 1;
	}

	private static int kit(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		String kit = StringArgumentType.getString(ctx, "kit");
		config.game.playerKit = kit.equals("player");
		config.game.teamKit = kit.equals("team");
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.kit", "Starting kit set to %s.", kit), true);
		return 1;
	}

	private static int distribution(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.board.distribution = List.of(
				IntegerArgumentType.getInteger(ctx, "s"),
				IntegerArgumentType.getInteger(ctx, "a"),
				IntegerArgumentType.getInteger(ctx, "b"),
				IntegerArgumentType.getInteger(ctx, "c"),
				IntegerArgumentType.getInteger(ctx, "d"));
		config.board.difficulty = "custom";
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.distribution", "Custom distribution set to %s.", config.board.distribution), true);
		return 1;
	}

	private static int weather(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.world.weather = StringArgumentType.getString(ctx, "weather");
		BoConfigs.saveServer();
		BingoGame game = BingoGame.get();
		if (game != null) com.alabtnt.bingo.game.ExtrasService.applyWorldRules(game);
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.weather", "Weather set to %s.", config.world.weather), true);
		return 1;
	}

	private static int daylight(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.world.daylight = StringArgumentType.getString(ctx, "daylight");
		BoConfigs.saveServer();
		BingoGame game = BingoGame.get();
		if (game != null) com.alabtnt.bingo.game.ExtrasService.applyWorldRules(game);
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.daylight", "Daylight set to %s.", config.world.daylight), true);
		return 1;
	}

	private static int stackInfo(CommandContext<CommandSourceStack> ctx) {
		var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 128);
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.stack", "Stack limit: %s | test stack: %s",
				stack.getMaxStackSize(), stack.getCount()), false);
		return 1;
	}

	private static int reroll(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		game.rerollBoards();
		game.sync();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.reroll", "Board rerolled (seed %s).", game.boardSeed()), true);
		return 1;
	}

	private static int difficulty(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.board.difficulty = StringArgumentType.getString(ctx, "difficulty");
		config.board.boardDifficulties.clear();
		if (!config.board.difficulty.equals("custom")) config.board.distribution.clear();
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.difficulty", "Difficulty set to %s.", config.board.difficulty), true);
		return 1;
	}

	private static int boards(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.board.boards = IntegerArgumentType.getInteger(ctx, "count");
		BoConfigs.saveServer();
		BingoGame game = BingoGame.get();
		if (game != null) game.rerollBoards();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.boards", "Board count set to %s.", config.board.boards), true);
		return 1;
	}

	private static int size(CommandContext<CommandSourceStack> ctx) {
		int size = IntegerArgumentType.getInteger(ctx, "size");
		if (size % 2 == 0) {
			ctx.getSource().sendFailure(Component.translatableWithFallback(
					"bingo.cmd.size", "Board size must be odd."));
			return 0;
		}
		ServerConfig config = BoConfigs.server();
		config.board.size = size;
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.size.ok", "Board size set to %s.", size), true);
		return 1;
	}

	private static int pool(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.board.pool = StringArgumentType.getString(ctx, "pool");
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.pool", "Objective pool set to %s.", config.board.pool), true);
		return 1;
	}

	private static int goal(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.board.goal = StringArgumentType.getString(ctx, "goal");
		config.board.goalValue = IntegerArgumentType.getInteger(ctx, "value");
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.goal", "Goal set to %s %s.", config.board.goal, config.board.goalValue), true);
		return 1;
	}

	private static int time(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		config.game.timeLimitMinutes = IntegerArgumentType.getInteger(ctx, "minutes");
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.time", "Time limit set to %s minutes.", config.game.timeLimitMinutes), true);
		return 1;
	}

	private static int bool(CommandContext<CommandSourceStack> ctx, String key, boolean value) {
		ServerConfig config = BoConfigs.server();
		switch (key) {
			case "lockout" -> config.board.lockout = value;
			case "fog" -> config.board.fog = value;
			case "hunter" -> config.extras.hunterMode = value;
			default -> {
			}
		}
		BoConfigs.saveServer();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.bool", "%s set to %s.", key, value), true);
		return 1;
	}

	private static int chest(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		BingoGame game = BingoGame.get();
		if (player == null || game == null) return 0;
		game.openTeamChest(player);
		return 1;
	}

	private static int supply(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		com.alabtnt.bingo.game.ExtrasService.supplyDrop(game, true);
		return 1;
	}

	private static int event(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		com.alabtnt.bingo.game.ExtrasService.trigger(game, StringArgumentType.getString(ctx, "type"));
		return 1;
	}

	private static int preset(CommandContext<CommandSourceStack> ctx) {
		ServerConfig config = BoConfigs.server();
		String preset = StringArgumentType.getString(ctx, "preset");
		applyPreset(config, preset);
		BoConfigs.saveServer();
		BingoGame game = BingoGame.get();
		if (game != null) game.rerollBoards();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.preset", "Preset '%s' applied.", preset), true);
		return 1;
	}

	public static void applyPreset(ServerConfig config, String preset) {
		config.preset = preset;
		// Reset the options that presets own, so switching presets is predictable.
		config.game.pvp = false;
		config.game.elytra = false;
		config.game.infiniteFireworks = false;
		config.game.spawnDimension = "minecraft:overworld";
		config.game.playerKit = false;
		config.game.teamKit = false;
		config.extras.hunterMode = false;
		config.board.boards = 1;
		config.board.lockout = false;
		config.board.fog = false;
		config.board.boardDifficulties.clear();
		config.board.distribution.clear();
		switch (preset) {
			case "classic" -> {
				config.board.pool = "items";
				config.board.goal = "lines";
				config.board.goalValue = 1;
				config.board.lockout = false;
				config.board.fog = false;
				config.board.difficulty = "easy";
				config.game.timeLimitMinutes = 30;
			}
			case "missions" -> {
				config.board.pool = "advancements";
				config.board.goal = "lines";
				config.board.goalValue = 1;
				config.board.lockout = false;
				config.board.fog = false;
				config.board.difficulty = "easy";
				config.game.timeLimitMinutes = 30;
			}
			case "speedrun" -> {
				config.board.pool = "mixed";
				config.board.goal = "lines";
				config.board.goalValue = 1;
				config.board.lockout = true;
				config.board.fog = false;
				config.board.difficulty = "medium";
				config.game.timeLimitMinutes = 15;
				config.game.elytra = true;
				config.game.infiniteFireworks = true;
			}
			case "nether" -> {
				config.board.pool = "mixed";
				config.board.goal = "lines";
				config.board.goalValue = 1;
				config.board.difficulty = "medium";
				config.game.timeLimitMinutes = 30;
				config.game.spawnDimension = "minecraft:the_nether";
				config.game.elytra = true;
				config.game.infiniteFireworks = true;
			}
			case "end" -> {
				config.board.pool = "mixed";
				config.board.goal = "lines";
				config.board.goalValue = 1;
				config.board.difficulty = "hard";
				config.game.timeLimitMinutes = 30;
				config.game.spawnDimension = "minecraft:the_end";
				config.game.elytra = true;
				config.game.infiniteFireworks = true;
			}
			case "hunter" -> {
				config.board.pool = "mixed";
				config.board.goal = "lines";
				config.board.goalValue = 1;
				config.board.difficulty = "medium";
				config.game.timeLimitMinutes = 30;
				config.extras.hunterMode = true;
				config.extras.runnerLives = 3;
				config.game.pvp = true;
			}
			default -> {
				config.board.pool = "mixed";
				config.board.goal = "lines";
				config.board.goalValue = 1;
				config.board.difficulty = "easy";
				config.game.timeLimitMinutes = 30;
			}
		}
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game == null) return 0;
		long ours = ctx.getSource().getServer().getAdvancements().getAllAdvancements().stream()
				.filter(h -> h.id().getNamespace().equals("bingo_ourselves")).count();
		long vanilla = ctx.getSource().getServer().getAdvancements().getAllAdvancements().stream()
				.filter(h -> h.id().getNamespace().equals("minecraft"))
				.filter(h -> !h.id().getPath().startsWith("recipes/"))
				.filter(h -> h.value().display().isPresent()).count();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("bingo.cmd.info",
				"Phase: %s | round %s | teams %s | boards %s | pool %s (%s items, %s advancements) | registry ours=%s vanilla=%s | arena %s",
				game.phase(), game.round(), game.teams().size(), game.boards().size(),
				game.config().board.pool, ObjectiveRegistry.items().size(),
				ObjectiveRegistry.advancements().size(), ours, vanilla,
				game.arena().level().dimension().identifier()), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		BingoGame game = BingoGame.get();
		if (game != null) game.reloadConfig();
		ObjectiveRegistry.reload(ctx.getSource().getServer());
		if (game != null) game.rerollBoards();
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"bingo.cmd.reload", "Configuration and objective data reloaded."), true);
		return 1;
	}
}
