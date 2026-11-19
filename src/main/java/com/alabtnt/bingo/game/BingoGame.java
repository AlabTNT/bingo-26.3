package com.alabtnt.bingo.game;

import com.alabtnt.bingo.BingoOurselves;
import com.alabtnt.bingo.config.BoConfigs;
import com.alabtnt.bingo.config.ServerConfig;
import com.alabtnt.bingo.network.BingoNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative Bingo round state. */
public final class BingoGame {
	private static BingoGame instance;

	private final MinecraftServer server;
	private ServerConfig config;
	private final ArenaManager arena;

	private GamePhase phase = GamePhase.LOBBY;
	private int countdown;
	private int timeLeft;
	private int totalTime;
	private int round;

	private long boardSeed;
	private final List<BingoBoard> boards = new ArrayList<>();
	private final Map<String, BingoTeam> teams = new LinkedHashMap<>();
	private final Map<UUID, String> memberships = new HashMap<>();
	private final Set<UUID> readyPlayers = new HashSet<>();
	private final Map<String, SimpleContainer> teamChests = new HashMap<>();

	private final Set<UUID> spectators = new HashSet<>();
	private String winnerTeam;
	private String endReason;

	private long ticks;
	private int completionTimer;
	private int syncTimer;
	private int eventTimer;
	private int supplyTimer;

	private BingoGame(MinecraftServer server) {
		this.server = server;
		this.config = BoConfigs.server();
		this.arena = new ArenaManager(server, config);
	}

	public static BingoGame create(MinecraftServer server) {
		instance = new BingoGame(server);
		instance.initialize();
		return instance;
	}

	public static BingoGame get() {
		return instance;
	}

	public static void clear() {
		instance = null;
	}

	private void initialize() {
		arena.initialize();
		ObjectiveRegistry.reload(server);
		createTeams(config.lobby.teamCount);
		if (config.lobby.buildPlatform) {
			LobbyService.ensureLobby(server, config);
		}
		loadState();
		rerollBoards();
		BingoOurselves.LOGGER.info("[Bingo] Game ready: phase={}, teams={}, arena={}",
				phase, teams.size(), arena.level().dimension().identifier());
	}

	public void reloadConfig() {
		this.config = BoConfigs.reloadServer();
		this.arena.setConfig(this.config);
	}

	public void setTeamCount(int count) {
		if (phase != GamePhase.LOBBY) return;
		java.util.List<java.util.UUID> players = new ArrayList<>(memberships.keySet());
		createTeams(count);
		int index = 0;
		for (java.util.UUID id : players) {
			BingoTeam team = teams.values().stream().toList().get(index % teams.size());
			index++;
			team.memberIds.add(id.toString());
			memberships.put(id, team.id);
		}
		updateReadyFlags();
		sync();
	}

	public MinecraftServer server() {
		return server;
	}

	public ServerConfig config() {
		return config;
	}

	public ArenaManager arena() {
		return arena;
	}

	public GamePhase phase() {
		return phase;
	}

	public int countdown() {
		return countdown;
	}

	public int timeLeftTicks() {
		return timeLeft;
	}

	public int totalTimeTicks() {
		return totalTime;
	}

	public int round() {
		return round;
	}

	public String winnerTeam() {
		return winnerTeam;
	}

	public String endReason() {
		return endReason;
	}

	public long boardSeed() {
		return boardSeed;
	}

	public Collection<BingoTeam> teams() {
		return teams.values();
	}

	public BingoTeam team(String id) {
		return id == null ? null : teams.get(id);
	}

	public BingoTeam teamOf(UUID playerId) {
		return teams.get(memberships.get(playerId));
	}

	public BingoTeam teamOf(ServerPlayer player) {
		return teamOf(player.getUUID());
	}

	public List<BingoBoard> boards() {
		return boards;
	}

	public BingoBoard boardFor(String teamId) {
		if (boards.isEmpty()) return null;
		if (boards.size() == 1) return boards.get(0);
		int index = teamIndex(teamId);
		return boards.get(Math.floorMod(index, boards.size()));
	}

	public int teamIndex(String teamId) {
		int index = 0;
		for (String id : teams.keySet()) {
			if (id.equals(teamId)) return index;
			index++;
		}
		return 0;
	}

