package com.zestudio.crimsoncovenant.block;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/// Silver spikes, set into the ground. Step on the plate and the spikes shoot up through whoever is standing on it,
/// then sink back a second later, ready for the next one. Silver bites the undead (vampire players too) and werewolves
/// much harder, and leaves them slowed.
public class SilverSpikesBlock extends Block {
    public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");
    public static final float STAB_DAMAGE = 3.0F;
    public static final float SILVER_BONUS = 5.0F;
    private static final int RETRACT_DELAY = 20;
    private static final int SLOW_DURATION = 60;
    private static final VoxelShape RETRACTED_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final VoxelShape EXTENDED_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 10.0, 16.0);

    public SilverSpikesBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(EXTENDED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(EXTENDED) ? EXTENDED_SHAPE : RETRACTED_SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EXTENDED);
    }

    private static boolean triggers(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && living.onGround() && !(living instanceof ArmorStand)
                && !(living instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!state.getValue(EXTENDED) && level instanceof ServerLevel serverLevel && triggers(entity)) {
            this.stab(serverLevel, pos, state);
        }
    }

    /// Up come the spikes, through everything standing on the plate
    private void stab(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(EXTENDED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, RETRACT_DELAY);
        DamageSource source = level.damageSources().source(ModDamageTypes.SILVER_TRAP);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), entity -> entity.isAlive() && !(entity instanceof ArmorStand))) {
            boolean silverWeak = BearTrapBlock.isWeakToSilver(victim);
            victim.hurtServer(level, source, STAB_DAMAGE + (silverWeak ? SILVER_BONUS : 0.0F));
            if (silverWeak) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_DURATION, 1));
                level.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY(0.3), victim.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
            }
        }
        level.playSound(null, pos, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS, 1.0F, 1.4F);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.6F);
    }

    /// Back down, ready again
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(EXTENDED)) {
            level.setBlock(pos, state.setValue(EXTENDED, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.6F);
        }
    }
}
