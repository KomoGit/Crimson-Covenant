package com.zestudio.crimsoncovenant.block;

import java.util.Optional;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/// A silver bear trap, set on the ground. The first creature to step in it (anything on foot; not a creative or
/// spectating player) is bitten and held fast: it can't move or jump until someone breaks the trap. The silver jaws
/// bite much deeper into the undead (vampire players too) and werewolves.
///
/// A sprung trap with nobody in it can be opened again by hand. Which creature a trap is holding is kept on the
/// creature (ModAttachments.TRAPPED_IN, sent to a player's own client so their movement is held there too).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class BearTrapBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty SPRUNG = BooleanProperty.create("sprung");
    public static final float BITE_DAMAGE = 4.0F;
    /// Added to the bite for anything silver hurts
    public static final float SILVER_BONUS = 6.0F;
    /// What's left of a held creature's movement. Not quite zero: the game reads a zero multiplier as "not stuck".
    private static final Vec3 HELD = new Vec3(1.0E-3, 1.0E-3, 1.0E-3);
    private static final VoxelShape OPEN_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0);
    private static final VoxelShape SPRUNG_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 6.0, 14.0);

    public BearTrapBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SPRUNG, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SPRUNG) ? SPRUNG_SHAPE : OPEN_SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        builder.add(FACING, SPRUNG);
    }

    public static boolean isWeakToSilver(LivingEntity entity) {
        return entity.is(ModTags.Entities.WEAK_TO_SILVER) || (entity instanceof Player player && Vampirism.isVampire(player));
    }

    private static boolean canSpring(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && living.onGround() && !(living instanceof ArmorStand)
                && !(living instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (state.getValue(SPRUNG)) {
            // Only the one it bit is held: anyone else can step over a sprung trap
            if (isHeldBy(entity, pos)) {
                entity.makeStuckInBlock(state, HELD);
            }
        } else if (level instanceof ServerLevel serverLevel && canSpring(entity)) {
            spring(serverLevel, pos, state, (LivingEntity) entity);
        }
    }

    /// The trap holding this creature, if one is
    public static Optional<BlockPos> heldIn(Entity entity) {
        return entity.hasData(ModAttachments.TRAPPED_IN) ? entity.getData(ModAttachments.TRAPPED_IN) : Optional.empty();
    }

    /// Wrenches a trap apart: a Master Vampire tearing free of it, or hunters prying a friend out. Pried open, it
    /// drops to be used again; torn apart, it's wrecked.
    public static void breakOpen(ServerLevel level, BlockPos pos, Entity breaker, boolean drop) {
        if (!(level.getBlockState(pos).getBlock() instanceof BearTrapBlock)) {
            return;
        }
        level.playSound(null, pos, drop ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.0F, drop ? 0.7F : 1.2F);
        level.destroyBlock(pos, drop, breaker);
    }

    public static boolean isHeldBy(Entity entity, BlockPos pos) {
        return entity.hasData(ModAttachments.TRAPPED_IN) && entity.getData(ModAttachments.TRAPPED_IN).map(pos::equals).orElse(false);
    }

    private static void spring(ServerLevel level, BlockPos pos, BlockState state, LivingEntity victim) {
        level.setBlock(pos, state.setValue(SPRUNG, true), Block.UPDATE_ALL);
        float damage = BITE_DAMAGE + (isWeakToSilver(victim) ? SILVER_BONUS : 0.0F);
        victim.hurtServer(level, level.damageSources().source(ModDamageTypes.BEAR_TRAP), damage);
        // Then pulled into the middle of the jaws, after the bite so its knockback can't throw them clear
        Vec3 middle = Vec3.atBottomCenterOf(pos);
        if (victim instanceof ServerPlayer player) {
            player.teleportTo(middle.x, middle.y, middle.z);
        } else {
            victim.setPos(middle);
        }
        victim.setDeltaMovement(Vec3.ZERO);
        // The position handed to entityInside is a cursor the game moves on, so keep a copy
        victim.setData(ModAttachments.TRAPPED_IN, Optional.of(pos.immutable()));

        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 1.6F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), middle.x, middle.y + 0.3, middle.z, 12, 0.2, 0.1, 0.2, 0.1);
        if (isWeakToSilver(victim)) {
            // Silver on undead flesh
            level.sendParticles(ParticleTypes.SMOKE, middle.x, middle.y + 0.4, middle.z, 10, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /// Opening a sprung trap by hand, once nobody is caught in it
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(SPRUNG)) {
            return InteractionResult.PASS;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), entity -> isHeldBy(entity, pos)).isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(SPRUNG, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    /// Free once the trap is broken (or opened, or the creature is somehow somewhere else)
    @SubscribeEvent
    static void releaseWhenBroken(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || entity.tickCount % 5 != 0 || !entity.hasData(ModAttachments.TRAPPED_IN)) {
            return;
        }
        Optional<BlockPos> trap = entity.getData(ModAttachments.TRAPPED_IN);
        if (trap.isEmpty()) {
            return;
        }
        BlockState state = entity.level().getBlockState(trap.get());
        boolean stillHeld = state.getBlock() instanceof BearTrapBlock && state.getValue(SPRUNG) && entity.isAlive()
                && entity.blockPosition().closerThan(trap.get(), 1.5);
        if (!stillHeld) {
            entity.removeData(ModAttachments.TRAPPED_IN);
        }
    }
}