	public SimpleContainer teamChest(String teamId) {
		return teamChests.computeIfAbsent(teamId, id -> new SimpleContainer(27));
	}

	public void sync() {
		BingoNetworking.broadcastState(this);
	}

	public void message(Component component) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(component);
		}
	}

	private void createTeams(int count) {
		teams.clear();
		List<TeamPalette.Entry> entries = TeamPalette.ENTRIES;
		for (int i = 0; i < Math.min(count, entries.size()); i++) {
			TeamPalette.Entry entry = entries.get(i);
			BingoTeam team = new BingoTeam(entry.id(), entry.id(), entry.color());
			team.lives = config.extras.runnerLives;
			teams.put(team.id, team);
			teamChests.put(team.id, new SimpleContainer(27));
		}
		if (!teams.isEmpty()) {
			teams.values().iterator().next().role = "RUNNER";
		}
	}

	/** Adds a player to a team (lobby only). */
	public boolean joinTeam(ServerPlayer player, String teamId) {
		BingoTeam team = teams.get(teamId);
		if (team == null) return false;
		if (phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN) return false;
		removePlayer(player.getUUID());
		team.memberIds.add(player.getUUID().toString());
		memberships.put(player.getUUID(), teamId);
		player.sendSystemMessage(Component.translatableWithFallback(
				"bingo.team.joined", "You joined team %s", Component.translatable("bingo.team." + teamId)));
		sync();
		return true;
	}

	private void removePlayer(UUID playerId) {
		String old = memberships.remove(playerId);
		BingoTeam oldTeam = teams.get(old);
		if (oldTeam != null) oldTeam.memberIds.remove(playerId.toString());
		readyPlayers.remove(playerId);
	}

	public List<ServerPlayer> onlineMembers(BingoTeam team) {
		List<ServerPlayer> result = new ArrayList<>();
		for (String id : team.memberIds) {
			ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(id));
			if (player != null) result.add(player);
		}
		return result;
	}

	public void onJoin(ServerPlayer player) {
		if (phase == GamePhase.PLAYING) {
			BingoTeam team = teamOf(player);
			if (team == null) {
				player.setGameMode(GameType.SPECTATOR);
			} else {
				BlockPos spawn = LobbyService.spawnFor(team, config, arena);
				arena.teleport(player, spawn);
			}
		} else {
			LobbyService.teleportToLobby(player, config, server);
			if (config.lobby.giveLobbyItems) {
				LobbyService.giveLobbyItems(player);
			}
		}
		sync();
	}

	public void leaveTeam(ServerPlayer player) {
		removePlayer(player.getUUID());
		player.sendSystemMessage(Component.translatableWithFallback("bingo.team.left", "You left your team."));
		sync();
	}

	public void onLeave(ServerPlayer player) {
		readyPlayers.remove(player.getUUID());
		updateReadyFlags();
		sync();
	}

	public void ready(ServerPlayer player) {
		if (phase != GamePhase.LOBBY) return;
		if (memberships.get(player.getUUID()) == null) {
			player.sendSystemMessage(Component.translatableWithFallback(
					"bingo.ready.no_team", "Join a team before readying up."));
			return;
		}
		if (!readyPlayers.add(player.getUUID())) {
			readyPlayers.remove(player.getUUID());
		}
		updateReadyFlags();
		sync();
		if (allReady() && server.getPlayerList().getPlayerCount() >= config.lobby.minPlayers) {
			startCountdown();
		}
	}

	private void updateReadyFlags() {
		for (BingoTeam team : teams.values()) {
			team.ready = !team.memberIds.isEmpty() && team.memberIds.stream()
					.map(UUID::fromString).allMatch(readyPlayers::contains);
		}
	}

	public boolean allReady() {
		boolean any = false;
		for (BingoTeam team : teams.values()) {
			if (!team.memberIds.isEmpty()) {
				any = true;
				if (!team.ready) return false;
			}
		}
		return any;
	}

	public void startCountdown() {
		if (phase != GamePhase.LOBBY) return;
		if (teams.values().stream().noneMatch(team -> !team.memberIds.isEmpty())) {
			message(Component.translatableWithFallback("bingo.start.no_players", "No players are on a team."));
			return;
		}
		phase = GamePhase.COUNTDOWN;
		countdown = config.lobby.countdownSeconds * 20;
		message(Component.translatableWithFallback("bingo.start.countdown", "Bingo starts in %s seconds!",
				config.lobby.countdownSeconds));
		sync();
	}

	public void forceStart() {
		startRound();
	}

	public void rerollBoards() {
		boardSeed = new Random().nextLong();
		boards.clear();
		for (int i = 0; i < config.board.boards; i++) {
			String difficulty = i < config.board.boardDifficulties.size()
					? config.board.boardDifficulties.get(i)
					: config.board.difficulty;
			BingoBoard board = ObjectiveRegistry.generate(i, config.board.size, difficulty,
					config.board.distribution, config.board.pool, boardSeed + i * 7919L);
			if (config.board.fog) {
				for (BingoTeam team : teams.values()) {
					int revealed = Math.max(1, config.board.fogInitialRevealed);
					int center = board.cellCount() / 2;
					for (int c = 0; c < revealed; c++) {
						board.reveal(center, team.id);
						if (c > 0) board.reveal((center + c) % board.cellCount(), team.id);
					}
				}
			} else {
				for (BingoTeam team : teams.values()) {
					board.revealAll(team.id);
				}
			}
			boards.add(board);
		}
	}

	public void startRound() {
		if (phase == GamePhase.PLAYING) return;
		readyPlayers.clear();
		updateReadyFlags();
		arena.newRound();
		rerollBoards();
		ExtrasService.reset();
		ExtrasService.applyWorldRules(this);
		var border = arena.level().getWorldBorder();
		border.setCenter(arena.center().getX() + 0.5, arena.center().getZ() + 0.5);
		border.setSize(Math.max(256, config.world.borderDiameter));
		round++;
		winnerTeam = null;
		endReason = null;
		timeLeft = config.game.timeLimitMinutes * 60 * 20;
		totalTime = timeLeft;
		phase = GamePhase.PLAYING;
		for (BingoTeam team : teams.values()) {
			team.score = 0;
			team.lines = 0;
			team.cards = 0;
			team.ready = false;
			team.lives = config.extras.runnerLives;
			team.role = "RUNNER";
		}
		if (config.extras.hunterMode) {
			BingoTeam hunter = config.extras.hunterTeam != null && !config.extras.hunterTeam.isEmpty()
					? teams.get(config.extras.hunterTeam) : null;
			if (hunter == null) {
				for (BingoTeam team : teams.values()) {
					if (!team.memberIds.isEmpty()) hunter = team;
				}
			}
			if (hunter != null) {
				hunter.role = "HUNTER";
				hunter.lives = -1;
			}
		}
		spectators.clear();
		BlockPos[] spawns = arena.spawnPositions(teams.size(), config.game.spawnDistanceChunks);
		int index = 0;
		for (BingoTeam team : teams.values()) {
			team.spawnIndex = index;
			for (ServerPlayer player : onlineMembers(team)) {
				resetPlayer(player);
				arena.teleport(player, spawns[Math.min(index, spawns.length - 1)]);
				giveKit(player);
			}
			index++;
		}
		message(Component.translatableWithFallback("bingo.round.started", "Bingo! Round %s has started.", round));
		sync();
	}

	private void resetPlayer(ServerPlayer player) {
		player.getInventory().clearContent();
		ElytraStore.container(player).setItem(0, ItemStack.EMPTY);
		player.removeAllEffects();
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.setExperienceLevels(0);
		player.setExperiencePoints(0);
		player.setGameMode(GameType.SURVIVAL);
		if (config.advancements.resetOnRoundStart) {
			resetAdvancements(player);
		}
	}

	public void resetAdvancements(ServerPlayer player) {
		for (var holder : server.getAdvancements().getAllAdvancements()) {
			if (!holder.id().getNamespace().equals(BingoOurselves.MOD_ID)) continue;
			var progress = player.getAdvancements().getOrStartProgress(holder);
			for (String criterion : progress.getCompletedCriteria()) {
				player.getAdvancements().revoke(holder, criterion);
			}
		}
	}

	private void giveKit(ServerPlayer player) {
		if (config.game.elytra) {
			ItemStack elytra = new ItemStack(Items.ELYTRA);
			elytra.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
			ElytraStore.container(player).setItem(0, elytra);
			if (config.game.infiniteFireworks) {
				player.getInventory().add(new ItemStack(Items.FIREWORK_ROCKET, 64));
			}
		}
		if (config.game.playerKit) {
			player.getInventory().add(new ItemStack(Items.STONE_SWORD));
			player.getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			player.getInventory().add(new ItemStack(Items.STONE_AXE));
			player.getInventory().add(new ItemStack(Items.BREAD, 16));
			player.getInventory().add(new ItemStack(Items.TORCH, 32));
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
		}
		if (config.game.teamKit) {
			player.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
			player.getInventory().add(new ItemStack(Items.SHIELD));
			player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 16));
		}
		if (config.game.nightVision) {
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
		}
	}

	public void endRound(String winner, String reason) {
		if (phase != GamePhase.PLAYING) return;
		phase = GamePhase.POSTGAME;
		winnerTeam = winner;
		endReason = reason;
		countdown = config.lobby.postgameSeconds * 20;
		if (winner != null) {
			BingoTeam team = teams.get(winner);
			if (team != null) team.cards++;
			message(Component.translatableWithFallback("bingo.round.winner", "Team %s wins the round!",
					Component.translatable("bingo.team." + winner)).withStyle(ChatFormatting.GOLD));
		} else {
			message(Component.translatableWithFallback("bingo.round.draw", "Round over: %s", reason));
		}
		saveState();
		sync();
	}

	public void toLobby() {
		phase = GamePhase.LOBBY;
		countdown = 0;
		winnerTeam = null;
		endReason = null;
		readyPlayers.clear();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			LobbyService.teleportToLobby(player, config, server);
			LobbyService.giveLobbyItems(player);
			player.setGameMode(GameType.ADVENTURE);
		}
		for (BingoTeam team : teams.values()) team.ready = false;
		sync();
	}

	public void onDeath(ServerPlayer player) {
		if (phase != GamePhase.PLAYING || !config.extras.hunterMode) return;
		BingoTeam team = teamOf(player);
		if (team == null || !team.role.equals("RUNNER")) return;
		team.lives--;
		if (team.lives <= 0) {
			spectators.add(player.getUUID());
			message(Component.translatableWithFallback("bingo.hunter.eliminated",
					"Team %s has no lives left!", Component.translatable("bingo.team." + team.id)));
		} else {
			message(Component.translatableWithFallback("bingo.hunter.lives",
					"Team %s has %s lives left", Component.translatable("bingo.team." + team.id), team.lives));
		}
	}

	public void onRespawn(ServerPlayer player) {
		if (phase != GamePhase.PLAYING) return;
		if (spectators.contains(player.getUUID())) {
			player.setGameMode(GameType.SPECTATOR);
		} else if (teamOf(player) != null) {
			BlockPos spawn = LobbyService.spawnFor(teamOf(player), config, arena);
			arena.teleport(player, spawn);
		}
	}

	public boolean allRunnersEliminated() {
		if (!config.extras.hunterMode) return false;
		for (BingoTeam team : teams.values()) {
			if (team.role.equals("RUNNER") && !team.memberIds.isEmpty() && team.lives > 0) return false;
		}
		return true;
	}

	public String hunterTeamId() {
		for (BingoTeam team : teams.values()) if (team.role.equals("HUNTER")) return team.id;
		return null;
	}

	public void tick() {
		ticks++;
		switch (phase) {
			case LOBBY -> {
				if (config.lobby.autoStartSeconds > 0 && ticks % 20 == 0
						&& server.getPlayerList().getPlayerCount() >= config.lobby.minPlayers
						&& server.getPlayerList().getPlayerCount() > 0) {
					startCountdown();
				}
			}
			case COUNTDOWN -> {
				countdown--;
				if (countdown <= 0) startRound();
			}
			case PLAYING -> {
				timeLeft--;
				if (timeLeft <= 0) {
					endRound(null, "time");
				} else {
					completionTimer++;
					if (completionTimer >= 10) {
						completionTimer = 0;
						CompletionService.tick(this);
					}
					ExtrasService.tick(this);
					if (allRunnersEliminated()) {
						endRound(hunterTeamId(), "eliminated");
					}
				}
			}
			case POSTGAME -> {
				countdown--;
				if (countdown <= 0) toLobby();
			}
		}
		syncTimer++;
		if (syncTimer >= 20) {
			syncTimer = 0;
			if (phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN) {
				updateActionBar();
				sync();
			}
		}
	}

	private void updateActionBar() {
		String text = phase == GamePhase.COUNTDOWN
				? Component.translatableWithFallback("bingo.actionbar.countdown", "Starting in %s...",
				Math.max(0, countdown / 20)).getString()
				: Component.translatableWithFallback("bingo.actionbar.time", "Time left: %s",
				formatTime(timeLeft)).getString();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendOverlayMessage(Component.literal(text));
		}
	}

	public static String formatTime(int ticks) {
		int seconds = Math.max(0, ticks / 20);
		return String.format("%02d:%02d", seconds / 60, seconds % 60);
	}

	public void openTeamChest(ServerPlayer player) {
		BingoTeam team = teamOf(player);
		if (team == null) {
			player.sendSystemMessage(Component.translatableWithFallback("bingo.chest.no_team", "You are not on a team."));
			return;
		}
		SimpleContainer container = teamChest(team.id);
		player.openMenu(new SimpleMenuProvider(
				(id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
				Component.translatableWithFallback("bingo.chest.title", "Team chest: %s",
						Component.translatable("bingo.team." + team.id))));
	}

	// ---- persistence -----------------------------------------------------

	private static final class Persisted {
		int round;
		List<BingoTeam> teams = new ArrayList<>();
		java.util.Map<String, List<String>> chests = new java.util.HashMap<>();
	}

	private java.nio.file.Path statePath() {
		return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("bingo/state.json");
	}

	public void saveState() {
		Persisted persisted = new Persisted();
		persisted.round = round;
		persisted.teams = new ArrayList<>(teams.values());
		var ops = com.mojang.serialization.JsonOps.INSTANCE;
		for (var entry : teamChests.entrySet()) {
			List<String> items = new ArrayList<>();
			for (int i = 0; i < entry.getValue().getContainerSize(); i++) {
				ItemStack stack = entry.getValue().getItem(i);
				if (stack.isEmpty()) {
					items.add("");
				} else {
					var encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
					items.add(encoded == null ? "" : encoded.toString());
				}
			}
			persisted.chests.put(entry.getKey(), items);
		}
		BoConfigs.write(statePath(), persisted);
	}

	private void loadState() {
		try {
			java.nio.file.Path path = statePath();
			if (!java.nio.file.Files.exists(path)) return;
			Persisted persisted = BoConfigs.GSON.fromJson(
					java.nio.file.Files.readString(path), Persisted.class);
			if (persisted != null) {
				round = persisted.round;
				if (persisted.chests != null) {
					var ops = com.mojang.serialization.JsonOps.INSTANCE;
					for (var entry : persisted.chests.entrySet()) {
						SimpleContainer container = teamChest(entry.getKey());
						for (int i = 0; i < entry.getValue().size() && i < container.getContainerSize(); i++) {
							String json = entry.getValue().get(i);
							if (json == null || json.isEmpty()) continue;
							try {
								ItemStack stack = ItemStack.CODEC.parse(ops,
										com.google.gson.JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
								if (!stack.isEmpty()) container.setItem(i, stack);
							} catch (Exception ignored) {
							}
						}
					}
				}
				if (persisted.teams != null && persisted.teams.size() == teams.size()) {
					for (BingoTeam loaded : persisted.teams) {
						BingoTeam team = teams.get(loaded.id);
						if (team != null) {
							team.memberIds = loaded.memberIds;
							team.role = loaded.role;
							team.lives = loaded.lives;
							for (String id : team.memberIds) {
								memberships.put(UUID.fromString(id), team.id);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			BingoOurselves.LOGGER.warn("[Bingo] Could not load state", e);
		}
	}
}
