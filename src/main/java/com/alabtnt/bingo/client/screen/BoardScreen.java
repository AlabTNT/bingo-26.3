package com.alabtnt.bingo.client.screen;

import com.alabtnt.bingo.client.ClientBingoState;
import com.alabtnt.bingo.network.BingoStatePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class BoardScreen extends Screen {
	private static final int TILE = 34;
	private final Screen parent;

	public BoardScreen(Screen parent) {
		super(Component.translatableWithFallback("bingo.screen.board", "Bingo Card"));
		this.parent = parent;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xC0101418);
		BingoStatePayload state = ClientBingoState.state();
		if (state == null || state.cells().isEmpty()) {
			graphics.centeredText(font, Component.translatableWithFallback("bingo.screen.no_game",
					"No Bingo round is running."), width / 2, height / 2, 0xFFFFFFFF);
			return;
		}
		int size = state.boardSize();
		int grid = size * TILE;
		int panelW = grid + 24;
		int panelH = grid + 96;
		int left = (width - panelW) / 2;
		int top = (height - panelH) / 2;
		graphics.fill(left, top, left + panelW, top + panelH, 0xE0202630);
		graphics.outline(left, top, panelW, panelH, 0x80FFFFFF);

		graphics.centeredText(font, Component.translatableWithFallback("bingo.screen.board", "Bingo Card"),
				width / 2, top + 8, 0xFFFFFFFF);
		String timer = ClientBingoState.time();
		graphics.centeredText(font, Component.literal(timer), width / 2, top + 22, 0xFFFFD166);

		int gridX = left + 12;
		int gridY = top + 40;
		BingoStatePayload.CellDto hovered = null;
		for (BingoStatePayload.CellDto cell : state.cells()) {
			int col = cell.index() % size;
			int row = cell.index() / size;
			int cx = gridX + col * TILE;
			int cy = gridY + row * TILE;
			graphics.fill(cx, cy, cx + TILE - 2, cy + TILE - 2, 0x30FFFFFF);
			if (!cell.revealed()) {
				graphics.centeredText(font, "?", cx + TILE / 2 - 2, cy + TILE / 2 - 5, 0xFF888888);
			} else {
				ItemStack stack = ClientBingoState.displayStack(cell);
				if (!stack.isEmpty()) {
					graphics.pose().pushMatrix();
					graphics.pose().translate(cx + 4, cy + 4);
					graphics.pose().scale(1.6f);
					graphics.item(stack, 0, 0);
					graphics.pose().popMatrix();
				}
				if (cell.completedMask() != 0) {
					graphics.fill(cx, cy, cx + TILE - 2, cy + TILE - 2, 0x7000FF66);
				}
				if (cell.owner() != null && !cell.owner().isEmpty()) {
					graphics.outline(cx, cy, TILE - 2, TILE - 2,
							0xFF000000 | ClientBingoState.color(cell.owner()));
				}
			}
			if (mouseX >= cx && mouseX < cx + TILE - 2 && mouseY >= cy && mouseY < cy + TILE - 2) {
				hovered = cell;
			}
		}

		int y = gridY + grid + 6;
		if (state.totalTime() > 0) {
			int progress = (int) (grid * (1f - (float) state.timeLeft() / state.totalTime()));
			graphics.fill(gridX, y, gridX + grid, y + 3, 0x40FFFFFF);
			graphics.fill(gridX, y, gridX + progress, y + 3, 0xFF4BC46A);
		}
		y += 10;
		if (state.teams() != null) {
			int tx = gridX;
			for (BingoStatePayload.TeamDto team : state.teams()) {
				String label = Component.translatable("bingo.team." + team.id()).getString()
						+ " " + team.score() + "/" + (size * size);
				graphics.text(font, Component.literal(label), tx, y,
						0xFF000000 | ClientBingoState.color(team.id()), false);
				tx += font.width(label) + 10;
				if (tx > gridX + grid) {
					tx = gridX;
					y += 10;
				}
			}
		}
		graphics.text(font, Component.translatableWithFallback("bingo.screen.hint",
				"ESC to close · Y reopens"), left + 12, top + panelH - 14, 0xFFAAAAAA, false);

		if (hovered != null && hovered.revealed()) {
			Component name;
			if (hovered.kind().equals("ADVANCEMENT") && hovered.advancement() != null) {
				name = advancementName(hovered);
			} else {
				ItemStack stack = ClientBingoState.displayStack(hovered);
				name = stack.isEmpty() ? Component.literal(hovered.displayItem()) : stack.getHoverName();
			}
			Component status = hovered.completedMask() != 0
					? Component.translatableWithFallback("bingo.screen.completed", "Completed")
					: Component.translatableWithFallback("bingo.screen.pending", "Not completed");
			graphics.setTooltipForNextFrame(name.copy().append(Component.literal("\n")).append(status),
					mouseX, mouseY);
		}
	}

	private Component advancementName(BingoStatePayload.CellDto cell) {
		var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
		if (connection != null) {
			var id = net.minecraft.resources.Identifier.tryParse(cell.advancement());
			if (id != null) {
				var holder = connection.getAdvancements().get(id);
				if (holder != null && holder.value().display().isPresent()) {
					return holder.value().display().get().title();
				}
			}
		}
		return Component.literal(cell.advancement());
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
