package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/// Shackles of the Damned (the Enchain curse): chained where it stands. It can't walk, fly or jump, and nothing slides it
/// along the ground (a blow's knockback included), though it still falls. Glowing ghostly chains are seen wrapped round
/// it and staked into the ground (drawn by client.renderer.SpectralChains), rattling now and then.
public class EnchainedEffect extends MobEffect {
    private static final int SHOW_INTERVAL = 3;
    private static final int RATTLE_INTERVAL = 30;

    public EnchainedEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A5A6A);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, CrimsonCovenant.id("effect.enchained.speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(Attributes.FLYING_SPEED, CrimsonCovenant.id("effect.enchained.flying_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(Attributes.JUMP_STRENGTH, CrimsonCovenant.id("effect.enchained.jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        // However it came to be chained (the curse, or a command), the players about see the chains
        if (!entity.getData(ModAttachments.ENCHAINED_LOOK)) {
            entity.setData(ModAttachments.ENCHAINED_LOOK, true);
        }
        // Held: no sliding about, though it can still fall
        Vec3 motion = entity.getDeltaMovement();
        if (motion.x != 0.0 || motion.z != 0.0 || motion.y > 0.0) {
            entity.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
            entity.syncVelocity = true;
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        if (entity.tickCount % SHOW_INTERVAL == 0) {
            showChains(level, entity);
        }
        if (entity.tickCount % RATTLE_INTERVAL == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 0.6F, 0.6F);
        }
        return true;
    }

    /// A faint cold shimmer coming off the chains
    public static void showChains(ServerLevel level, LivingEntity entity) {
        double radius = entity.getBbWidth() * 0.5 + 0.1;
        double angle = entity.tickCount * 0.4;
        double x = entity.getX() + Math.cos(angle) * radius;
        double z = entity.getZ() + Math.sin(angle) * radius;
        level.sendParticles(new DustParticleOptions(Curse.COLOUR, 0.6F), x, entity.getY(0.1 + (entity.tickCount % 7) * 0.1), z, 1, 0.02, 0.02, 0.02, 0.0);
        if (entity.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, entity.getX(), entity.getY() + 0.05, entity.getZ(), 1, radius * 0.8, 0.0, radius * 0.8, 0.0);
        }
    }
}
