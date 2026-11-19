package com.alabtnt.bingo.client.screen;

import com.alabtnt.bingo.config.BoConfigs;
import com.alabtnt.bingo.config.ClientConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BOConfigScreen {
	private BOConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ClientConfig cfg = BoConfigs.client();
		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable("bingo.config.title"))
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable("bingo.config.category.hud"))
						.option(bool("bingo.config.hud.enabled", true, () -> cfg.hudEnabled, v -> cfg.hudEnabled = v))
						.option(bool("bingo.config.hud.timer", true, () -> cfg.showTimer, v -> cfg.showTimer = v))
						.option(enumOption("bingo.config.hud.anchor", ClientConfig.Anchor.TOP_LEFT,
								cfg::anchor, v -> cfg.hudAnchor = v.name().toLowerCase(Locale.ROOT)))
						.option(intOption("bingo.config.hud.offset_x", 4, -100, 400,
								() -> cfg.hudOffsetX, v -> cfg.hudOffsetX = v))
						.option(intOption("bingo.config.hud.offset_y", 4, -100, 400,
								() -> cfg.hudOffsetY, v -> cfg.hudOffsetY = v))
						.option(floatOption("bingo.config.hud.scale", 1.0f, 0.5f, 2.5f, 0.05f,
								() -> cfg.hudScale, v -> cfg.hudScale = v))
						.option(floatOption("bingo.config.hud.opacity", 0.85f, 0.15f, 1.0f, 0.05f,
								() -> cfg.hudOpacity, v -> cfg.hudOpacity = v))
						.option(bool("bingo.config.hud.background", true, () -> cfg.hudBackground, v -> cfg.hudBackground = v))
						.option(bool("bingo.config.hud.hide_debug", true, () -> cfg.hideOnDebug, v -> cfg.hideOnDebug = v))
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable("bingo.config.category.map"))
						.option(bool("bingo.config.map.minimap", true, () -> cfg.enableMinimap, v -> cfg.enableMinimap = v))
						.option(enumOption("bingo.config.map.anchor", ClientConfig.Anchor.TOP_RIGHT,
								cfg::minimapAnchor, v -> cfg.minimapAnchor = v.name().toLowerCase(Locale.ROOT)))
						.option(intOption("bingo.config.map.size", 96, 64, 192,
								() -> cfg.minimapSize, v -> cfg.minimapSize = v))
						.option(floatOption("bingo.config.map.opacity", 0.85f, 0.2f, 1.0f, 0.05f,
								() -> cfg.minimapOpacity, v -> cfg.minimapOpacity = v))
						.option(bool("bingo.config.map.north_up", true, () -> cfg.minimapNorthUp, v -> cfg.minimapNorthUp = v))
						.option(bool("bingo.config.map.worldmap", true, () -> cfg.enableWorldMap, v -> cfg.enableWorldMap = v))
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable("bingo.config.category.integrations"))
						.option(bool("bingo.config.integration.jei", true, () -> cfg.enableJei, v -> cfg.enableJei = v))
						.option(bool("bingo.config.integration.sorting", true, () -> cfg.enableInventorySorting, v -> cfg.enableInventorySorting = v))
						.option(LabelOption.create(Component.translatable("bingo.config.integration.note")))
						.build())
				.save(() -> {
					BoConfigs.saveClient();
					com.alabtnt.bingo.client.integration.IntegrationToggles.apply();
				})
				.build()
				.generateScreen(parent);
	}

	private static Option<Boolean> bool(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder()
				.name(Component.translatable(key))
				.description(OptionDescription.of(Component.translatable(key + ".desc")))
				.binding(def, get, set)
				.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
				.build();
	}

	private static Option<Integer> intOption(String key, int def, int min, int max,
			Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Integer>createBuilder()
				.name(Component.translatable(key))
				.description(OptionDescription.of(Component.translatable(key + ".desc")))
				.binding(def, get, set)
				.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(1))
				.build();
	}

	private static Option<Float> floatOption(String key, float def, float min, float max, float step,
			Supplier<Float> get, Consumer<Float> set) {
		return Option.<Float>createBuilder()
				.name(Component.translatable(key))
				.description(OptionDescription.of(Component.translatable(key + ".desc")))
				.binding(def, get, set)
				.controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step)
						.formatValue(v -> Component.literal(String.format(Locale.ROOT, "%.2f", v))))
				.build();
	}

	private static Option<ClientConfig.Anchor> enumOption(String key, ClientConfig.Anchor def,
			Supplier<ClientConfig.Anchor> get, Consumer<ClientConfig.Anchor> set) {
		return Option.<ClientConfig.Anchor>createBuilder()
				.name(Component.translatable(key))
				.description(OptionDescription.of(Component.translatable(key + ".desc")))
				.binding(def, get, set)
				.controller(opt -> EnumControllerBuilder.create(opt)
						.enumClass(ClientConfig.Anchor.class)
						.valueFormatter(v -> Component.translatable("bingo.config.anchor." + v.name().toLowerCase(Locale.ROOT))))
				.build();
	}
}
