package com.zestudio.crimsoncovenant.world;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/// Ghosts only haunt old mineshafts and abandoned (zombie) villages. Vanilla's biome-based spawning can't
/// target structures like that, so this places them directly: every 10 seconds, each player has a chance of a
/// ghost appearing 8-24 blocks away, but only inside one of those structures, and never more than 3 nearby.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class GhostSpawner {
    private static final int ATTEMPT_INTERVAL = 200;
    private static final int SPAWN_CHANCE = 3;
    private static final int MIN_DISTANCE = 8;
    private static final int MAX_DISTANCE = 24;
    private static final int MAX_NEARBY = 3;
    private static final double NEARBY_RANGE = 48.0;

    private static int nextAttempt = ATTEMPT_INTERVAL;

    private GhostSpawner() {}

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MONSTERS) || --nextAttempt > 0) {
            return;
        }
        nextAttempt = ATTEMPT_INTERVAL;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && random.nextInt(SPAWN_CHANCE) == 0) {
                trySpawnNear(level, player.blockPosition(), random);
            }
        }
    }

    private static void trySpawnNear(ServerLevel level, BlockPos center, RandomSource random) {
        if (level.getEntitiesOfClass(Ghost.class, new AABB(center).inflate(NEARBY_RANGE)).size() >= MAX_NEARBY) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2.0;
        int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);
        BlockPos pos = center.offset((int) (Math.cos(angle) * distance), random.nextInt(9) - 4, (int) (Math.sin(angle) * distance));
        if (!level.isLoaded(pos) || !level.isEmptyBlock(pos) || !isHaunted(level, pos)) {
            return;
        }
        Ghost ghost = ModEntities.GHOST.get().create(level, EntitySpawnReason.NATURAL);
        if (ghost != null) {
            ghost.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            ghost.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            ghost.setHaunt(pos);
            level.addFreshEntity(ghost);
        }
    }

    /// Inside a mineshaft, or inside an abandoned (zombie) village
    public static boolean isHaunted(ServerLevel level, BlockPos pos) {
        if (level.structureManager().getStructureWithPieceAt(pos, StructureTags.MINESHAFT).isValid()) {
            return true;
        }
        StructureStart village = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        return village.isValid() && isAbandonedVillage(village);
    }

    // Abandoned villages use the same structures as normal ones, but are built from the "zombie" template pools
    private static boolean isAbandonedVillage(StructureStart village) {
        return village.getPieces().stream().anyMatch(piece -> piece instanceof PoolElementStructurePiece poolPiece
                && poolPiece.getElement() instanceof SinglePoolElement element
                && element.toString().contains("/zombie/"));
    }
}
