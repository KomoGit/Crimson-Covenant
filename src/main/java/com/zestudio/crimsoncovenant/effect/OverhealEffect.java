package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.BloodOrb;
import com.zestudio.crimsoncovenant.registry.ModEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/// From Blood Parade: healing past full health, held as extra (absorption) hearts, up to 10 of them. They hold for
/// as long as the orb lasts; once it's gone they drain away steadily over its last 10 seconds, rather than all at
/// once.
public class OverhealEffect extends MobEffect {
    public static final double MAX_OVERHEAL = 20.0;

    public OverhealEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB0162B);
        this.addAttributeModifier(Attributes.MAX_ABSORPTION, CrimsonCovenant.id("effect.overheal"), MAX_OVERHEAL, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return remainingDuration <= BloodOrb.OVERHEAL_DURATION;
    }

    /// Fading: each tick takes an even share of what's left, so the extra hearts reach nothing just as it ends
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        MobEffectInstance overheal = entity.getEffect(ModEffects.OVERHEAL);
        if (overheal != null && overheal.getDuration() > 0) {
            float absorption = entity.getAbsorptionAmount();
            entity.setAbsorptionAmount(absorption - absorption / overheal.getDuration());
        }
        return true;
    }
}
