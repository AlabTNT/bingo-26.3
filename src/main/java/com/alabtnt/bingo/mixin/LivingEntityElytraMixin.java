package com.alabtnt.bingo.mixin;

import com.alabtnt.bingo.game.ElytraStore;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the extra elytra slot actually glide without occupying the chest slot. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityElytraMixin {
	@Shadow
	protected abstract boolean canGlide();

	@Inject(method = "canGlide", at = @At("RETURN"), cancellable = true)
	private void bingo$canGlide(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) return;
		if (!((Object) this instanceof Player player)) return;
		if (ElytraStore.has(player) && !player.onGround() && !player.isPassenger()
				&& !player.hasEffect(MobEffects.LEVITATION)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
	private void bingo$updateFallFlying(CallbackInfo ci) {
		if (!((Object) this instanceof Player player)) return;
		if (!ElytraStore.has(player)) return;
		if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;
		if (player.level().isClientSide()) {
			ci.cancel();
			return;
		}
		if (!this.canGlide()) {
			player.stopFallFlying();
		} else if (player.tickCount % 20 == 0) {
			ElytraStore.damage(player);
		}
		ci.cancel();
	}
}
