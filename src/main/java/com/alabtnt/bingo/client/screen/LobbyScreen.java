package com.alabtnt.bingo.client.screen;

import com.alabtnt.bingo.client.ClientBingoState;
import com.alabtnt.bingo.network.BingoActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LobbyScreen extends Screen {
	private final Screen parent;

	public LobbyScreen(Screen parent) {
		super(Component.translatableWithFallback("bingo.screen.lobby", "Bingo Lobby"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int y = height / 2 - 40;
		var state = ClientBingoState.state();
		if (state != null) {
			int buttonWidth = 110;
			int x = cx - buttonWidth - 4;
			for (var team : state.teams()) {
				Button button = Button.builder(
						Component.translatable("bingo.team." + team.id()),
						b -> ClientPlayNetworking.send(new BingoActionPayload("team", team.id())))
						.bounds(x, y, buttonWidth, 20).build();
				addRenderableWidget(button);
				y += 24;
				if (y > height / 2 + 40) {
					y = height / 2 - 40;
					x = cx + 4;
				}
			}
		}
		addRenderableWidget(Button.builder(
				Component.translatableWithFallback("bingo.screen.ready", "Ready"),
				b -> ClientPlayNetworking.send(new BingoActionPayload("ready", "")))
				.bounds(cx - 60, height - 70, 120, 20).build());
		addRenderableWidget(Button.builder(
				Component.translatableWithFallback("bingo.screen.start", "Start round"),
				b -> ClientPlayNetworking.send(new BingoActionPayload("start", "")))
				.bounds(cx - 60, height - 46, 120, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xC0101418);
		graphics.centeredText(font, Component.translatableWithFallback("bingo.screen.lobby", "Bingo Lobby"),
				width / 2, height / 2 - 80, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatableWithFallback("bingo.screen.lobby.hint",
						"Choose a team, then press Ready. Operators can start the round."),
				width / 2, height / 2 - 64, 0xFFAAAAAA);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
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
