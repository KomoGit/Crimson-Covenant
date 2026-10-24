package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.entity.PlagueDoctor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/// The Plague, carried by rats and passed on by their bite. It saps the sick slowly (never below half a heart, like
/// poison) and leaves them hungry, and it's catching: now and then it jumps to someone standing close by. Rats carry
/// it without falling ill, and the undead, vampires among them, have no living blood for it to take hold in.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class PlagueEffect extends MobEffect {
    private static final int SAP_INTERVAL = 80;
    private static final int SPREAD_INTERVAL = 100;
    private static final float SPREAD_CHANCE = 0.2F;
    private static final double SPREAD_RANGE = 3.0;

    public PlagueEffect() {
        super(MobEffectCategory.HARMFUL, 0x7A8B3C);
    }

    public static boolean isImmune(LivingEntity entity) {
        return entity instanceof Rat || entity instanceof PlagueDoctor || Vampirism.isUndead(entity);
    }

    private static int sapInterval(int amplifier) {
        return Math.max(10, SAP_INTERVAL >> amplifier);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return remainingDuration % sapInterval(amplifier) == 0 || remainingDuration % SPREAD_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        MobEffectInstance plague = entity.getEffect(ModEffects.PLAGUE);
        int remaining = plague != null ? plague.getDuration() : 0;
        if (remaining % sapInterval(amplifier) == 0) {
            if (entity.getHealth() > 1.0F) {
                entity.hurtServer(level, entity.damageSources().magic(), 1.0F);
            }
            if (entity instanceof Player player) {
                player.causeFoodExhaustion(2.0F);
            }
        }
        if (plague != null && remaining % SPREAD_INTERVAL == 0 && entity.getRandom().nextFloat() < SPREAD_CHANCE) {
            spread(level, entity, plague);
        }
        return true;
    }

    // It jumps to someone close, with half the time left
    private static void spread(ServerLevel level, LivingEntity sick, MobEffectInstance plague) {
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, sick.getBoundingBox().inflate(SPREAD_RANGE),
                other -> other != sick && other.isAlive() && !other.hasEffect(ModEffects.PLAGUE) && !isImmune(other))) {
            near.addEffect(new MobEffectInstance(ModEffects.PLAGUE, Math.max(200, plague.getDuration() / 2), plague.getAmplifier()), sick);
            return;
        }
    }

    @SubscribeEvent
    static void immunity(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().is(ModEffects.PLAGUE) && isImmune(event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
