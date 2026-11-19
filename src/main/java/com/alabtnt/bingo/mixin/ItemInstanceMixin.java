package com.alabtnt.bingo.mixin;

import net.minecraft.world.item.ItemInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Requirement 9: raise the stack limit from 64 to 128.
 *
 * <p>In 26.3 the stack size is read through {@link ItemInstance#getMaxStackSize()},
 * whose default implementation reads the {@code minecraft:max_stack_size} data
 * component. Injecting into the interface default covers every item, including
 * items whose component declares 16 (eggs, ender pearls, ...) or 1 (tools).</p>
 */
@Mixin(ItemInstance.class)
public interface ItemInstanceMixin {

	@Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true)
	private void bingo$raiseStackLimit(CallbackInfoReturnable<Integer> cir) {
		int vanilla = cir.getReturnValue();
		if (vanilla > 1 && vanilla < 128) {
			cir.setReturnValue(128);
		}
	}
}
