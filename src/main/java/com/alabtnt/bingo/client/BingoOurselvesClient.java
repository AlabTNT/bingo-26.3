package com.alabtnt.bingo.client;

import com.alabtnt.bingo.client.hud.BoardHud;
import com.alabtnt.bingo.client.map.ClientMapData;
import com.alabtnt.bingo.client.map.MinimapHud;
import com.alabtnt.bingo.client.map.WorldMapScreen;
import com.alabtnt.bingo.client.integration.IntegrationToggles;
import com.alabtnt.bingo.client.screen.BOConfigScreen;
import com.alabtnt.bingo.client.screen.BoardScreen;
import com.alabtnt.bingo.client.screen.LobbyScreen;
import com.alabtnt.bingo.config.BoConfigs;
import com.alabtnt.bingo.network.BingoStatePayload;
import com.alabtnt.bingo.network.OpenScreenPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class BingoOurselvesClient implements ClientModInitializer {
	public static final Identifier HUD_ID =
			Identifier.fromNamespaceAndPath("bingo_ourselves", "board_hud");
	public static final Identifier MINIMAP_ID =
			Identifier.fromNamespaceAndPath("bingo_ourselves", "minimap");

	private static KeyMapping openBoardKey;
	private static KeyMapping toggleHudKey;
	private static KeyMapping configKey;
	private static KeyMapping lobbyKey;
	private static KeyMapping sortKey;
	private static KeyMapping worldMapKey;
	private static KeyMapping minimapKey;

	@Override
	public void onInitializeClient() {
		BoConfigs.client();
		HudElementRegistry.addLast(HUD_ID, new BoardHud());
		HudElementRegistry.addLast(MINIMAP_ID, new MinimapHud());

		ClientPlayNetworking.registerGlobalReceiver(BingoStatePayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					ClientBingoState.update(payload);
					if (context.client().level != null) {
						ClientMapData.ensureKey(context.client().level, payload);
					}
				}));
		ClientPlayNetworking.registerGlobalReceiver(com.alabtnt.bingo.network.ElytraSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					Minecraft client = context.client();
					if (client.player == null) return;
					net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.EMPTY;
					if (!payload.itemId().isEmpty()) {
						var id = Identifier.tryParse(payload.itemId());
						if (id != null) {
							var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);
							if (item.isPresent()) {
								stack = new net.minecraft.world.item.ItemStack(item.get(), Math.max(1, payload.count()));
								stack.setDamageValue(payload.damage());
							}
						}
					}
					com.alabtnt.bingo.game.ElytraStore.setClient(client.player.getUUID(), stack);
				}));
		ClientPlayNetworking.registerGlobalReceiver(OpenScreenPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					Minecraft client = context.client();
					switch (payload.screen()) {
						case "board" -> client.setScreenAndShow(new BoardScreen(client.gui.screen()));
						case "lobby" -> client.setScreenAndShow(new LobbyScreen(client.gui.screen()));
						default -> {
						}
					}
				}));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientBingoState.clear();
			ClientMapData.clear();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			IntegrationToggles.apply();
			ClientMapData.clear();
		});

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("bingo_ourselves", "general"));
		openBoardKey = key("key.bingo.open_board", InputConstants.KEY_Y, category);
		toggleHudKey = key("key.bingo.toggle_hud", InputConstants.KEY_H, category);
		configKey = key("key.bingo.config", InputConstants.KEY_O, category);
		lobbyKey = key("key.bingo.lobby", InputConstants.KEY_G, category);
		sortKey = key("key.bingo.sort", InputConstants.KEY_R, category);
		worldMapKey = key("key.bingo.world_map", InputConstants.KEY_M, category);
		minimapKey = key("key.bingo.toggle_minimap", InputConstants.KEY_N, category);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean noScreen = client.player != null && client.gui.screen() == null;
			while (openBoardKey.consumeClick()) {
				if (noScreen) client.setScreenAndShow(new BoardScreen(null));
			}
			while (lobbyKey.consumeClick()) {
				if (noScreen) client.setScreenAndShow(new LobbyScreen(null));
			}
			while (configKey.consumeClick()) {
				if (noScreen) client.setScreenAndShow(BOConfigScreen.create(null));
			}
			while (toggleHudKey.consumeClick()) {
				var cfg = BoConfigs.client();
				cfg.hudEnabled = !cfg.hudEnabled;
				BoConfigs.saveClient();
				if (client.player != null) {
					client.player.sendSystemMessage(Component.translatableWithFallback(
							"bingo.hud.toggle", "Bingo HUD %s", cfg.hudEnabled ? "on" : "off"));
				}
			}
			while (worldMapKey.consumeClick()) {
				if (noScreen && BoConfigs.client().enableWorldMap) {
					client.setScreenAndShow(new WorldMapScreen(null));
				}
			}
			while (minimapKey.consumeClick()) {
				var cfg = BoConfigs.client();
				cfg.enableMinimap = !cfg.enableMinimap;
				BoConfigs.saveClient();
			}
			while (sortKey.consumeClick()) {
				if (!BoConfigs.client().enableInventorySorting) continue;
				if (client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
					IntegrationToggles.requestSort();
				}
			}
		});

		IntegrationToggles.apply();
	}

	private static KeyMapping key(String name, int code, KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, code, category));
	}
}
