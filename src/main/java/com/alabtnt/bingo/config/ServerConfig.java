package com.alabtnt.bingo.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side game configuration. Stored in {@code config/bingo-ourselves/server.json}.
 * Every field has a sane default; commands and presets mutate a copy and save it.
 */
public final class ServerConfig {
	public int configVersion = 1;
	public String preset = "mixed";

	public Lobby lobby = new Lobby();
	public Board board = new Board();
	public Game game = new Game();
	public World world = new World();
	public Extras extras = new Extras();
	public Advancements advancements = new Advancements();

	public static final class Lobby {
		public int minPlayers = 2;
		public int teamCount = 4;
		public int countdownSeconds = 10;
		public int postgameSeconds = 15;
		/** 0 = no automatic start (admin must use /bingo start). */
		public int autoStartSeconds = 0;
		public double x = 0.5;
		public double y = 101;
		public double z = 0.5;
		public boolean buildPlatform = true;
		public boolean giveLobbyItems = true;
	}

	public static final class Board {
		/** Odd sizes only: 3, 5, 7, 9. */
		public int size = 5;
		public int boards = 1;
		public String pool = "mixed"; // items | advancements | mixed
		public String difficulty = "easy"; // easy | medium | hard | extreme | custom
		/** S, A, B, C, D counts; empty = derived from difficulty. */
		public List<Integer> distribution = new ArrayList<>();
		public String goal = "lines"; // lines | items | cards
		public int goalValue = 1;
		public boolean lockout = false;
		public boolean fog = false;
		/** Number of cells revealed at the start when fog is enabled. */
		public int fogInitialRevealed = 1;
		public boolean hiddenItems = false;
		public boolean consumeItems = false;
		/** Per-board difficulty overrides, e.g. ["easy", "hard"]. */
		public List<String> boardDifficulties = new ArrayList<>();
	}

	public static final class Game {
		public int timeLimitMinutes = 30;
		public boolean keepInventory = true;
		public boolean pvp = false;
		public boolean elytra = false;
		public boolean infiniteFireworks = false;
		public boolean nightVision = false;
		public boolean playerKit = false;
		public boolean teamKit = false;
		public int spawnDistanceChunks = 128;
		public String spawnDimension = "minecraft:overworld";
		public boolean allowSpectators = true;
		public boolean giveMementos = true;
	}

	public static final class World {
		/** Recreate the arena dimension with a new seed for every round. */
		public boolean randomMap = true;
		/** Fallback arena center when dynamic dimension creation is unavailable. */
		public int borderDiameter = 4096;
		public String weather = "cycle"; // cycle | clear | rain | thunder
		public String daylight = "cycle"; // cycle | day | night
		public boolean lockTime = false;
		public boolean randomEvents = true;
		public int eventIntervalSeconds = 180;
		public boolean supplyDrops = true;
		public int supplyDropIntervalSeconds = 300;
	}

	public static final class Extras {
		public boolean teamChest = true;
		public boolean hunterMode = false;
		/** Team id that plays as hunter; empty = last team. */
		public String hunterTeam = "";
		public int runnerLives = 3;
		public boolean huntersTrackRunners = true;
		public boolean huntersGlow = true;
		public boolean sharedTeamChat = true;
	}

	public static final class Advancements {
		public boolean replaceVanilla = true;
		public boolean resetOnRoundStart = true;
	}
}
