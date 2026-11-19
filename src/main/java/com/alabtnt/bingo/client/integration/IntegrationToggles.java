package com.alabtnt.bingo.client.integration;

import com.alabtnt.bingo.config.BoConfigs;

/** Runtime toggles for the bundled utility mods. All calls are optional/reflective. */
public final class IntegrationToggles {
	private IntegrationToggles() {
	}

	public static void apply() {
		var cfg = BoConfigs.client();
		setJeiOverlay(cfg.enableJei);
	}

	public static void requestSort() {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (client.player == null) return;
		var menu = client.player.containerMenu;
		if (menu == null) return;
		boolean container = !(menu instanceof net.minecraft.world.inventory.InventoryMenu);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.alabtnt.bingo.network.SortPayload(menu.containerId, container, true));
	}

	private static void setJeiOverlay(boolean enabled) {
		try {
			Class<?> internal = Class.forName("mezz.jei.common.Internal");
			Object toggleState = internal.getMethod("getClientToggleState").invoke(null);
			if (toggleState == null) return;
			toggleState.getClass().getMethod("setOverlayEnabled", boolean.class).invoke(toggleState, enabled);
		} catch (Throwable ignored) {
			// JEI is optional.
		}
	}
}
