package com.alabtnt.bingo.network;

import com.alabtnt.bingo.game.BingoBoard;
import com.alabtnt.bingo.game.BingoGame;
import com.alabtnt.bingo.game.BingoTeam;
import com.alabtnt.bingo.game.GamePhase;
import com.alabtnt.bingo.game.Objective;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Full board/game snapshot for one player (fog is applied per team). */
public record BingoStatePayload(
		int phase,
		int countdown,
		int timeLeft,
		int totalTime,
		int round,
		long arenaSeed,
		int boardSize,
		int boardCount,
		int boardIndex,
		String goal,
		int goalValue,
		boolean fog,
		boolean lockout,
		boolean hunter,
		String winner,
		String reason,
		String myTeam,
		List<TeamDto> teams,
		List<CellDto> cells) implements CustomPacketPayload {

	public static final Type<BingoStatePayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("bingo_ourselves", "state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BingoStatePayload> CODEC =
			StreamCodec.of((buf, payload) -> payload.write(buf), BingoStatePayload::read);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public record TeamDto(String id, boolean ready, int score, int lines, int cards,
			int lives, String role, int members) {
	}

	public record CellDto(int index, String kind, String displayItem, String advancement,
			String nameKey, String fallback, int tier, boolean revealed, int completedMask, String owner) {
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(phase);
		buf.writeVarInt(countdown);
		buf.writeVarInt(timeLeft);
		buf.writeVarInt(totalTime);
		buf.writeVarInt(round);
		buf.writeLong(arenaSeed);
		buf.writeVarInt(boardSize);
		buf.writeVarInt(boardCount);
		buf.writeVarInt(boardIndex);
		buf.writeUtf(goal == null ? "lines" : goal);
		buf.writeVarInt(goalValue);
		buf.writeBoolean(fog);
		buf.writeBoolean(lockout);
		buf.writeBoolean(hunter);
		buf.writeUtf(winner == null ? "" : winner);
		buf.writeUtf(reason == null ? "" : reason);
		buf.writeUtf(myTeam == null ? "" : myTeam);
		buf.writeVarInt(teams.size());
		for (TeamDto team : teams) {
			buf.writeUtf(team.id());
			buf.writeBoolean(team.ready());
			buf.writeVarInt(team.score());
			buf.writeVarInt(team.lines());
			buf.writeVarInt(team.cards());
			buf.writeVarInt(team.lives());
			buf.writeUtf(team.role());
			buf.writeVarInt(team.members());
		}
		buf.writeVarInt(cells.size());
		for (CellDto cell : cells) {
			buf.writeVarInt(cell.index());
			buf.writeUtf(cell.kind());
			buf.writeUtf(cell.displayItem() == null ? "" : cell.displayItem());
			buf.writeUtf(cell.advancement() == null ? "" : cell.advancement());
			buf.writeUtf(cell.nameKey() == null ? "" : cell.nameKey());
			buf.writeUtf(cell.fallback() == null ? "" : cell.fallback());
			buf.writeVarInt(cell.tier());
			buf.writeBoolean(cell.revealed());
			buf.writeVarInt(cell.completedMask());
			buf.writeUtf(cell.owner() == null ? "" : cell.owner());
		}
	}

	private static BingoStatePayload read(RegistryFriendlyByteBuf buf) {
		int phase = buf.readVarInt();
		int countdown = buf.readVarInt();
		int timeLeft = buf.readVarInt();
		int totalTime = buf.readVarInt();
		int round = buf.readVarInt();
		long arenaSeed = buf.readLong();
		int boardSize = buf.readVarInt();
		int boardCount = buf.readVarInt();
		int boardIndex = buf.readVarInt();
		String goal = buf.readUtf();
		int goalValue = buf.readVarInt();
		boolean fog = buf.readBoolean();
		boolean lockout = buf.readBoolean();
		boolean hunter = buf.readBoolean();
		String winner = buf.readUtf();
		String reason = buf.readUtf();
		String myTeam = buf.readUtf();
		int teamCount = buf.readVarInt();
		List<TeamDto> teams = new ArrayList<>(teamCount);
		for (int i = 0; i < teamCount; i++) {
			teams.add(new TeamDto(buf.readUtf(), buf.readBoolean(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt()));
		}
		int cellCount = buf.readVarInt();
		List<CellDto> cells = new ArrayList<>(cellCount);
		for (int i = 0; i < cellCount; i++) {
			cells.add(new CellDto(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
					buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(),
					buf.readVarInt(), buf.readUtf()));
		}
		return new BingoStatePayload(phase, countdown, timeLeft, totalTime, round, arenaSeed, boardSize,
				boardCount, boardIndex, goal, goalValue, fog, lockout, hunter,
				winner.isEmpty() ? null : winner, reason.isEmpty() ? null : reason,
				myTeam.isEmpty() ? null : myTeam, teams, cells);
	}

	public static BingoStatePayload of(BingoGame game, ServerPlayer player) {
		BingoTeam team = game.teamOf(player);
		BingoBoard board = team == null ? game.boards().isEmpty() ? null : game.boards().get(0) : game.boardFor(team.id);
		List<TeamDto> teams = new ArrayList<>();
		for (BingoTeam t : game.teams()) {
			teams.add(new TeamDto(t.id, t.ready, t.score, t.lines, t.cards, t.lives, t.role,
					game.onlineMembers(t).size()));
		}
		List<CellDto> cells = new ArrayList<>();
		if (board != null) {
			List<String> teamIds = new ArrayList<>(game.teams().stream().map(t -> t.id).toList());
			for (int index = 0; index < board.cellCount(); index++) {
				Objective objective = board.cells.get(index);
				boolean revealed = team == null || !game.config().board.fog || board.isRevealed(index, team.id);
				int mask = 0;
				for (int i = 0; i < teamIds.size() && i < 31; i++) {
					if (board.isCompleted(index, teamIds.get(i))) mask |= (1 << i);
				}
				if (!revealed) {
					cells.add(new CellDto(index, "hidden", null, null, null, null,
							objective == null ? 2 : objective.tier, false, mask, board.owner(index)));
					continue;
				}
				if (objective == null) {
					cells.add(new CellDto(index, "hidden", null, null, null, null, 2, true, mask, null));
					continue;
				}
				cells.add(new CellDto(index, objective.kind.name(), objective.displayItem,
						objective.advancement, objective.nameKey, objective.nameFallback,
						objective.tier, true, mask, board.owner(index)));
			}
		}
		int boardIndex = board == null ? 0 : board.index;
		return new BingoStatePayload(
				game.phase().ordinal(), game.countdown(), game.timeLeftTicks(), game.totalTimeTicks(),
				game.round(), game.arena().seed(), board == null ? game.config().board.size : board.size,
				game.boards().size(), boardIndex, game.config().board.goal,
				game.config().board.goalValue, game.config().board.fog, game.config().board.lockout,
				game.config().extras.hunterMode, game.winnerTeam(), game.endReason(),
				team == null ? null : team.id, teams, cells);
	}
}
