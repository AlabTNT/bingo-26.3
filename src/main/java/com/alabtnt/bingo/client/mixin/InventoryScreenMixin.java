package com.alabtnt.bingo.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the extra elytra slot outline in the player inventory. */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryScreenMixin {
	@Shadow
	protected int leftPos;

	@Shadow
	protected int topPos;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void bingo$elytraSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!((Object) this instanceof InventoryScreen)) return;
		graphics.outline(leftPos + 151, topPos + 61, 18, 18, 0x80FFFFFF);
	}
}
