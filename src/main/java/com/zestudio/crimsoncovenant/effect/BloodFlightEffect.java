package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

/// The Flight skill: creative-style flight while it lasts. Once it runs out (or Holy Water washes it away), the
/// game takes flight away by itself, because the player no longer "may fly".
public class BloodFlightEffect extends MobEffect {
    public static final int DURATION = 80;
    /// For this long after take-off, a player whose client dropped out of flight is put back into it
    private static final int TAKEOFF_TICKS = 10;
    /// A soft landing once the flight wears off on its own
    private static final int LANDING_SLOW_FALL = 60;

    public BloodFlightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB0162B);
        this.addAttributeModifier(NeoForgeMod.CREATIVE_FLIGHT, CrimsonCovenant.id("effect.blood_flight"), 1.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        MobEffectInstance flight = entity.getEffect(ModEffects.BLOOD_FLIGHT);
        if (!(entity instanceof Player player) || flight == null) {
            return true;
        }
        // Blood Wings (upgraded, amplifier 1) fly twice as long, and end in a dive rather than a gentle landing
        boolean bloodWings = amplifier >= 1;
        if (flight.getDuration() <= 1) {
            if (bloodWings) {
                player.setData(ModAttachments.BLOOD_WINGS_DIVE, true);
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LANDING_SLOW_FALL, 0, false, false, true));
            }
        } else if (flight.getDuration() > DURATION * (amplifier + 1) - TAKEOFF_TICKS && !player.getAbilities().flying && !player.onGround()) {
            startFlying(player);
        }
        return true;
    }

    public static void startFlying(Player player) {
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }
}
