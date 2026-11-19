package com.alabtnt.bingo.game;

import com.alabtnt.bingo.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Set;

public final class LobbyService {
	private LobbyService() {
	}

	public static BlockPos lobbyPos(ServerConfig config) {
		return new BlockPos((int) Math.floor(config.lobby.x), (int) config.lobby.y, (int) Math.floor(config.lobby.z));
	}

	public static void ensureLobby(MinecraftServer server, ServerConfig config) {
		var level = server.overworld();
		BlockPos center = lobbyPos(config);
		var state = level.getBlockState(center);
		if (!state.isAir() && state.is(Blocks.SMOOTH_QUARTZ)) return;

		int radius = 15;
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				BlockPos pos = center.offset(x, -1, z);
				level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
				for (int y = 0; y < 5; y++) {
					level.setBlockAndUpdate(pos.above(y), Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int x = -radius; x <= radius; x++) {
			level.setBlockAndUpdate(center.offset(x, -1, -radius), Blocks.SEA_LANTERN.defaultBlockState());
			level.setBlockAndUpdate(center.offset(x, -1, radius), Blocks.SEA_LANTERN.defaultBlockState());
		}
		level.setBlockAndUpdate(center.below(), Blocks.LODESTONE.defaultBlockState());
		level.getWorldBorder().setCenter(center.getX() + 0.5, center.getZ() + 0.5);
		level.getWorldBorder().setSize(31);
	}

	public static void teleportToLobby(ServerPlayer player, ServerConfig config, MinecraftServer server) {
		var level = server.overworld();
		BlockPos pos = lobbyPos(config);
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		level.getWorldBorder().setCenter(pos.getX() + 0.5, pos.getZ() + 0.5);
		level.getWorldBorder().setSize(31);
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 0f, 0f, true);
		player.setGameMode(GameType.ADVENTURE);
	}

	public static void giveLobbyItems(ServerPlayer player) {
		player.getInventory().clearContent();
		player.getInventory().add(named(new ItemStack(Items.COMPASS), "bingo.lobby.item.team", "Team selection"));
		player.getInventory().add(named(new ItemStack(Items.BELL), "bingo.lobby.item.ready", "Ready up"));
		player.getInventory().add(named(new ItemStack(Items.MAP), "bingo.lobby.item.board", "Bingo board"));
		player.getInventory().add(named(new ItemStack(Items.CHEST), "bingo.lobby.item.chest", "Team chest"));
		player.getInventory().add(named(new ItemStack(Items.BOOK), "bingo.lobby.item.help", "Bingo help"));
		player.getInventory().setSelectedSlot(0);
	}

	private static ItemStack named(ItemStack stack, String key, String fallback) {
		stack.set(DataComponents.CUSTOM_NAME, Component.translatableWithFallback(key, fallback));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatableWithFallback(key + ".lore", "Right-click to use"))));
		return stack;
	}

	public static BlockPos spawnFor(BingoTeam team, ServerConfig config, ArenaManager arena) {
		BlockPos[] positions = arena.spawnPositions(Math.max(1, config.lobby.teamCount), config.game.spawnDistanceChunks);
		int index = Math.floorMod(team.spawnIndex, positions.length);
		return positions[index];
	}
}
