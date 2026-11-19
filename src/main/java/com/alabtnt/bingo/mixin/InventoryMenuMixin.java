package com.alabtnt.bingo.mixin;

import com.alabtnt.bingo.game.ElytraStore;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a dedicated elytra slot to the player inventory menu. */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void bingo$addElytraSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		((AbstractContainerMenuInvoker) (Object) this).bingo$addSlot(new Slot(ElytraStore.container(owner), 0, 152, 62) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Items.ELYTRA);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
	}
}
