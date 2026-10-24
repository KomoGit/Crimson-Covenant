package com.zestudio.crimsoncovenant.block;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.curse.CurseforgeMenu;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/// A Curseforge: an anvil a ghost has been poured into, its iron split with cracks that glow a cold blue. Use a Ghost in
/// a Bottle on any anvil to make one. Work at it to lay curses on weapons (see CurseforgeMenu); sneak, and it's an anvil
/// still, one that never wears out. It doesn't fall, either.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class CurseforgeBlock extends HorizontalDirectionalBlock {
    private static final Component TITLE = Component.translatable("container.crimsoncovenant.curseforge");
    private static final Component ANVIL_TITLE = Component.translatable("container.repair");
    // An anvil's shape: its long top runs across the way it faces
    private static final VoxelShape BASE = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);
    private static final VoxelShape X_SHAPE = Shapes.or(BASE, Block.box(3.0, 4.0, 4.0, 13.0, 5.0, 12.0), Block.box(4.0, 5.0, 6.0, 12.0, 10.0, 10.0),
            Block.box(0.0, 10.0, 3.0, 16.0, 16.0, 13.0));
    private static final VoxelShape Z_SHAPE = Shapes.or(BASE, Block.box(4.0, 4.0, 3.0, 12.0, 5.0, 13.0), Block.box(6.0, 5.0, 4.0, 10.0, 10.0, 12.0),
            Block.box(3.0, 10.0, 0.0, 13.0, 16.0, 16.0));

    public CurseforgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /// Use it to lay curses; sneak and use it to work it as a plain anvil (which, unlike an anvil, it never wears out)
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(player.isSecondaryUseActive() ? anvil(level, pos) : state.getMenuProvider(level, pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new CurseforgeMenu(id, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
    }

    private static MenuProvider anvil(Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new AsAnvil(id, inventory, ContainerLevelAccess.create(level, pos)), ANVIL_TITLE);
    }

    /// The Curseforge worked as an anvil: repairing, renaming, combining enchantments, all as an anvil does. An anvil only
    /// wears out (chipped, damaged, then broken) if it's in the anvil tag, which the Curseforge isn't, so it never does.
    public static class AsAnvil extends AnvilMenu {
        public AsAnvil(int containerId, Inventory inventory, ContainerLevelAccess access) {
            super(containerId, inventory, access);
        }

        @Override
        protected boolean isValidBlock(BlockState state) {
            return state.is(ModBlocks.CURSEFORGE.get());
        }
    }

    // The cracks breathe out a cold light
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(new DustParticleOptions(Curse.COLOUR, 0.5F), pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.3 + random.nextDouble() * 0.7,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.01, 0.0);
        }
        if (random.nextInt(20) == 0) {
            level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0.0, 0.02, 0.0);
        }
    }

    /// Pour a ghost into an anvil (any anvil, chipped or not) and it becomes a Curseforge
    @SubscribeEvent
    static void pourAGhostIntoAnAnvil(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        BlockState anvil = event.getLevel().getBlockState(event.getPos());
        if (!held.is(ModItems.GHOST_IN_A_BOTTLE.get()) || !anvil.is(BlockTags.ANVIL)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level) {
            pourGhost(level, event.getPos(), player, event.getHand());
        }
    }

    /// The ghost in the player's hand goes into the anvil at `pos`, making it a Curseforge; the empty bottle comes back
    public static boolean pourGhost(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        BlockState anvil = level.getBlockState(pos);
        if (!held.is(ModItems.GHOST_IN_A_BOTTLE.get()) || !anvil.is(BlockTags.ANVIL)) {
            return false;
        }
        forge(level, pos, anvil);
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(ModItems.SILVERED_BOTTLE.get())));
        return true;
    }

    public static void forge(ServerLevel level, BlockPos pos, BlockState anvil) {
        Direction facing = anvil.hasProperty(AnvilBlock.FACING) ? anvil.getValue(AnvilBlock.FACING) : Direction.NORTH;
        level.setBlockAndUpdate(pos, ModBlocks.CURSEFORGE.get().defaultBlockState().setValue(FACING, facing));
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.2F, 0.6F);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 0.5F);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.03);
        level.sendParticles(new DustParticleOptions(Curse.COLOUR, 1.2F), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 30, 0.4, 0.3, 0.4, 0.0);
    }
}
