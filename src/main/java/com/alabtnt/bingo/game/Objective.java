package com.alabtnt.bingo.game;

import java.util.ArrayList;
import java.util.List;

/** A single objective that can appear on a Bingo board. */
public final class Objective {
	public enum Kind {
		ITEM, ADVANCEMENT
	}

	public String id;
	public Kind kind;
	public int tier; // 0=S 1=A 2=B 3=C 4=D
	public String displayItem;
	public List<String> items = new ArrayList<>();
	public String advancement;
	public String nameKey;
	public String nameFallback;

	public Objective() {
	}

	public static Objective item(String itemId, int tier) {
		Objective objective = new Objective();
		objective.id = "item:" + itemId;
		objective.kind = Kind.ITEM;
		objective.tier = tier;
		objective.displayItem = itemId;
		objective.items.add(itemId);
		return objective;
	}

	public static Objective group(String groupId, String displayItem, List<String> items, int tier) {
		Objective objective = new Objective();
		objective.id = "group:" + groupId;
		objective.kind = Kind.ITEM;
		objective.tier = tier;
		objective.displayItem = displayItem;
		objective.items.addAll(items);
		objective.nameKey = "bingo.objective.group." + groupId;
		return objective;
	}

	public static Objective advancement(String advancementId, String displayItem, int tier) {
		Objective objective = new Objective();
		objective.id = "adv:" + advancementId;
		objective.kind = Kind.ADVANCEMENT;
		objective.tier = tier;
		objective.displayItem = displayItem;
		objective.advancement = advancementId;
		return objective;
	}
}
