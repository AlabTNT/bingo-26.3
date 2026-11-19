package com.alabtnt.bingo.client;

import com.alabtnt.bingo.game.GamePhase;
import com.alabtnt.bingo.game.TeamPalette;
import com.alabtnt.bingo.network.BingoStatePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ClientBingoState {
	private static BingoStatePayload state;

	private ClientBingoState() {
	}

	public static void update(BingoStatePayload payload) {
		state = payload;
	}

	public static void clear() {
		state = null;
	}

	public static BingoStatePayload state() {
		return state;
	}

	public static boolean isPlaying() {
		return state != null && (state.phase() == GamePhase.PLAYING.ordinal()
				|| state.phase() == GamePhase.COUNTDOWN.ordinal());
	}

	public static boolean isLobby() {
		return state == null || state.phase() == GamePhase.LOBBY.ordinal()
				|| state.phase() == GamePhase.POSTGAME.ordinal();
	}

	public static BingoStatePayload.TeamDto myTeam() {
		if (state == null) return null;
		for (BingoStatePayload.TeamDto team : state.teams()) {
			if (team.id().equals(state.myTeam())) return team;
		}
		return null;
	}

	public static ItemStack displayStack(BingoStatePayload.CellDto cell) {
		if (cell == null || cell.displayItem() == null || cell.displayItem().isEmpty()) return ItemStack.EMPTY;
		Identifier id = Identifier.tryParse(cell.displayItem());
		if (id == null) return ItemStack.EMPTY;
		var item = BuiltInRegistries.ITEM.getOptional(id);
		return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	public static int color(String teamId) {
		return TeamPalette.colorOf(teamId);
	}

	public static String time() {
		if (state == null) return "00:00";
		int ticks = state.phase() == GamePhase.COUNTDOWN.ordinal() ? state.countdown() : state.timeLeft();
		int seconds = Math.max(0, ticks / 20);
		return String.format("%02d:%02d", seconds / 60, seconds % 60);
	}
}
