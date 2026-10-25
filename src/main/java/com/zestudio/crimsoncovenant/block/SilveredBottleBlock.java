package com.zestudio.crimsoncovenant.block;

import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.zestudio.crimsoncovenant.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.guide.Souls;

/// A Silvered Bottle: a glass bottle lined with silver dust, the one thing that can hold a ghost. Use an empty one on
/// a ghost to bottle it (and a full one on thin air to let it out again). Either can be set down on a floor or a
/// table, and a ghost inside glows a faint blue through the glass, looking out at whoever set it down. Bottled ghosts
/// will one day strengthen the Ancient Translator.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class SilveredBottleBlock extends Block implements EntityBlock {
    public static final BooleanProperty GHOST = BooleanProperty.create("ghost");
    /// Which way the ghost inside looks
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /// How brightly a bottled ghost glows
    public static final int GHOST_LIGHT = 6;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(5.0, 0.0, 5.0, 11.0, 7.0, 11.0), Block.box(6.0, 7.0, 6.0, 10.0, 10.5, 10.0));

    public SilveredBottleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(GHOST, false).setValue(FACING, Direction.NORTH));
    }

    // The ghost looks back at whoever sets it down
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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

    // The ghost shimmers inside (see SilveredBottleBlockEntity)
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SilveredBottleBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || !state.getValue(GHOST) || type != ModBlockEntities.SILVERED_BOTTLE.get()) {
            return null;
        }
        BlockEntityTicker<SilveredBottleBlockEntity> ticker = SilveredBottleBlockEntity::clientTick;
        return (BlockEntityTicker<T>) ticker;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GHOST, FACING);
    }

    /// An empty Silvered Bottle used on a ghost draws it in
    @SubscribeEvent
    static void bottleGhosts(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        if (!(event.getTarget() instanceof Ghost ghost) || !held.is(ModItems.SILVERED_BOTTLE.get()) || !ghost.isAlive()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!player.level().isClientSide()) {
            bottle(player, ghost, event.getHand());
        }
    }

    public static void bottle(Player player, Ghost ghost, net.minecraft.world.InteractionHand hand) {
        Souls.capture(player, ghost, hand, new ItemStack(ModItems.GHOST_IN_A_BOTTLE.get()));
    }
}
