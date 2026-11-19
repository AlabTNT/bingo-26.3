package com.alabtnt.bingo.client.map;

import com.alabtnt.bingo.client.ClientBingoState;
import com.alabtnt.bingo.config.BoConfigs;
import com.alabtnt.bingo.config.ClientConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

public final class MinimapHud implements HudElement {
	private int sampleTimer;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ClientConfig cfg = BoConfigs.client();
		if (!cfg.enableMinimap || mc.player == null || mc.level == null) return;
		if (cfg.hideOnDebug && mc.getDebugOverlay().showDebugScreen()) return;
		if (cfg.hideOnChat && mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) return;

		ClientLevel level = mc.level;
		ClientMapData.ensureKey(level, ClientBingoState.state());
		int size = Math.max(64, Math.min(192, cfg.minimapSize));
		int blocksPerPixel = 2;
		int centerX = (int) mc.player.getX();
		int centerZ = (int) mc.player.getZ();
		if (++sampleTimer >= 20) {
			sampleTimer = 0;
			ClientMapData.ensure(level, centerX, centerZ, size, blocksPerPixel);
		}

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int x = switch (cfg.minimapAnchor()) {
			case TOP_LEFT, BOTTOM_LEFT -> cfg.minimapOffsetX;
			case TOP_RIGHT, BOTTOM_RIGHT -> screenW - size - cfg.minimapOffsetX;
		};
		int y = switch (cfg.minimapAnchor()) {
			case TOP_LEFT, TOP_RIGHT -> cfg.minimapOffsetY;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - size - cfg.minimapOffsetY;
		};

		graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2,
				((int) (Math.max(0.2f, Math.min(1f, cfg.minimapOpacity)) * 255) << 24) | 0x101418);
		graphics.outline(x - 2, y - 2, size + 4, size + 4, 0x80FFFFFF);

		int pixel = 2;
		for (int px = 0; px < size; px += pixel) {
			int worldX = centerX + (px - size / 2) * blocksPerPixel;
			for (int py = 0; py < size; py += pixel) {
				int worldZ = centerZ + (py - size / 2) * blocksPerPixel;
				int color = ClientMapData.colorAt(level, worldX, worldZ, blocksPerPixel);
				graphics.fill(x + px, y + py, x + px + pixel, y + py + pixel, color);
			}
		}

		int cx = x + size / 2;
		int cy = y + size / 2;
		graphics.fill(cx - 1, cy - 4, cx + 2, cy - 2, 0xFFFFFFFF);
		graphics.fill(cx - 4, cy - 1, cx - 2, cy + 2, 0xFFFFFFFF);
		graphics.fill(cx + 2, cy - 1, cx + 4, cy + 2, 0xFFFFFFFF);
		graphics.fill(cx - 1, cy + 2, cx + 2, cy + 4, 0xFFFFFFFF);
		graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFF5555);
		if (cfg.minimapNorthUp) {
			graphics.centeredText(mc.font, "N", cx, y + 1, 0xFFFFFFFF);
		}
		graphics.text(mc.font, Component.literal(centerX + ", " + centerZ),
				x + 2, y + size - 10, 0xCCFFFFFF, false);
	}
}
