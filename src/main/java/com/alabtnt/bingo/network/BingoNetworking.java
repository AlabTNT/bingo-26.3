package com.alabtnt.bingo.network;

import com.alabtnt.bingo.BingoOurselves;
import com.alabtnt.bingo.game.BingoGame;
import com.alabtnt.bingo.game.GamePhase;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class BingoNetworking {
	private BingoNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.clientboundPlay().register(BingoStatePayload.TYPE, BingoStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ElytraSyncPayload.TYPE, ElytraSyncPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BingoActionPayload.TYPE, BingoActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SortPayload.TYPE, SortPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BingoActionPayload.TYPE, (payload, context) ->
				context.server().execute(() -> handle(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(SortPayload.TYPE, (payload, context) ->
				context.server().execute(() -> com.alabtnt.bingo.game.SortService.sort(
						context.player(), payload.containerId(), payload.container(), payload.player())));
	}

	public static void broadcastState(BingoGame game) {
		for (ServerPlayer player : game.server().getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(player, BingoStatePayload.TYPE)) continue;
			ServerPlayNetworking.send(player, BingoStatePayload.of(game, player));
		}
	}

	public static void openBoard(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, OpenScreenPayload.TYPE)) {
			ServerPlayNetworking.send(player, new OpenScreenPayload("board"));
		}
	}

	public static void syncElytra(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
		if (!ServerPlayNetworking.canSend(player, ElytraSyncPayload.TYPE)) return;
		String id = stack.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getKey(stack.getItem()).toString();
		ServerPlayNetworking.send(player, new ElytraSyncPayload(id,
				stack.getCount(), stack.getDamageValue()));
	}

	public static void openLobby(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, OpenScreenPayload.TYPE)) {
			ServerPlayNetworking.send(player, new OpenScreenPayload("lobby"));
		}
	}

	private static void handle(ServerPlayer player, BingoActionPayload payload) {
		BingoGame game = BingoGame.get();
		if (game == null) return;
		switch (payload.action()) {
			case "ready" -> game.ready(player);
			case "leave" -> game.leaveTeam(player);
			case "team" -> game.joinTeam(player, payload.arg());
			case "start" -> {
				if (isAdmin(player) && game.phase() == GamePhase.LOBBY) game.startCountdown();
			}
			case "end" -> {
				if (isAdmin(player)) game.endRound(null, "command");
			}
			case "chest" -> game.openTeamChest(player);
			case "board" -> openBoard(player);
			case "lobby" -> openLobby(player);
			default -> BingoOurselves.LOGGER.debug("Unknown bingo action {}", payload.action());
		}
	}

	private static boolean isAdmin(ServerPlayer player) {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
	}
}
