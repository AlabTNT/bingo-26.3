package com.alabtnt.bingo;

import com.alabtnt.bingo.command.BingoCommand;
import com.alabtnt.bingo.config.BoConfigs;
import com.alabtnt.bingo.game.BingoGame;
import com.alabtnt.bingo.game.GamePhase;
import com.alabtnt.bingo.game.ObjectiveRegistry;
import com.alabtnt.bingo.network.BingoNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BingoOurselves implements ModInitializer {
	public static final String MOD_ID = "bingo_ourselves";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BoConfigs.server();
		BingoNetworking.registerPayloads();
		BingoCommand.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			com.alabtnt.bingo.game.ElytraStore.initialize(server);
			BingoGame.create(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BingoGame game = BingoGame.get();
			if (game != null) game.saveState();
			com.alabtnt.bingo.game.ElytraStore.save();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BingoGame.clear();
			com.alabtnt.bingo.game.ElytraStore.reset();
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			if (success) ObjectiveRegistry.reload(server);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			BingoGame game = BingoGame.get();
			if (game != null) game.tick();
		});

		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				BingoGame game = BingoGame.get();
				if (game != null) game.onDeath(player);
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			BingoGame game = BingoGame.get();
			if (game != null) game.onRespawn(newPlayer);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			BingoGame game = BingoGame.get();
			if (game != null) game.onJoin(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BingoGame game = BingoGame.get();
			if (game != null) game.onLeave(handler.player);
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			BingoGame game = BingoGame.get();
			if (game == null || game.phase() != GamePhase.LOBBY && game.phase() != GamePhase.POSTGAME) {
				return InteractionResult.PASS;
			}
			var stack = player.getItemInHand(hand);
			if (stack.is(Items.COMPASS)) {
				BingoCommand.cycleTeam(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			if (stack.is(Items.BELL)) {
				game.ready(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			if (stack.is(Items.MAP)) {
				BingoNetworking.openBoard(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			if (stack.is(Items.CHEST)) {
				game.openTeamChest(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			if (stack.is(Items.BOOK)) {
				BingoNetworking.openLobby(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		LOGGER.info("[Bingo] Bingo! 26.3 engine loaded");
	}
}
