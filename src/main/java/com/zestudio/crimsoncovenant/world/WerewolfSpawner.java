package com.zestudio.crimsoncovenant.world;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/// Werewolves only roam on full-moon nights (and blood moons). While the moon is full, every 30-60 seconds
/// there's a 50% chance one appears 24-48 blocks from a player on the surface, never more than 3 nearby.
/// During a blood moon they come every time, in pairs. They vanish again when the moon sets (see Werewolf).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class WerewolfSpawner {
    private static final int MIN_DELAY = 600;
    private static final int RANDOM_EXTRA_DELAY = 600;
    private static final int SPAWN_CHANCE = 2;
    private static final int MAX_NEARBY = 3;
    private static final double NEARBY_RANGE = 64.0;
    private static final int PLACEMENT_TRIES = 10;

    private static int nextAttempt = MIN_DELAY;

    private WerewolfSpawner() {}

    /// Full moon (or blood moon) and night time
    public static boolean isWerewolfNight(ServerLevel level) {
        if (BloodMoon.isActive(level)) {
            return true;
        }
        ServerLevel overworld = level.getServer().overworld();
        return HunterPartySpawner.isNight(overworld) && BloodMoon.moonPhase(overworld) == MoonPhase.FULL_MOON;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MONSTERS) || !isWerewolfNight(level) || --nextAttempt > 0) {
            return;
        }
        RandomSource random = level.getRandom();
        nextAttempt = MIN_DELAY + random.nextInt(RANDOM_EXTRA_DELAY);
        boolean bloodMoon = BloodMoon.isActive(level);
        if ((!bloodMoon && random.nextInt(SPAWN_CHANCE) != 0) || level.players().isEmpty()) {
            return;
        }
        ServerPlayer player = level.players().get(random.nextInt(level.players().size()));
        if (player.isSpectator() || level.getEntitiesOfClass(Werewolf.class, new AABB(player.blockPosition()).inflate(NEARBY_RANGE)).size() >= MAX_NEARBY) {
            return;
        }
        int count = bloodMoon ? 2 : 1;
        for (int i = 0; i < count; i++) {
            spawnNear(level, player.blockPosition(), random);
        }
    }

    public static boolean spawnNear(ServerLevel level, BlockPos center, RandomSource random) {
        for (int i = 0; i < PLACEMENT_TRIES; i++) {
            int dx = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
            int dz = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
            BlockPos column = center.offset(dx, 0, dz);
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (!level.getFluidState(pos.below()).isEmpty() || !SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, ModEntities.WEREWOLF.get())) {
                continue;
            }
            Werewolf werewolf = ModEntities.WEREWOLF.get().create(level, EntitySpawnReason.NATURAL);
            if (werewolf != null) {
                werewolf.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
                werewolf.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
                level.addFreshEntity(werewolf);
                return true;
            }
        }
        return false;
    }
}
