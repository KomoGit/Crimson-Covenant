package com.zestudio.crimsoncovenant.block;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/// The net a Silver Net Trap drops. Like a cobweb, only worse: anything caught in it can barely move, and silver
/// burns the undead and werewolves tangled in it, once a second. It rots away after half a minute, or can be cut
/// sooner.
public class SilverNetBlock extends Block {
    private static final Vec3 TANGLED = new Vec3(0.1, 0.05, 0.1);
    public static final float BURN_DAMAGE = 1.0F;
    public static final int LIFETIME = 600;

    public SilverNetBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, LIFETIME);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.destroyBlock(pos, false);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        entity.makeStuckInBlock(state, TANGLED);
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity living && BearTrapBlock.isWeakToSilver(living)
                && serverLevel.getGameTime() % 20 == 0) {
            living.hurtServer(serverLevel, serverLevel.damageSources().source(ModDamageTypes.SILVER_TRAP), BURN_DAMAGE);
            serverLevel.sendParticles(ParticleTypes.SMOKE, living.getX(), living.getY(0.5), living.getZ(), 4, 0.2, 0.3, 0.2, 0.01);
        }
    }
}
