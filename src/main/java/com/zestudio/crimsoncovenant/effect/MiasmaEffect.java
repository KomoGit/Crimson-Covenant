package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.VampireBat;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.entity.PlagueDoctor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/// Breathing the Miasma (see MiasmaCloud): a weak poison, slower than the real thing and never taking the last half
/// heart, that turns the stomach. Staying in it long enough also blinds (the cloud keeps count). The undead (vampires,
/// players and bats alike), iron golems and the rats who bring it breathe it without harm.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class MiasmaEffect extends MobEffect {
    private static final int POISON_INTERVAL = 50;
    private static final int NAUSEA_TICKS = 120;

    public MiasmaEffect() {
        super(MobEffectCategory.HARMFUL, 0x5C8A2E);
    }

    /// Who breathes the Miasma without harm: the rats who bring it, the Plague Doctor behind his mask, the undead
    /// (vampires, players and bats among them) with no breath to lose, and iron golems, who don't breathe at all
    public static boolean isImmune(LivingEntity entity) {
        return entity instanceof Rat || entity instanceof PlagueDoctor || entity instanceof IronGolem || Vampirism.isUndead(entity) || entity instanceof Vampire || entity instanceof VampireBat;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return remainingDuration % POISON_INTERVAL == 0 || remainingDuration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        int remaining = entity.getEffect(ModEffects.MIASMA) instanceof MobEffectInstance miasma ? miasma.getDuration() : 0;
        if (remaining % POISON_INTERVAL == 0 && entity.getHealth() > 1.0F) {
            entity.hurtServer(level, entity.damageSources().magic(), 1.0F);
        }
        MobEffectInstance nausea = entity.getEffect(MobEffects.NAUSEA);
        if (nausea == null || nausea.getDuration() < NAUSEA_TICKS / 2) {
            entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0, false, false, true));
        }
        return true;
    }

    @SubscribeEvent
    static void immunity(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().is(ModEffects.MIASMA) && isImmune(event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
