package com.zestudio.crimsoncovenant.block;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.entity.ThroneSeat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/// A Blood Throne: a seat of black stone, its velvet soaked through, skulls set in its arms and crowning its high
/// back. Sit on it (use it) and, for as long as you stay seated, it siphons the life of every villager held captive in
/// an Iron Maiden around it, as a coffin does while you sleep: you heal, and a vampire drinks too (see ThroneSeat).
/// It's only for sitting on, never sleeping.
public class BloodThroneBlock extends HorizontalDirectionalBlock {
    /// Where the sitter sits, above the floor
    public static final double SEAT_HEIGHT = 8.5 / 16.0;
    private static final int BLOOD_RED = 0x7A0610;
    // Drawn facing north: the seat in front, the back to the south
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(Block.box(0.0, 0.0, 0.0, 16.0, 8.5, 16.0),
            Block.box(1.0, 8.5, 13.0, 15.0, 16.0, 16.0), Block.box(0.0, 8.5, 2.0, 2.5, 12.0, 14.0), Block.box(13.5, 8.5, 2.0, 16.0, 12.0, 14.0)));

    public BloodThroneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    // It faces whoever sets it down
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && !player.isPassenger()) {
            sit(serverLevel, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    /// Seats the player on the throne at `pos`, if no one's on it already. Returns the seat, or null.
    public static @Nullable ThroneSeat sit(ServerLevel level, BlockPos pos, Player player) {
        if (!level.getEntitiesOfClass(ThroneSeat.class, new AABB(pos)).isEmpty()) {
            return null;
        }
        ThroneSeat seat = ThroneSeat.at(level, pos);
        level.addFreshEntity(seat);
        if (!player.startRiding(seat)) {
            seat.discard();
            return null;
        }
        return seat;
    }

    // The velvet is soaked through: now and then it drips
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            Direction front = state.getValue(FACING);
            double x = pos.getX() + 0.5 + front.getStepX() * 0.45 + (random.nextDouble() - 0.5) * 0.6 * Math.abs(front.getStepZ());
            double z = pos.getZ() + 0.5 + front.getStepZ() * 0.45 + (random.nextDouble() - 0.5) * 0.6 * Math.abs(front.getStepX());
            level.addParticle(new DustParticleOptions(BLOOD_RED, 0.6F), x, pos.getY() + 0.5, z, 0.0, -0.03, 0.0);
        }
    }
}
