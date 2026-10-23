package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/// Holy fire left behind by Holy Water. Its victim looks like it's burning, but unlike normal fire it isn't put
/// out by water or rain, and it ignores fire resistance and fire immunity. Burns once a second while it lasts,
/// and slows the victim down as much as Slowness II. Vampires can't keep up their Resistance while burning.
public class HolyFlameEffect extends MobEffect {
    private static final float DAMAGE_PER_SECOND = 1.0F;

    public HolyFlameEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF9A2E);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, CrimsonCovenant.id("effect.holy_flame"), -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    // Every tick, to keep the burning look up; damage only once a second
    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        // Shows the regular on-fire flames without real fire, so there's nothing for water to put out
        entity.setSharedFlagOnFire(true);
        if (entity.tickCount % 20 == 0) {
            entity.hurtServer(level, level.damageSources().source(ModDamageTypes.HOLY_FIRE), DAMAGE_PER_SECOND * (amplifier + 1));
        }
        return true;
    }
}
