package com.zestudio.crimsoncovenant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.zestudio.crimsoncovenant.hunter.HunterGear;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/// The hunters' potions only brew for a hunter of high enough rank (see HunterGear): the stand treats a brew beyond
/// its brewer's rank as nothing it knows how to brew, so it never starts.
@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin {
    @Inject(method = "isBrewable", at = @At("RETURN"), cancellable = true)
    private static void crimsoncovenant$keepHunterPotionsToHunters(ServerLevel level, BrewingStandBlockEntity stand, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !HunterGear.mayBrew(level, stand)) {
            cir.setReturnValue(false);
        }
    }
}
