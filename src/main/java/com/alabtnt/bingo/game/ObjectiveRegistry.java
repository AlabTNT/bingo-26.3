package com.alabtnt.bingo.game;

import com.alabtnt.bingo.BingoOurselves;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Loads item/advancement objective pools from the mod's data pack. */
public final class ObjectiveRegistry {
	private static final Gson GSON = new Gson();
	private static final String NS = "bingo_ourselves";

	private static List<Objective> items = List.of();
	private static List<Objective> advancements = List.of();

	private ObjectiveRegistry() {
	}

	public static void reload(MinecraftServer server) {
		List<Objective> loadedItems = new ArrayList<>();
		List<Objective> loadedAdvancements = new ArrayList<>();

		read(server, "bingo/items.json").ifPresent(root -> {
			for (var element : root.getAsJsonArray("items")) {
				JsonObject entry = element.getAsJsonObject();
				String id = entry.get("id").getAsString();
				int tier = entry.has("tier") ? entry.get("tier").getAsInt() : 2;
				if (BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).isEmpty()) continue;
				loadedItems.add(Objective.item(id, tier));
			}
		});

		read(server, "bingo/advancements.json").ifPresent(root -> {
			for (var element : root.getAsJsonArray("advancements")) {
				JsonObject entry = element.getAsJsonObject();
				String id = entry.get("id").getAsString();
				int tier = entry.has("tier") ? entry.get("tier").getAsInt() : 2;
				String icon = entry.has("icon") ? entry.get("icon").getAsString() : "minecraft:paper";
				loadedAdvancements.add(Objective.advancement(id, icon, tier));
			}
		});

		items = List.copyOf(loadedItems);
		advancements = List.copyOf(loadedAdvancements);
		BingoOurselves.LOGGER.info("[Bingo] Loaded {} item and {} advancement objectives",
				items.size(), advancements.size());
	}

	private static Optional<JsonObject> read(MinecraftServer server, String path) {
		Optional<Resource> resource = server.getResourceManager()
				.getResource(Identifier.fromNamespaceAndPath(NS, path));
		if (resource.isEmpty()) {
			BingoOurselves.LOGGER.warn("[Bingo] Missing objective data {}", path);
			return Optional.empty();
		}
		try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
			return Optional.of(GSON.fromJson(reader, JsonObject.class));
		} catch (Exception e) {
			BingoOurselves.LOGGER.error("[Bingo] Could not read {}", path, e);
			return Optional.empty();
		}
	}

	public static List<Objective> items() {
		return items;
	}

	public static List<Objective> advancements() {
		return advancements;
	}

	public static List<Objective> pool(String mode) {
		return switch (mode == null ? "mixed" : mode) {
			case "items" -> items;
			case "advancements" -> advancements;
			default -> {
				List<Objective> all = new ArrayList<>(items.size() + advancements.size());
				all.addAll(items);
				all.addAll(advancements);
				yield all;
			}
		};
	}

	public static List<Objective> byTier(List<Objective> pool, int tier) {
		List<Objective> result = new ArrayList<>();
		for (Objective objective : pool) {
			if (objective.tier == tier) result.add(objective);
		}
		return result;
	}

	public static int[] distribution(int size, String difficulty, List<Integer> custom) {
		int cells = size * size;
		if (custom != null && custom.size() == 5) {
			int sum = custom.stream().mapToInt(Integer::intValue).sum();
			if (sum == cells) {
				return custom.stream().mapToInt(Integer::intValue).toArray();
			}
		}
		return switch (difficulty == null ? "easy" : difficulty) {
			case "medium" -> new int[]{0, 0, Math.round(cells * 0.12f), Math.round(cells * 0.40f), 0};
			case "hard" -> new int[]{0, Math.round(cells * 0.04f), Math.round(cells * 0.20f), Math.round(cells * 0.40f), 0};
			case "extreme" -> new int[]{0, Math.round(cells * 0.20f), Math.round(cells * 0.28f), Math.round(cells * 0.28f), 0};
			default -> new int[]{0, 0, 0, Math.round(cells * 0.36f), 0};
		};
	}

	/** Fills the last tier so that every distribution sums to the board size. */
	public static int[] normalise(int[] distribution, int cells) {
		int[] result = distribution.clone();
		int sum = 0;
		for (int i = 0; i < 4; i++) sum += result[i];
		result[4] = Math.max(0, cells - sum);
		if (result[4] == 0 && sum > cells) {
			result[4] = cells - (result[0] + result[1] + result[2] + result[3]);
		}
		return result;
	}

	public static BingoBoard generate(int index, int size, String difficulty, List<Integer> custom,
			String poolMode, long seed) {
		BingoBoard board = new BingoBoard(index, size);
		board.difficulty = difficulty;
		board.seed = seed;
		Random random = new Random(seed);
		List<Objective> pool = new ArrayList<>(pool(poolMode));
		if (pool.isEmpty()) {
			BingoOurselves.LOGGER.warn("[Bingo] Objective pool '{}' is empty", poolMode);
		}
		Set<String> used = new HashSet<>();
		List<List<Objective>> tiers = new ArrayList<>();
		for (int tier = 0; tier < 5; tier++) tiers.add(byTier(pool, tier));

		int[] distribution = normalise(distribution(size, difficulty, custom), size * size);
		List<Objective> cells = new ArrayList<>();
		for (int tier = 0; tier < 5; tier++) {
			List<Objective> candidates = new ArrayList<>(tiers.get(tier));
			candidates.removeIf(objective -> used.contains(objective.id));
			java.util.Collections.shuffle(candidates, random);
			int count = distribution[tier];
			for (int i = 0; i < count; i++) {
				Objective objective = nextCandidate(candidates, tiers, used, tier, random);
				if (objective == null) break;
				used.add(objective.id);
				cells.add(objective);
			}
		}
		// If a tier ran out, fill the remaining cells from the whole pool.
		if (cells.size() < size * size) {
			List<Objective> candidates = new ArrayList<>(pool);
			candidates.removeIf(objective -> used.contains(objective.id));
			java.util.Collections.shuffle(candidates, random);
			for (Objective objective : candidates) {
				if (cells.size() >= size * size) break;
				used.add(objective.id);
				cells.add(objective);
			}
		}
		java.util.Collections.shuffle(cells, random);
		for (int i = 0; i < size * size; i++) {
			board.cells.set(i, i < cells.size() ? cells.get(i) : null);
		}
		return board;
	}

	private static Objective nextCandidate(List<Objective> candidates, List<List<Objective>> tiers,
			Set<String> used, int tier, Random random) {
		for (int offset = 0; offset < 5; offset++) {
			int candidateTier = (tier + offset) % 5;
			List<Objective> list = candidateTier == tier ? candidates : tiers.get(candidateTier);
			for (Objective objective : list) {
				if (!used.contains(objective.id)) return objective;
			}
		}
		return null;
	}
}
