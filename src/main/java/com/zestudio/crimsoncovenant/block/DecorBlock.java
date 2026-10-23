package com.zestudio.crimsoncovenant.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/// A decorative block that faces the player who placed it and has its own hitbox, such as the gravestones and
/// the villager workstations. The shape is given for a block facing north and rotated for the other directions;
/// it should match the block model's elements.
public class DecorBlock extends HorizontalDirectionalBlock {
    private final Map<Direction, VoxelShape> shapes;

    public DecorBlock(BlockBehaviour.Properties properties, VoxelShape northShape) {
        super(properties);
        this.shapes = Shapes.rotateHorizontal(northShape);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /// Shape made of `Block.box` boxes, in 1/16th of a block, for a block facing north
    public static VoxelShape shape(double[]... boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
        }
        return shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
