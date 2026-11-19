package com.alabtnt.bingo.client.hud;

import com.alabtnt.bingo.client.ClientBingoState;
import com.alabtnt.bingo.config.BoConfigs;
import com.alabtnt.bingo.config.ClientConfig;
import com.alabtnt.bingo.network.BingoStatePayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class BoardHud implements HudElement {
	private static final int TILE = 18;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ClientConfig cfg = BoConfigs.client();
		if (!cfg.hudEnabled || mc.player == null) return;
		if (cfg.hideOnDebug && mc.getDebugOverlay().showDebugScreen()) return;
		if (cfg.hideOnChat && mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) return;
		BingoStatePayload state = ClientBingoState.state();
		if (state == null || state.cells().isEmpty() || state.boardSize() <= 0) return;

		int size = state.boardSize();
		int grid = size * TILE;
		int width = grid + 4;
		int height = grid + 4 + (cfg.showTimer ? 12 : 0);
		float scale = Math.max(0.5f, Math.min(2.5f, cfg.hudScale));
		int drawW = Math.round(width * scale);
		int drawH = Math.round(height * scale);
		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int x = switch (cfg.anchor()) {
			case TOP_LEFT, BOTTOM_LEFT -> cfg.hudOffsetX;
			case TOP_RIGHT, BOTTOM_RIGHT -> screenW - drawW - cfg.hudOffsetX;
		};
		int y = switch (cfg.anchor()) {
			case TOP_LEFT, TOP_RIGHT -> cfg.hudOffsetY;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - drawH - cfg.hudOffsetY;
		};

		int bg = ((int) (Math.max(0.15f, Math.min(1f, cfg.hudOpacity)) * 255) << 24) | 0x101418;
		int ownIndex = -1;
		for (int i = 0; i < state.teams().size(); i++) {
			if (state.teams().get(i).id().equals(state.myTeam())) ownIndex = i;
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale);
		if (cfg.hudBackground) {
			graphics.fill(0, 0, width, height, bg);
			graphics.outline(0, 0, width, height, 0x60FFFFFF);
		}
		int gridY = cfg.showTimer ? 12 : 4;
		for (BingoStatePayload.CellDto cell : state.cells()) {
			int col = cell.index() % size;
			int row = cell.index() / size;
			int cx = 2 + col * TILE;
			int cy = gridY + row * TILE;
			graphics.fill(cx, cy, cx + TILE - 1, cy + TILE - 1, 0x40FFFFFF);
			if (!cell.revealed()) {
				graphics.centeredText(mc.font, "?", cx + TILE / 2 - 1, cy + 4, 0xFF888888);
				continue;
			}
			ItemStack stack = ClientBingoState.displayStack(cell);
			if (!stack.isEmpty()) {
				graphics.item(stack, cx + 1, cy + 1);
			}
			int mask = cell.completedMask();
			if (mask != 0) {
				String teamId = state.teams().get(Integer.numberOfTrailingZeros(mask)).id();
				int color = ownIndex >= 0 && (mask & (1 << ownIndex)) != 0
						? 0xA000FF66
						: 0x80000000 | (ClientBingoState.color(teamId) & 0xFFFFFF);
				graphics.fill(cx, cy, cx + TILE - 1, cy + TILE - 1, color);
			}
			String owner = cell.owner();
			if (owner != null && !owner.isEmpty()) {
				graphics.outline(cx, cy, TILE - 1, TILE - 1, 0xFF000000 | ClientBingoState.color(owner));
			}
		}
		if (cfg.showTimer) {
			Component text = Component.literal(ClientBingoState.time());
			graphics.text(mc.font, text, 3, 2, 0xFFFFFFFF, true);
		}
		graphics.pose().popMatrix();
	}
}
