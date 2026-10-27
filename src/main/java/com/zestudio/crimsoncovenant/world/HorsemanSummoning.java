package com.zestudio.crimsoncovenant.world;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.HalloweenSpirit;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;
import com.zestudio.crimsoncovenant.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/// Calling up the Headless Horseman: a jack o'lantern set down by night near a Crooked Tree (one of its Crooked Logs)
/// is taken for his head. It vanishes in a burst of flame, and he comes riding up the road towards whoever set it down.
/// Only one Horseman at a time: none is called while he (or his spirit) is still abroad near the tree.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class HorsemanSummoning {
    /// How close to a Crooked Log the jack o'lantern has to be
    public static final int TREE_RANGE = 10;
    /// How far off down the road he appears
    private static final int MIN_DISTANCE = 18;
    private static final int MAX_DISTANCE = 28;
    private static final double ALREADY_RIDING_RANGE = 96.0;

    private HorsemanSummoning() {}

    @SubscribeEvent
    static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(Blocks.JACK_O_LANTERN) && event.getLevel() instanceof ServerLevel level) {
            LivingEntity placer = event.getEntity() instanceof LivingEntity living ? living : null;
            summon(level, event.getPos(), level.isDarkOutside(), placer);
        }
    }

    /// The foot of the Crooked Tree within TREE_RANGE of `pos` (its lowest log nearest `pos`), if there's one
    public static @Nullable BlockPos findCrookedTree(ServerLevel level, BlockPos pos) {
        BlockPos best = null;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-TREE_RANGE, -TREE_RANGE, -TREE_RANGE), pos.offset(TREE_RANGE, TREE_RANGE, TREE_RANGE))) {
            if (level.getBlockState(near).is(ModBlocks.CROOKED_LOG.get())
                    && (best == null || near.getY() < best.getY() || (near.getY() == best.getY() && near.distSqr(pos) < best.distSqr(pos)))) {
                best = near.immutable();
            }
        }
        return best;
    }

    /// The jack o'lantern at `lantern`, set down by `placer`: if it's `night` and by a Crooked Tree, and no Horseman
    /// rides near already, it's taken and he's called up. Returns him, if he was.
    public static @Nullable HeadlessHorseman summon(ServerLevel level, BlockPos lantern, boolean night, @Nullable LivingEntity placer) {
        BlockPos tree = night ? findCrookedTree(level, lantern) : null;
        if (tree == null
                || !level.getEntitiesOfClass(LivingEntity.class, new AABB(lantern).inflate(ALREADY_RIDING_RANGE),
                        rider -> (rider instanceof HeadlessHorseman horseman && horseman.hasBossBar()) || rider instanceof HalloweenSpirit).isEmpty()) {
            return null;
        }
        level.setBlock(lantern, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Vec3 centre = Vec3.atCenterOf(lantern);
        level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y, centre.z, 30, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.SOUL, centre.x, centre.y, centre.z, 15, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, lantern, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.6F);
        return HeadlessHorseman.summon(level, roadEnd(level, lantern), placer, tree, null);
    }

    /// Where he rides in from: the furthest bit of road (dirt path) MIN_DISTANCE to MAX_DISTANCE blocks off, or open
    /// ground that far off if there's no road
    static Vec3 roadEnd(ServerLevel level, BlockPos from) {
        Vec3 best = null;
        double bestDistance = 0.0;
        for (int dx = -MAX_DISTANCE; dx <= MAX_DISTANCE; dx += 2) {
            for (int dz = -MAX_DISTANCE; dz <= MAX_DISTANCE; dz += 2) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < MIN_DISTANCE || distance > MAX_DISTANCE || distance <= bestDistance) {
                    continue;
                }
                int x = from.getX() + dx;
                int z = from.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (level.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.DIRT_PATH)) {
                    best = new Vec3(x + 0.5, y, z + 0.5);
                    bestDistance = distance;
                }
            }
        }
        if (best != null) {
            return best;
        }
        float angle = level.getRandom().nextFloat() * Mth.TWO_PI;
        int x = Mth.floor(from.getX() + Mth.cos(angle) * MIN_DISTANCE);
        int z = Mth.floor(from.getZ() + Mth.sin(angle) * MIN_DISTANCE);
        return new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
    }
}
