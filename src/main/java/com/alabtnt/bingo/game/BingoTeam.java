package com.alabtnt.bingo.game;

import java.util.ArrayList;
import java.util.List;

public final class BingoTeam {
	public String id;
	public String nameKey;
	public int color;
	public String colorName;
	public List<String> memberIds = new ArrayList<>();
	public boolean ready;
	public int score;
	public int lines;
	public int cards;
	public int lives;
	public String role = "RUNNER";
	public int spawnIndex;

	public BingoTeam() {
	}

	public BingoTeam(String id, String colorName, int color) {
		this.id = id;
		this.colorName = colorName;
		this.color = color;
		this.nameKey = "bingo.team." + id;
	}
}
