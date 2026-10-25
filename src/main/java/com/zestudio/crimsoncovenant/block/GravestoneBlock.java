package com.zestudio.crimsoncovenant.block;

import java.util.Locale;

import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/// A gravestone. Those in a graveyard may have a ghost bound to them (see GraveyardPiece): by day it rests inside the
/// stone, and at nightfall it rises to haunt the graveyard, coming back to its stone at dawn (see Ghost). Destroy the
/// ghost and the stone lies empty for good, so a graveyard holds only so many ghosts. Breaking a stone with a ghost
/// resting in it lets the ghost out, furious. Gravestones set down by players never have one.
public class GravestoneBlock extends DecorBlock {
    public static final EnumProperty<Spirit> SPIRIT = EnumProperty.create("spirit", Spirit.class);
    /// How far from its stone a risen ghost may be and still count as about (beyond that, it's thought lost)
    private static final double GHOST_RANGE = 48.0;

    /// What's bound to the stone
    public enum Spirit implements StringRepresentable {
        /// Nothing: an ordinary stone
        NONE,
        /// A ghost, resting inside
        RESTING,
        /// A ghost, out haunting the graveyard
        ROAMING;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public GravestoneBlock(BlockBehaviour.Properties properties, VoxelShape northShape) {
        super(properties, northShape);
        this.registerDefaultState(this.defaultBlockState().setValue(SPIRIT, Spirit.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SPIRIT);
    }

    public static boolean isGravestone(BlockState state) {
        return state.getBlock() instanceof GravestoneBlock;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(SPIRIT) != Spirit.NONE;
    }

    // At night a resting ghost rises; by day, a ghost that's out but nowhere about (gone in peaceful, say) is let rest
    // again
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        tend(level, pos, state, level.isDarkOutside());
    }

    /// What a stone's ghost does, `night` or day
    public static void tend(ServerLevel level, BlockPos pos, BlockState state, boolean night) {
        Spirit spirit = state.getValue(SPIRIT);
        if (spirit == Spirit.RESTING && night) {
            rise(level, pos, state);
        } else if (spirit == Spirit.ROAMING && !night && ghostOf(level, pos) == null) {
            level.setBlock(pos, state.setValue(SPIRIT, Spirit.RESTING), Block.UPDATE_CLIENTS);
        }
    }

    /// The ghost resting in the stone at `pos` rises out of it, bound to it still. Returns the ghost, if it rose.
    public static Ghost rise(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MONSTERS)) {
            return null;
        }
        Ghost ghost = ModEntities.GHOST.get().create(level, EntitySpawnReason.EVENT);
        if (ghost == null) {
            return null;
        }
        ghost.snapTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        ghost.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        ghost.bindTo(pos);
        level.addFreshEntity(ghost);
        level.setBlock(pos, state.setValue(SPIRIT, Spirit.ROAMING), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.02);
        return ghost;
    }

    /// The ghost bound to the stone at `pos`, if it's about (and not on its way out)
    public static Ghost ghostOf(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(Ghost.class, new AABB(pos).inflate(GHOST_RANGE), ghost -> ghost.isAlive() && pos.equals(ghost.getGrave())).stream().findFirst().orElse(null);
    }

    /// Sets what's bound to the gravestone at `pos`, if there's still a gravestone there
    public static void setSpirit(Level level, BlockPos pos, Spirit spirit) {
        BlockState state = level.getBlockState(pos);
        if (isGravestone(state) && state.getValue(SPIRIT) != spirit) {
            level.setBlock(pos, state.setValue(SPIRIT, spirit), Block.UPDATE_CLIENTS);
        }
    }

    // Break a stone with a ghost resting in it, and the ghost comes out after whoever woke it, bound to nothing now
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && state.getValue(SPIRIT) == Spirit.RESTING && !player.isCreative()) {
            Ghost ghost = rise(serverLevel, pos, state);
            if (ghost != null) {
                ghost.unbind();
                ghost.setTarget(player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
