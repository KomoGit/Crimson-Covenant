package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/// Knocked Down (by Gravequake, see curse.Earthshatter): thrown flat on the ground. It can't walk, fly or jump, nothing
/// slides it along the ground, a creeper's fuse goes out, and it can't land a blow (see Earthshatter) until it's up
/// again. Players about see it lying flat (ModAttachments.KNOCKED_DOWN_UNTIL, see CrimsonCovenantClient).
public class KnockedDownEffect extends MobEffect {
    public KnockedDownEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B5A48);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, CrimsonCovenant.id("effect.knocked_down.speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(Attributes.FLYING_SPEED, CrimsonCovenant.id("effect.knocked_down.flying_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(Attributes.JUMP_STRENGTH, CrimsonCovenant.id("effect.knocked_down.jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        // However it came to be down (the quake, or a command), the players about see it lying there
        if (entity.getData(ModAttachments.KNOCKED_DOWN_UNTIL) == 0L) {
            MobEffect self = this;
            entity.getActiveEffects().stream().filter(effect -> effect.getEffect().value() == self).findFirst()
                    .ifPresent(effect -> entity.setData(ModAttachments.KNOCKED_DOWN_UNTIL, level.getGameTime() + effect.getDuration()));
        }
        Vec3 motion = entity.getDeltaMovement();
        if (motion.x != 0.0 || motion.z != 0.0 || motion.y > 0.0) {
            entity.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
            entity.syncVelocity = true;
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        if (entity instanceof Creeper creeper) {
            creeper.setSwellDir(-1);
        }
        return true;
    }
}
