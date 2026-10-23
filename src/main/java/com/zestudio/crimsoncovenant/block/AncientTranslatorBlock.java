package com.zestudio.crimsoncovenant.block;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.skill.AncientTomeItem;
import com.zestudio.crimsoncovenant.skill.BloodSkill;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/// Turns an Ancient Tome into the blood skill written in it. A skull floats over the table between four melted
/// candles; use a tome on it and the skull takes the book in its jaws, and a few seconds later the skill pops out.
///
/// The tome being translated is kept in the block state (no block entity needed), which also lets the block's loot
/// table give the tome back if the translator is broken mid-translation.
public class AncientTranslatorBlock extends Block {
    /// 0 when idle, otherwise the BloodSkill being translated (its ordinal + 1)
    public static final IntegerProperty TOME = IntegerProperty.create("tome", 0, BloodSkill.values().length);
    public static final int TRANSLATION_TIME = 100;
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);
    /// The tops of the four candle wicks, in sixteenths of a block (they match the block model)
    private static final double[][] CANDLE_WICKS = {{2, 17, 2}, {14, 15.5, 2}, {2, 16, 14}, {14, 15, 14}};

    public AncientTranslatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TOME, 0));
    }

    public static boolean isTranslating(BlockState state) {
        return state.getValue(TOME) > 0;
    }

    public static @Nullable BloodSkill translating(BlockState state) {
        int tome = state.getValue(TOME);
        return tome == 0 ? null : BloodSkill.values()[tome - 1];
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof AncientTomeItem tome)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (isTranslating(state)) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.translator.busy").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            startTranslating(serverLevel, pos, state, tome.skill());
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        String key = isTranslating(state) ? "message.crimsoncovenant.translator.busy" : "message.crimsoncovenant.translator.hint";
        player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    public static void startTranslating(ServerLevel level, BlockPos pos, BlockState state, BloodSkill skill) {
        level.setBlockAndUpdate(pos, state.setValue(TOME, skill.ordinal() + 1));
        level.scheduleTick(pos, state.getBlock(), TRANSLATION_TIME);
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    /// The translation is done: the skill rises out of the book
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BloodSkill skill = translating(state);
        if (skill == null) {
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(TOME, 0));
        ItemEntity result = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, new ItemStack(skill.skillItem()), 0.0, 0.2, 0.0);
        result.setDefaultPickUpDelay();
        level.addFreshEntity(result);
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.5F), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.0);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    // The melted candles burn, and glyphs pour into the book while it's being read
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (double[] wick : CANDLE_WICKS) {
            if (random.nextInt(3) == 0) {
                double wx = pos.getX() + wick[0] / 16.0;
                double wy = pos.getY() + wick[1] / 16.0 + 0.03;
                double wz = pos.getZ() + wick[2] / 16.0;
                level.addParticle(ParticleTypes.SMALL_FLAME, wx, wy, wz, 0.0, 0.0, 0.0);
                if (random.nextInt(8) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, wx, wy + 0.05, wz, 0.0, 0.01, 0.0);
                }
            }
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.1;
        double z = pos.getZ() + 0.5;
        if (isTranslating(state)) {
            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.ENCHANT, x, y, z, (random.nextDouble() - 0.5) * 3.0, random.nextDouble() * 1.5, (random.nextDouble() - 0.5) * 3.0);
            }
            if (random.nextInt(3) == 0) {
                level.addParticle(new DustParticleOptions(Vampire.BLOOD_COLOR, 0.8F), x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.0, 0.0);
            }
        } else if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, (random.nextDouble() - 0.5) * 2.0, random.nextDouble(), (random.nextDouble() - 0.5) * 2.0);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOME);
    }
}
