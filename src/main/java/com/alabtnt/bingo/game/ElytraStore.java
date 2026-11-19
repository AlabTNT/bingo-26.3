package com.alabtnt.bingo.game;

import com.alabtnt.bingo.BingoOurselves;
import com.alabtnt.bingo.config.BoConfigs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Extra elytra slot, allowing a chestplate and an elytra at the same time (requirement 11). */
public final class ElytraStore {
	private static final Map<UUID, Container> SERVER = new HashMap<>();
	private static final Map<UUID, Container> CLIENT = new HashMap<>();
	private static MinecraftServer server;
	private static boolean loaded;

	private ElytraStore() {
	}

	public static final class Container extends SimpleContainer {
		private final UUID owner;
		private final boolean client;

		public Container(UUID owner, boolean client) {
			super(1);
			this.owner = owner;
			this.client = client;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (client) {
				return;
			}
			save();
			ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(owner);
			if (player != null) {
				com.alabtnt.bingo.network.BingoNetworking.syncElytra(player, getItem(0));
			}
		}
	}

	public static Container container(Player player) {
		boolean client = player.level().isClientSide();
		Map<UUID, Container> map = client ? CLIENT : SERVER;
		return map.computeIfAbsent(player.getUUID(), id -> new Container(id, client));
	}

	public static boolean has(Player player) {
		if (player == null) return false;
		ItemStack stack = container(player).getItem(0);
		return !stack.isEmpty() && stack.is(Items.ELYTRA);
	}

	public static ItemStack get(Player player) {
		return container(player).getItem(0).copy();
	}

	public static void setClient(UUID playerId, ItemStack stack) {
		Container container = CLIENT.computeIfAbsent(playerId, id -> new Container(id, true));
		container.setItem(0, stack);
	}

	public static void damage(Player player) {
		Container container = container(player);
		ItemStack stack = container.getItem(0);
		if (stack.isEmpty()) return;
		if (!stack.isDamageableItem()) return;
		if (stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)) return;
		int next = stack.getDamageValue() + 1;
		if (next >= stack.getMaxDamage()) {
			container.setItem(0, ItemStack.EMPTY);
			player.stopFallFlying();
		} else {
			stack.setDamageValue(next);
			container.setChanged();
		}
	}

	public static void initialize(MinecraftServer minecraftServer) {
		server = minecraftServer;
		if (loaded) return;
		loaded = true;
		Path path = statePath();
		try {
			if (!Files.exists(path)) return;
			JsonObject root = BoConfigs.GSON.fromJson(Files.readString(path), JsonObject.class);
			if (root == null) return;
			for (String key : root.keySet()) {
				ItemStack stack = ItemStack.CODEC.parse(JsonOps.INSTANCE, root.get(key))
						.result().orElse(ItemStack.EMPTY);
				if (!stack.isEmpty()) {
					containerById(UUID.fromString(key)).setItem(0, stack);
				}
			}
		} catch (Exception e) {
			BingoOurselves.LOGGER.warn("[Bingo] Could not load elytra slots", e);
		}
	}

	private static Container containerById(UUID id) {
		return SERVER.computeIfAbsent(id, key -> new Container(key, false));
	}

	public static void reset() {
		SERVER.clear();
		CLIENT.clear();
		loaded = false;
	}

	public static void save() {
		if (server == null) return;
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, Container> entry : SERVER.entrySet()) {
			ItemStack stack = entry.getValue().getItem(0);
			if (stack.isEmpty()) continue;
			JsonElement encoded = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack)
					.result().orElse(null);
			if (encoded != null) root.add(entry.getKey().toString(), encoded);
		}
		BoConfigs.write(statePath(), root);
	}

	private static Path statePath() {
		return server == null ? Path.of("bingo-elytra.json")
				: server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
				.resolve("bingo/elytra.json");
	}
}
