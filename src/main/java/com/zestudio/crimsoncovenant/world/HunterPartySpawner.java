package com.zestudio.crimsoncovenant.world;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Hunter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/// Spawns hunting parties near players in the overworld at night, similar to vanilla pillager patrols.
///
/// While it's night, every 1-2 minutes there's a 50% chance a party appears 24-48 blocks from a random
/// player, unless hunters are already close to that player. So roughly one party shows up early each night,
/// and another only once the first has moved on, died or despawned.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class HunterPartySpawner {
    // Time between spawn attempts, in ticks (20 ticks = 1 second). Only counts down at night.
    private static final int MIN_DELAY = 1200;
    private static final int RANDOM_EXTRA_DELAY = 1200;
    // 1 in N attempts actually spawns a party
    private static final int SPAWN_CHANCE = 2;
    // No new party if hunters are already this close to the player
    private static final double NEARBY_PARTY_RANGE = 96.0;
    private static final double BLOOD_MOON_PARTY_RANGE = 40.0;
    // Candidate positions to try before giving up on an attempt
    private static final int PLACEMENT_TRIES = 10;
    // Overworld clock time when night starts and ends
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    private static int nextAttempt = MIN_DELAY;

    private HunterPartySpawner() {}

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (!level.getGameRules().get(GameRules.SPAWN_MOBS) || !isNight(level) || --nextAttempt > 0) {
            return;
        }

        RandomSource random = level.getRandom();
        // Under a blood moon the hunters ride out in force: twice as often, every time, and closer together
        boolean bloodMoon = BloodMoon.isActive(level);
        nextAttempt = (MIN_DELAY + random.nextInt(RANDOM_EXTRA_DELAY)) / (bloodMoon ? 2 : 1);
        if ((!bloodMoon && random.nextInt(SPAWN_CHANCE) != 0) || level.players().isEmpty()) {
            return;
        }

        ServerPlayer player = level.players().get(random.nextInt(level.players().size()));
        if (!player.isSpectator() && !hasHuntersNearby(level, player.blockPosition(), bloodMoon ? BLOOD_MOON_PARTY_RANGE : NEARBY_PARTY_RANGE)) {
            BlockPos pos = spawnPartyNear(level, player.blockPosition(), random);
            if (pos != null) {
                CrimsonCovenant.LOGGER.debug("Spawned a hunter party at {} near {}", pos, player.getName().getString());
            }
        }
    }

    /// Tries a few spots 24-48 blocks from the given position and spawns a party at the first good one.
    /// Returns where the party spawned, or null if no suitable spot was found.
    public static @Nullable BlockPos spawnPartyNear(ServerLevel level, BlockPos center, RandomSource random) {
        for (int i = 0; i < PLACEMENT_TRIES; i++) {
            int dx = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
            int dz = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
            BlockPos column = center.offset(dx, 0, dz);
            if (!level.hasChunksAt(column.getX() - 10, column.getZ() - 10, column.getX() + 10, column.getZ() + 10)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            // Skip oceans, rivers and lakes
            if (!level.getFluidState(surface.below()).isEmpty()) {
                continue;
            }
            if (Hunter.spawnParty(level, surface, random, EntitySpawnReason.PATROL) > 0) {
                return surface;
            }
        }
        return null;
    }

    public static boolean hasHuntersNearby(ServerLevel level, BlockPos pos, double range) {
        return !level.getEntitiesOfClass(Hunter.class, new AABB(pos).inflate(range)).isEmpty();
    }

    public static boolean isNight(ServerLevel level) {
        long timeOfDay = level.getOverworldClockTime() % 24000L;
        return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
    }
}
