package com.zestudio.crimsoncovenant.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/// Blood Loss, from a vampire bat swarm: the victim goes cold, with the same frosted screen and sluggishness as
/// standing in powder snow. It's kept just short of fully frozen, so it never deals freezing damage itself.
public class BloodLossEffect extends MobEffect {
    private static final float CHILL = 0.9F;

    public BloodLossEffect() {
        super(MobEffectCategory.HARMFUL, 0x6E0B14);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.canFreeze()) {
            entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), (int) (entity.getTicksRequiredToFreeze() * CHILL)));
        }
        return true;
    }
}
