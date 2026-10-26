package com.zestudio.crimsoncovenant.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.player.Player;

/// Hydrophobia, the first stage of Rabies (see Rabies): a sick animal won't eat and runs from water (Rabies.ShunWaterGoal).
/// A player who has it panics in water, the world swimming round them. A Potion of Cleanse cures it; left alone, it
/// turns into Rabies when it runs out.
public class HydrophobiaEffect extends MobEffect {
    private static final int CHECK_INTERVAL = 20;
    private static final int PANIC_TICKS = 100;

    public HydrophobiaEffect() {
        super(MobEffectCategory.HARMFUL, 0x3E6E8E);
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
        if (entity instanceof Player player && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, PANIC_TICKS));
        }
        return true;
    }
}
