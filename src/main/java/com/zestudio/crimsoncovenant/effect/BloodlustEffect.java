package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/// Bloodlust: what a vampire feels under a blood moon. Faster movement, faster attacks and steady regeneration.
/// Vampire mobs don't use the attack speed attribute, so their melee checks for this effect directly.
public class BloodlustEffect extends MobEffect {
    private static final int REGENERATION_INTERVAL = 40;

    public BloodlustEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC0102A);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, CrimsonCovenant.id("effect.bloodlust.speed"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(Attributes.ATTACK_SPEED, CrimsonCovenant.id("effect.bloodlust.attack_speed"), 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return remainingDuration % REGENERATION_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(1.0F);
        }
        return true;
    }
}
