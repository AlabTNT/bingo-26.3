package com.alabtnt.bingo.game;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scans inventories and advancement progress, then updates the boards. */
public final class CompletionService {
	private CompletionService() {
	}

	public static void tick(BingoGame game) {
		if (game.phase() != GamePhase.PLAYING) return;
		for (BingoTeam team : game.teams()) {
			BingoBoard board = game.boardFor(team.id);
			if (board == null) continue;
			Set<Identifier> inventoryItems = inventoryItems(game, team);

			for (int index = 0; index < board.cellCount(); index++) {
				if (board.isCompleted(index, team.id)) continue;
				if (game.config().board.lockout) {
					String owner = board.owner(index);
					if (owner != null && !owner.equals(team.id)) continue;
				}
				Objective objective = board.cells.get(index);
				if (objective == null) continue;

				boolean complete = switch (objective.kind) {
					case ITEM -> hasAny(objective.items, inventoryItems);
					case ADVANCEMENT -> advancementDone(game, team, objective.advancement);
				};
				if (!complete) continue;

				completeCell(game, team, board, index, objective);
			}
			updateScores(game, team, board);
		}
		checkWin(game);
	}

	private static Set<Identifier> inventoryItems(BingoGame game, BingoTeam team) {
		Set<Identifier> items = new HashSet<>();
		for (ServerPlayer player : game.onlineMembers(team)) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (stack.isEmpty()) continue;
				Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (id != null) items.add(id);
			}
		}
		return items;
	}

	private static boolean hasAny(List<String> ids, Set<Identifier> inventory) {
		for (String id : ids) {
			if (inventory.contains(Identifier.parse(id))) return true;
		}
		return false;
	}

	private static boolean advancementDone(BingoGame game, BingoTeam team, String advancementId) {
		if (advancementId == null) return false;
		var holder = game.server().getAdvancements().get(Identifier.parse(advancementId));
		if (holder == null) return false;
		for (ServerPlayer player : game.onlineMembers(team)) {
			if (player.getAdvancements().getOrStartProgress(holder).isDone()) return true;
		}
		return false;
	}

	private static void completeCell(BingoGame game, BingoTeam team, BingoBoard board, int index, Objective objective) {
		board.completed.get(index).add(team.id);
		if (game.config().board.lockout) {
			board.lockOwners.set(index, team.id);
		}
		if (game.config().board.fog) {
			board.revealNeighbours(index, team.id, false);
		}

		Component name = displayName(game, objective);
		Component message = Component.translatableWithFallback("bingo.cell.completed",
				"%s completed %s", Component.translatable("bingo.team." + team.id), name)
				.withStyle(ChatFormatting.GREEN);
		game.message(message);
		for (ServerPlayer player : game.onlineMembers(team)) {
			player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 1.4f);
		}
		game.sync();
	}

	private static Component displayName(BingoGame game, Objective objective) {
		if (objective.kind == Objective.Kind.ADVANCEMENT) {
			var holder = game.server().getAdvancements().get(Identifier.parse(objective.advancement));
			if (holder != null && holder.value().display().isPresent()) {
				return holder.value().display().get().title();
			}
		}
		if (objective.displayItem != null) {
			var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(objective.displayItem));
			if (item.isPresent()) return new ItemStack(item.get()).getHoverName();
		}
		return Component.literal(objective.displayItem == null ? objective.id : objective.displayItem);
	}

	private static void updateScores(BingoGame game, BingoTeam team, BingoBoard board) {
		team.score = board.completedCount(team.id);
		team.lines = board.lines(team.id);
		if (board.full(team.id)) {
			team.cards = 1;
		} else {
			team.cards = 0;
		}
	}

	private static void checkWin(BingoGame game) {
		String goal = game.config().board.goal;
		int target = Math.max(1, game.config().board.goalValue);
		for (BingoTeam team : game.teams()) {
			if (game.config().extras.hunterMode && team.role.equals("HUNTER")) continue;
			boolean win = switch (goal) {
				case "items" -> team.score >= target;
				case "cards" -> team.cards >= target;
				default -> team.lines >= target;
			};
			if (win) {
				game.endRound(team.id, "bingo");
				return;
			}
		}
	}
}
