package com.alabtnt.bingo.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One board (card) in the round. */
public final class BingoBoard {
	public int index;
	public int size = 5;
	public String difficulty = "easy";
	public long seed;
	public List<Objective> cells = new ArrayList<>();

	public List<Set<String>> completed = new ArrayList<>();
	public Set<String> completedTeams = new LinkedHashSet<>();
	public List<String> lockOwners = new ArrayList<>(); // cell index -> team id ("" = none)
	public List<Set<String>> revealed = new ArrayList<>();

	public BingoBoard() {
	}

	public BingoBoard(int index, int size) {
		this.index = index;
		this.size = size;
		resize();
	}

	public void resize() {
		cells.clear();
		completed.clear();
		revealed.clear();
		completedTeams.clear();
		lockOwners.clear();
		for (int i = 0; i < size * size; i++) {
			cells.add(null);
			completed.add(new LinkedHashSet<>());
			revealed.add(new LinkedHashSet<>());
			lockOwners.add("");
		}
	}

	public int cellCount() {
		return size * size;
	}

	public Objective cell(int x, int y) {
		if (x < 0 || y < 0 || x >= size || y >= size) return null;
		return cells.get(y * size + x);
	}

	public boolean isCompleted(int index, String team) {
		return index >= 0 && index < completed.size() && completed.get(index).contains(team);
	}

	public boolean isRevealed(int index, String team) {
		return index >= 0 && index < revealed.size() && revealed.get(index).contains(team);
	}

	public String owner(int index) {
		if (index < 0 || index >= lockOwners.size()) return null;
		String owner = lockOwners.get(index);
		return owner == null || owner.isEmpty() ? null : owner;
	}

	public void reveal(int index, String team) {
		if (index < 0 || index >= revealed.size()) return;
		revealed.get(index).add(team);
	}

	public void revealAll(String team) {
		for (int i = 0; i < cellCount(); i++) reveal(i, team);
	}

	public void revealNeighbours(int index, String team, boolean diagonals) {
		int x = index % size;
		int y = index / size;
		reveal(index, team);
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dy == 0) continue;
				if (!diagonals && dx != 0 && dy != 0) continue;
				int nx = x + dx;
				int ny = y + dy;
				if (nx >= 0 && ny >= 0 && nx < size && ny < size) {
					reveal(ny * size + nx, team);
				}
			}
		}
	}

	public int completedCount(String team) {
		int count = 0;
		for (Set<String> set : completed) {
			if (set.contains(team)) count++;
		}
		return count;
	}

	public int lines(String team) {
		int lines = 0;
		for (int row = 0; row < size; row++) {
			boolean all = true;
			for (int col = 0; col < size; col++) {
				if (!isCompleted(row * size + col, team)) {
					all = false;
					break;
				}
			}
			if (all) lines++;
		}
		for (int col = 0; col < size; col++) {
			boolean all = true;
			for (int row = 0; row < size; row++) {
				if (!isCompleted(row * size + col, team)) {
					all = false;
					break;
				}
			}
			if (all) lines++;
		}
		boolean diag = true;
		boolean anti = true;
		for (int i = 0; i < size; i++) {
			if (!isCompleted(i * size + i, team)) diag = false;
			if (!isCompleted(i * size + (size - 1 - i), team)) anti = false;
		}
		if (diag) lines++;
		if (anti) lines++;
		return lines;
	}

	public boolean full(String team) {
		return completedCount(team) >= cellCount();
	}
}
