package com.zestudio.crimsoncovenant.block;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/// A rats' burrow: earth with a hole gnawed into one side (or the top), or a Rat Hole gnawed into the side of wood, home to up to six rats, a pair and their young (see
/// RatBurrowBlockEntity). Rats dig them themselves. Break one open and every rat that lives there comes for you.
public class RatBurrowBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public RatBurrowBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /// A burrow of either kind: earth or wood
    public static boolean isBurrow(BlockState state) {
        return state.getBlock() instanceof RatBurrowBlock;
    }

    /// Where a rat stands to go in or come out
    public static BlockPos doorstep(BlockState state, BlockPos pos) {
        return pos.relative(state.hasProperty(FACING) ? state.getValue(FACING) : Direction.UP);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RatBurrowBlockEntity burrow) {
            burrow.releaseAll(player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RatBurrowBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.RAT_BURROW.get(), RatBurrowBlockEntity::serverTick);
    }

    // A little dust stirs at the hole now and then
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(20) == 0) {
            BlockPos door = doorstep(state, pos);
            level.addParticle(ParticleTypes.ASH, door.getX() + 0.5 - state.getValue(FACING).getStepX() * 0.45, door.getY() + 0.3,
                    door.getZ() + 0.5 - state.getValue(FACING).getStepZ() * 0.45, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
