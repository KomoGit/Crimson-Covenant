package com.zestudio.crimsoncovenant.block;

import java.util.Comparator;

import com.zestudio.crimsoncovenant.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/// A bundle of silver-threaded net hung from a ceiling. It watches the four blocks below it, and when anything walks
/// underneath it drops: a Silver Net lands on the creature and tangles it up. A sprung trap is just its frame, until
/// someone ties a fresh net in (use it by hand).
public class SilverNetTrapBlock extends Block {
    public static final BooleanProperty SPRUNG = BooleanProperty.create("sprung");
    public static final int REACH = 4;
    private static final int WATCH_INTERVAL = 10;
    private static final VoxelShape SHAPE = Block.box(4.0, 8.0, 4.0, 12.0, 16.0, 12.0);

    public SilverNetTrapBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SPRUNG, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.UP && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPRUNG);
    }

    // It keeps watch with a scheduled tick, started when it's placed (or re-armed)
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!state.getValue(SPRUNG)) {
            level.scheduleTick(pos, this, WATCH_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SPRUNG)) {
            return;
        }
        AABB below = new AABB(pos.getX(), pos.getY() - REACH, pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ() + 1);
        LivingEntity victim = level.getEntitiesOfClass(LivingEntity.class, below, SilverNetTrapBlock::catches).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))).orElse(null);
        if (victim == null || !this.drop(level, pos, state, victim)) {
            level.scheduleTick(pos, this, WATCH_INTERVAL);
        }
    }

    private static boolean catches(LivingEntity entity) {
        return entity.isAlive() && !(entity instanceof ArmorStand) && !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /// The net falls over the creature: a Silver Net where it stands, and over its head too if there's room
    private boolean drop(ServerLevel level, BlockPos pos, BlockState state, LivingEntity victim) {
        BlockPos feet = victim.blockPosition();
        if (!level.getBlockState(feet).canBeReplaced()) {
            return false;
        }
        BlockState net = ModBlocks.SILVER_NET.get().defaultBlockState();
        level.setBlock(feet, net, Block.UPDATE_ALL);
        if (victim.getBbHeight() > 1.0F && level.getBlockState(feet.above()).canBeReplaced() && !feet.above().equals(pos)) {
            level.setBlock(feet.above(), net, Block.UPDATE_ALL);
        }
        level.setBlock(pos, state.setValue(SPRUNG, true), Block.UPDATE_ALL);
        level.playSound(null, feet, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.2F, 0.7F);
        level.playSound(null, feet, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);
        return true;
    }

    /// Tying in a fresh net re-arms it
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(SPRUNG)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(SPRUNG, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
