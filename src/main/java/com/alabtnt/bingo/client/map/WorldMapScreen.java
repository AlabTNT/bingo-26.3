package com.alabtnt.bingo.client.map;

import com.alabtnt.bingo.client.ClientBingoState;
import com.alabtnt.bingo.config.BoConfigs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class WorldMapScreen extends Screen {
	private final Screen parent;
	private int centerX;
	private int centerZ;
	private double blocksPerPixel = 4.0;
	private boolean dragging;
	private double lastMouseX;
	private double lastMouseY;

	public WorldMapScreen(Screen parent) {
		super(Component.translatableWithFallback("bingo.map.title", "Bingo World Map"));
		this.parent = parent;
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player != null) {
			centerX = (int) player.getX();
			centerZ = (int) player.getZ();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		graphics.fill(0, 0, width, height, 0xF0101418);
		if (mc.level == null || mc.player == null) {
			graphics.centeredText(font, Component.translatableWithFallback("bingo.map.no_world",
					"No world loaded"), width / 2, height / 2, 0xFFFFFFFF);
			return;
		}
		ClientMapData.ensureKey(mc.level, ClientBingoState.state());
		int pixel = 4;
		int step = Math.max(4, (int) Math.round(blocksPerPixel * 2));
		ClientMapData.ensure(mc.level, centerX, centerZ,
				(int) (Math.max(width, height) * blocksPerPixel / pixel), step);

		for (int sx = 0; sx < width; sx += pixel) {
			int worldX = centerX + (int) ((sx - width / 2) * blocksPerPixel);
			for (int sy = 20; sy < height - 20; sy += pixel) {
				int worldZ = centerZ + (int) ((sy - height / 2) * blocksPerPixel);
				int color = ClientMapData.colorAt(mc.level, worldX, worldZ, step);
				graphics.fill(sx, sy, sx + pixel, sy + pixel, color);
			}
		}

		int px = (int) (width / 2 + (mc.player.getX() - centerX) / blocksPerPixel);
		int py = (int) (height / 2 + (mc.player.getZ() - centerZ) / blocksPerPixel);
		graphics.fill(px - 3, py - 3, px + 4, py + 4, 0xFFFF5555);
		graphics.outline(px - 5, py - 5, 11, 11, 0xFFFFFFFF);

		graphics.fill(0, 0, width, 20, 0xB0101418);
		graphics.centeredText(font, Component.translatableWithFallback("bingo.map.title", "Bingo World Map"),
				width / 2, 6, 0xFFFFFFFF);
		graphics.text(font, Component.literal(centerX + ", " + centerZ + "  |  "
						+ String.format("%.1f", blocksPerPixel) + " b/px"),
				6, height - 26, 0xFFCCCCCC, false);
		graphics.text(font, Component.translatableWithFallback("bingo.map.hint",
				"Drag to pan · Scroll to zoom · ESC to close"), 6, height - 14, 0xFF888888, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			dragging = true;
			lastMouseX = event.x();
			lastMouseY = event.y();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			centerX -= (int) ((event.x() - lastMouseX) * blocksPerPixel);
			centerZ -= (int) ((event.y() - lastMouseY) * blocksPerPixel);
			lastMouseX = event.x();
			lastMouseY = event.y();
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY > 0) {
			blocksPerPixel = Math.max(1.0, blocksPerPixel * 0.8);
		} else if (scrollY < 0) {
			blocksPerPixel = Math.min(32.0, blocksPerPixel * 1.25);
		}
		return true;
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
