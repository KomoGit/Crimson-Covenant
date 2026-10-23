package com.zestudio.crimsoncovenant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.zestudio.crimsoncovenant.effect.EphemeralEffect;

import net.minecraft.world.entity.player.Player;

/// Lets a player under the Ephemeral effect drift through walls. At the top of every tick vanilla switches a
/// player's collisions off only if they're a spectator, and nothing can step in between with an event, so this
/// switches them off for ghosts right after.
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void crimsoncovenant$ghostsPassThroughWalls(CallbackInfo ci) {
        EphemeralEffect.tickGhostBody((Player) (Object) this);
    }
}
