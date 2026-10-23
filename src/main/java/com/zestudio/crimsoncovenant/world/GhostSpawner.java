package com.zestudio.crimsoncovenant.world;

import java.util.ArrayList;
import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/// Ghosts only haunt old mineshafts, abandoned (zombie) villages and graveyards. Vanilla's biome-based spawning can't
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
    private static final int SPAWN_TRIES = 12;

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
        // A random spot near the player almost never lands in a 3-wide mineshaft tunnel, so the ghost is placed
        // inside one of the haunted structure's own pieces (a corridor, a crossing, a house) instead
        List<BoundingBox> rooms = hauntedPiecesNear(level, center);
        if (rooms.isEmpty()) {
            return;
        }
        for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
            BoundingBox room = rooms.get(random.nextInt(rooms.size()));
            BlockPos pos = new BlockPos(random.nextIntBetweenInclusive(room.minX(), room.maxX()),
                    random.nextIntBetweenInclusive(room.minY(), room.maxY()), random.nextIntBetweenInclusive(room.minZ(), room.maxZ()));
            double distanceSqr = pos.distSqr(center);
            if (distanceSqr >= MIN_DISTANCE * MIN_DISTANCE && distanceSqr <= MAX_DISTANCE * MAX_DISTANCE
                    && level.isLoaded(pos) && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                spawnGhost(level, pos, random);
                return;
            }
        }
    }

    private static void spawnGhost(ServerLevel level, BlockPos pos, RandomSource random) {
        Ghost ghost = ModEntities.GHOST.get().create(level, EntitySpawnReason.NATURAL);
        if (ghost != null) {
            ghost.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            ghost.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            ghost.setHaunt(pos);
            level.addFreshEntity(ghost);
        }
    }

    /// The pieces of every mineshaft, abandoned village and graveyard within reach of the player
    public static List<BoundingBox> hauntedPiecesNear(ServerLevel level, BlockPos center) {
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        BoundingBox reach = new BoundingBox(center).inflatedBy(MAX_DISTANCE);
        List<BoundingBox> rooms = new ArrayList<>();
        List<StructureStart> starts = level.structureManager().startsForStructure(SectionPos.blockToSectionCoord(center.getX()),
                SectionPos.blockToSectionCoord(center.getZ()), structure -> isHauntedKind(structures.wrapAsHolder(structure)));
        for (StructureStart start : starts) {
            if (structures.wrapAsHolder(start.getStructure()).is(StructureTags.VILLAGE) && !isAbandonedVillage(start)) {
                continue;
            }
            for (StructurePiece piece : start.getPieces()) {
                if (piece.getBoundingBox().intersects(reach)) {
                    rooms.add(piece.getBoundingBox());
                }
            }
        }
        return rooms;
    }

    public static boolean isHauntedKind(Holder<Structure> structure) {
        return structure.is(StructureTags.MINESHAFT) || structure.is(StructureTags.VILLAGE) || structure.is(ModStructures.GRAVEYARD_STRUCTURE);
    }

    // Abandoned villages use the same structures as normal ones, but are built from the "zombie" template pools
    private static boolean isAbandonedVillage(StructureStart village) {
        return village.getPieces().stream().anyMatch(piece -> piece instanceof PoolElementStructurePiece poolPiece
                && poolPiece.getElement() instanceof SinglePoolElement element
                && element.toString().contains("/zombie/"));
    }
}
