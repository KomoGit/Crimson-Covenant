package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/// From the Potion of Garlic, usually thrown: undead (vampire players too) caught in it hit 40% softer per level and
/// drag their feet. The living don't mind it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class GarlicEffect extends MobEffect {
    public static final float WEAKENING_PER_LEVEL = 0.4F;
    private static final float MIN_DAMAGE_LEFT = 0.2F;
    private static final int SLOWNESS_REFRESH = 20;

    public GarlicEffect() {
        super(MobEffectCategory.HARMFUL, 0xE6DFC3);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return remainingDuration % SLOWNESS_REFRESH == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (Vampirism.isUndead(entity)) {
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_REFRESH + 10, amplifier, true, false, false));
        }
        return true;
    }

    /// How much of an undead's blow still lands under garlic
    public static float damageLeft(int amplifier) {
        return Math.max(MIN_DAMAGE_LEFT, 1.0F - WEAKENING_PER_LEVEL * (amplifier + 1));
    }

    @SubscribeEvent
    static void weakenUndeadBlows(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && Vampirism.isUndead(attacker)) {
            MobEffectInstance garlic = attacker.getEffect(ModEffects.GARLIC);
            if (garlic != null) {
                event.setAmount(event.getAmount() * damageLeft(garlic.getAmplifier()));
            }
        }
    }
}
