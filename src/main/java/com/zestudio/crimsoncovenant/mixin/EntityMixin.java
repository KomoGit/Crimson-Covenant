package com.zestudio.crimsoncovenant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.zestudio.crimsoncovenant.curse.Earthshatter;

import net.minecraft.world.entity.Entity;

/// A creature thrown down by Gravequake is drawn lying flat, its body out along the ground beside the box it stands in,
/// so it can be picked out (struck, aimed at) there too: its pick radius stretches to cover where it lies. Nothing else
/// asks how far an entity can be picked from, so it's done here.
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
    private void crimsoncovenant$theFallenLieWide(CallbackInfoReturnable<Float> cir) {
        float lying = Earthshatter.lyingReach((Entity) (Object) this);
        if (lying > cir.getReturnValueF()) {
            cir.setReturnValue(lying);
        }
    }
}
