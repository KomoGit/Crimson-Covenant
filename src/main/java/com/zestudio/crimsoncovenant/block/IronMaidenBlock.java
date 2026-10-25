package com.zestudio.crimsoncovenant.block;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModBlockEntities;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.item.BloodBottleItem;

import net.minecraft.ChatFormatting;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/// An Iron Maiden: a tall iron figure of a cloaked, hooded woman, her face framed in tight curls, standing over two
/// blocks tall, for keeping a villager, a Plague Doctor or a witch captive. Empty, her double doors stand open on the
/// spikes lining them and her back; shut on a captive, blood seeps from between them and her face weeps. Use one (as an
/// item) on one of them to shut them inside and set it down by your coffin: when you've slept the night (or the day)
/// through there, you wake healed, and as a vampire having drunk deep of their life force (see CoffinBlock). A Blood
/// Throne nearby siphons them too, for as long as you sit on it. They don't die of it outright, but there's only so
/// much blood in them: once it's all been drawn they're gone (a villager now and then rising again as a zombie
/// villager), and you need someone new. While anyone's inside, a thread of blood runs from the maiden to the coffin or
/// throne, and now and then you hear them: a moan, a plea, banging on the iron.
///
/// Use an empty bottle on a filled maiden to draw off a Bottle of Blood, from the blood left in the captive.
///
/// Sneak and use a maiden to open it and let its captive go; break it, and they go free too.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class IronMaidenBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    /// What each captive gives whoever sleeps it through in a coffin nearby, drawn from the blood left in them: health,
    /// and the blood itself for a vampire
    public static final int BLOOD_PER_CAPTIVE = 25;
    public static final float HEALTH_PER_CAPTIVE = 6.0F;
    private static final int BLOOD_RED = 0x8A0B0B;
    private static final VoxelShape LOWER = Shapes.or(Block.box(0.5, 0.0, 1.0, 15.5, 1.5, 14.5), Block.box(2.0, 1.5, 1.5, 14.0, 16.0, 14.0));
    private static final VoxelShape UPPER = Shapes.or(Block.box(2.0, 0.0, 1.5, 14.0, 12.5, 14.0), Block.box(3.5, 12.5, 3.5, 12.5, 16.0, 12.5));
    private static final java.util.Map<Direction, VoxelShape> LOWER_SHAPES = Shapes.rotateHorizontal(LOWER);
    private static final java.util.Map<Direction, VoxelShape> UPPER_SHAPES = Shapes.rotateHorizontal(UPPER);

    public IronMaidenBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(OCCUPIED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(state.getValue(FACING));
    }

    // ---- Two blocks tall ----

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (level.getBlockEntity(pos) instanceof IronMaidenBlockEntity maiden) {
            maiden.setCaptive(stack.get(ModDataComponents.CAPTIVE.get()));
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return super.canSurvive(state, level, pos);
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (directionToNeighbour.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (directionToNeighbour == Direction.UP)) {
            return neighbourState.is(this) && neighbourState.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    // Broken from the top in creative, the bottom goes too but drops nothing (in survival it drops the maiden)
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OCCUPIED);
    }

    /// Both halves show whether there's someone inside
    public static void setOccupied(Level level, BlockPos lower, boolean occupied) {
        for (BlockPos pos : new BlockPos[] {lower, lower.above()}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof IronMaidenBlock && state.getValue(OCCUPIED) != occupied) {
                level.setBlock(pos, state.setValue(OCCUPIED, occupied), 3);
            }
        }
    }

    public static BlockPos lowerHalf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    // ---- The captive ----

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new IronMaidenBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || type != ModBlockEntities.IRON_MAIDEN.get() || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        BlockEntityTicker<IronMaidenBlockEntity> ticker = IronMaidenBlockEntity::clientTick;
        return (BlockEntityTicker<T>) ticker;
    }

    /// An empty bottle, held to the seam between her doors, fills with the captive's blood
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(Items.GLASS_BOTTLE) || !(level.getBlockEntity(lowerHalf(state, pos)) instanceof IronMaidenBlockEntity maiden) || !maiden.hasCaptive()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel) {
            bottleBlood(serverLevel, maiden, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    /// Draws a Bottle of Blood's worth from the captive into the empty bottle in the player's hand (their last drops,
    /// if that's all there is, bleeding them dry). Returns whether there was anyone to draw it from.
    public static boolean bottleBlood(ServerLevel level, IronMaidenBlockEntity maiden, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.GLASS_BOTTLE) || maiden.drain(BloodBottleItem.BLOOD) <= 0) {
            return false;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(ModItems.BLOOD_BOTTLE.get())));
        BlockPos pos = maiden.getBlockPos();
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(new DustParticleOptions(BLOOD_RED, 0.8F), pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 8, 0.2, 0.3, 0.2, 0.0);
        return true;
    }

    /// Sneak and use it to let the captive out; otherwise it tells you how much life is left in them
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(lowerHalf(state, pos)) instanceof IronMaidenBlockEntity maiden) || !maiden.hasCaptive()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (player.isSecondaryUseActive()) {
                maiden.release();
            } else {
                player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.iron_maiden.captive", maiden.getCaptive().name(), maiden.getCaptive().blood())
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Blood weeps from the seam between her doors
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(OCCUPIED) && state.getValue(HALF) == DoubleBlockHalf.LOWER && random.nextInt(4) == 0) {
            Direction front = state.getValue(FACING);
            double x = pos.getX() + 0.5 + front.getStepX() * 0.42;
            double z = pos.getZ() + 0.5 + front.getStepZ() * 0.42;
            level.addParticle(new DustParticleOptions(BLOOD_RED, 0.6F), x, pos.getY() + 0.2 + random.nextDouble() * 0.6, z, 0.0, -0.02, 0.0);
        }
    }

    // ---- Taking captives, and feeding on them ----

    /// An empty Iron Maiden used on a villager, a Plague Doctor or a witch (see ModTags.Entities.IRON_MAIDEN_CAPTIVES)
    /// shuts them inside
    @SubscribeEvent
    static void kidnap(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        if (!(event.getTarget() instanceof Mob captive) || !captive.is(ModTags.Entities.IRON_MAIDEN_CAPTIVES) || !held.is(ModItems.IRON_MAIDEN.get())
                || held.has(ModDataComponents.CAPTIVE.get()) || !captive.isAlive()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!player.level().isClientSide()) {
            kidnap(player, captive, event.getHand());
        }
    }

    public static void kidnap(Player player, Mob captive, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack full = held.copyWithCount(1);
        captive.stopSleeping();
        // A villager's bed and workplace are free for someone else
        if (captive instanceof Villager villager) {
            for (MemoryModuleType<GlobalPos> poi : List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT)) {
                villager.releasePoi(poi);
            }
        }
        full.set(ModDataComponents.CAPTIVE.get(), IronMaidenBlockEntity.Captive.of(captive));
        captive.level().playSound(null, captive.getX(), captive.getY(), captive.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0F, 0.6F);
        captive.level().playSound(null, captive.getX(), captive.getY(), captive.getZ(), captive instanceof Witch ? SoundEvents.WITCH_HURT : SoundEvents.VILLAGER_HURT,
                SoundSource.NEUTRAL, 1.0F, 1.0F);
        captive.discard();
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, full));
    }

    /// The Iron Maidens with captives inside around a coffin
    public static java.util.List<IronMaidenBlockEntity> captivesAround(Level level, BlockPos coffin) {
        java.util.List<IronMaidenBlockEntity> maidens = new java.util.ArrayList<>();
        int range = IronMaidenBlockEntity.COFFIN_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(coffin.offset(-range, -2, -range), coffin.offset(range, 2, range))) {
            if (level.getBlockEntity(pos) instanceof IronMaidenBlockEntity maiden && maiden.hasCaptive()) {
                maidens.add(maiden);
            }
        }
        return maidens;
    }

    /// Whoever has slept it through in a coffin wakes restored by the captives around it: healed, and a vampire fed.
    /// Returns how many captives were drawn on (none, if the sleeper woke wanting for nothing).
    public static int feedOnCaptives(Player sleeper, BlockPos coffin) {
        boolean anyCaptives = !captivesAround(sleeper.level(), coffin).isEmpty();
        int drawn = restore(sleeper, coffin, BLOOD_PER_CAPTIVE, HEALTH_PER_CAPTIVE);
        if (drawn > 0) {
            String message = Vampirism.isVampire(sleeper) ? "message.crimsoncovenant.iron_maiden.fed" : "message.crimsoncovenant.iron_maiden.restored";
            sleeper.sendOverlayMessage(Component.translatable(message, drawn).withStyle(ChatFormatting.DARK_RED));
        } else if (anyCaptives) {
            sleeper.sendOverlayMessage(Component.translatable("message.crimsoncovenant.iron_maiden.spared").withStyle(ChatFormatting.GRAY));
        }
        return drawn;
    }

    /// How much blood `drinker` could do with: what a vampire's blood pool lacks, or what it'd take to heal them fully
    /// (`healthEach` health for every `bloodEach` blood), whichever is more
    public static int bloodWanted(Player drinker, int bloodEach, float healthEach) {
        float missingHealth = drinker.getMaxHealth() - drinker.getHealth();
        int forHealth = missingHealth <= 0.0F ? 0 : Mth.ceil(missingHealth * bloodEach / healthEach);
        int forBlood = Vampirism.isVampire(drinker) ? BloodPool.MAX_BLOOD - BloodPool.get(drinker) : 0;
        return Math.max(forHealth, forBlood);
    }

    /// Draws on the captives around `drinkingPlace` (a coffin or a Blood Throne): up to `bloodEach` from each, out of
    /// what's left in them, but never more than the drinker wants (see bloodWanted): a full drinker leaves the rest in
    /// them for later. The drinker heals in proportion (`healthEach` for a full draw), and a vampire drinks the blood.
    /// Returns how many captives were drawn on.
    public static int restore(Player drinker, BlockPos drinkingPlace, int bloodEach, float healthEach) {
        int wanted = bloodWanted(drinker, bloodEach, healthEach);
        int blood = 0;
        int drawn = 0;
        for (IronMaidenBlockEntity maiden : captivesAround(drinker.level(), drinkingPlace)) {
            if (wanted - blood <= 0) {
                break;
            }
            int taken = maiden.drain(Math.min(bloodEach, wanted - blood));
            if (taken > 0) {
                blood += taken;
                drawn++;
            }
        }
        if (blood > 0) {
            drinker.heal(healthEach * blood / bloodEach);
            if (Vampirism.isVampire(drinker)) {
                BloodPool.add(drinker, blood);
            }
        }
        return drawn;
    }

    @SubscribeEvent
    static void wakeFed(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel) || event.wakeImmediately() || !player.isSleepingLongEnough() || !CoffinBlock.isInCoffin(player)) {
            return;
        }
        player.getSleepingPos().ifPresent(coffin -> feedOnCaptives(player, coffin));
    }
}
