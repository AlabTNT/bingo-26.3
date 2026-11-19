package com.alabtnt.bingo.client.integration;

import com.alabtnt.bingo.client.screen.BOConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return BOConfigScreen::create;
	}
}
