package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// A gentle way down once something that held a player up (the Crimson Shroud, Blood Flight, the Potion of Ephemeral)
/// lets them go. They fall freely until the ground is near, within five blocks (farther the faster they're falling, so
/// they can't drop past it between ticks), then drift down under Slow Falling, however long that takes, until they
/// land. Nothing in between hurts them for the fall.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class SoftLanding {
    /// How near the ground they are when the slow fall takes hold
    public static final double CATCH_HEIGHT = 5.0;
    /// Slow Falling is given a little at a time, topped up while they're still coming down
    private static final int SLOW_FALL_TICKS = 20;
    /// Caught, they're slowed at once to no more than this (blocks a tick)
    private static final double CAUGHT_SPEED = 0.3;

    private SoftLanding() {}

    public static void begin(Player player) {
        if (!player.isCreative() && !player.isSpectator()) {
            player.setData(ModAttachments.SOFT_LANDING, true);
        }
    }

    public static boolean isLanding(Player player) {
        return player.getData(ModAttachments.SOFT_LANDING);
    }

    @SubscribeEvent
    static void comeDown(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isLanding(player)) {
            return;
        }
        // Down safe, or held up some other way again
        if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying || CrimsonShroudEffect.isShrouded(player)
                || player.isCreative() || player.isSpectator()) {
            player.removeData(ModAttachments.SOFT_LANDING);
            return;
        }
        Vec3 motion = player.getKnownMovement();
        double falling = Math.max(0.0, -motion.y);
        double lookAhead = CATCH_HEIGHT + falling * 2.0;
        Vec3 feet = player.position();
        HitResult ground = player.level().clip(new ClipContext(feet, feet.subtract(0.0, lookAhead, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (ground.getType() == HitResult.Type.MISS) {
            return;
        }
        MobEffectInstance slowFall = player.getEffect(MobEffects.SLOW_FALLING);
        if (slowFall == null || slowFall.getDuration() < SLOW_FALL_TICKS / 2) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, true));
        }
        if (falling > CAUGHT_SPEED) {
            player.setDeltaMovement(motion.x, -CAUGHT_SPEED, motion.z);
            player.syncVelocity = true;
        }
    }

    // However they come down, the fall doesn't hurt
    @SubscribeEvent
    static void noFallDamage(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && isLanding(player)) {
            event.setCanceled(true);
            player.removeData(ModAttachments.SOFT_LANDING);
        }
    }
}
