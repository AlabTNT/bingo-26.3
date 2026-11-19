package com.alabtnt.bingo.game;

import java.util.List;

public final class TeamPalette {
	public record Entry(String id, int color) {
	}

	public static final List<Entry> ENTRIES = List.of(
			new Entry("red", 0xE04B4B),
			new Entry("blue", 0x4B7BE0),
			new Entry("green", 0x4BC46A),
			new Entry("yellow", 0xE0C34B),
			new Entry("purple", 0xA45BE0),
			new Entry("aqua", 0x4BC9D8),
			new Entry("orange", 0xE08C4B),
			new Entry("pink", 0xE05BA4)
	);

	private TeamPalette() {
	}

	public static int colorOf(String teamId) {
		for (Entry entry : ENTRIES) {
			if (entry.id.equals(teamId)) return entry.color();
		}
		return 0xFFFFFF;
	}
}
