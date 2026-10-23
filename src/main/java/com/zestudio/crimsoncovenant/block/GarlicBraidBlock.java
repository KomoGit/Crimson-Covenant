package com.zestudio.crimsoncovenant.block;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/// A braid of garlic bulbs, hung from a ceiling or against a wall (or a door). Undead won't come near it: see
/// GarlicWard. FACING points away from the wall it hangs on.
public class GarlicBraidBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    private static final VoxelShape HANGING_SHAPE = Block.box(5.0, 1.0, 5.0, 11.0, 16.0, 11.0);
    // Against a north wall, rotated for the others
    private static final Map<Direction, VoxelShape> WALL_SHAPES = Shapes.rotateHorizontal(Block.box(5.0, 1.0, 0.0, 11.0, 15.0, 5.0));

    public GarlicBraidBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(HANGING, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HANGING) ? HANGING_SHAPE : WALL_SHAPES.get(state.getValue(FACING).getOpposite());
    }

    /// Hung from what's above, or against the wall behind it, whichever the player is facing first. Never stood
    /// on the floor.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState state;
            if (direction == Direction.UP) {
                state = this.defaultBlockState().setValue(HANGING, true).setValue(FACING, context.getHorizontalDirection().getOpposite());
            } else if (direction.getAxis().isHorizontal()) {
                state = this.defaultBlockState().setValue(HANGING, false).setValue(FACING, direction.getOpposite());
            } else {
                continue;
            }
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        return null;
    }

    private static Direction supportDirection(BlockState state) {
        return state.getValue(HANGING) ? Direction.UP : state.getValue(FACING).getOpposite();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = supportDirection(state);
        BlockPos supportPos = pos.relative(toSupport);
        BlockState support = level.getBlockState(supportPos);
        // Nailed to a door, it stays put when the door swings open
        if (support.getBlock() instanceof DoorBlock) {
            return true;
        }
        return state.getValue(HANGING)
                ? Block.canSupportCenter(level, supportPos, Direction.DOWN)
                : support.isFaceSturdy(level, supportPos, toSupport.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == supportDirection(state) && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HANGING);
    }
}
