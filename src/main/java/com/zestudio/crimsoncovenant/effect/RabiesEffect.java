package com.zestudio.crimsoncovenant.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fox.Fox;

/// Rabies, the second stage (see Rabies): incurable. The rabid foam at the mouth, attack people on sight and snap at
/// animals that come near (but still fear water, and anyone standing by it), and die when it runs out, two days on. All of that is in Rabies; this only keeps a sick fox
/// from eating, as Hydrophobia does.
public class RabiesEffect extends MobEffect {
    private static final int CHECK_INTERVAL = 20;

    public RabiesEffect() {
        super(MobEffectCategory.HARMFUL, 0x6E3A22);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return remainingDuration % CHECK_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof Fox fox) {
            Rabies.spitOutFood(fox);
        }
        return true;
    }
}
