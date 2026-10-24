package com.zestudio.crimsoncovenant.world;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;

/// Rats find their way into villages. Every twenty seconds or so at night, the spawner looks around each player for
/// a spot in a village, and a rat or two turns up there, unless the village already has its share. A village a player
/// spends the night in soon has its rats; from there they dig in and breed (see Rat).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class RatSpawner implements CustomSpawner {
    private static final int INTERVAL = 400;
    /// Spots tried around each player, each time, until one is in a village
    private static final int ATTEMPTS = 6;
    private static final int MIN_DISTANCE = 16;
    private static final int MAX_DISTANCE = 48;
    /// A village with this many rats about doesn't draw any more
    private static final int VILLAGE_SHARE = 6;

    private int nextTick = INTERVAL;

    @SubscribeEvent
    static void addToOverworld(ModifyCustomSpawnersEvent event) {
        if (event.getLevel().dimension() == Level.OVERWORLD) {
            event.addCustomSpawner(new RatSpawner());
        }
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (!spawnEnemies || --this.nextTick > 0) {
            return;
        }
        this.nextTick = INTERVAL;
        if (!level.isDarkOutside() || level.players().isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                trySpawnAround(level, player.blockPosition(), random);
            }
        }
    }

    /// Tries a few spots around `centre` until rats turn up at one. Returns how many came.
    public static int trySpawnAround(ServerLevel level, BlockPos centre, RandomSource random) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);
            double angle = random.nextDouble() * Math.PI * 2.0;
            BlockPos column = centre.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
            int spawned = trySpawnIn(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column), random);
            if (spawned > 0) {
                return spawned;
            }
        }
        return 0;
    }

    /// Spawns a rat or two at `pos` if it's in a village that hasn't got its share already. Returns how many came.
    public static int trySpawnIn(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isVillage(pos) || !level.isLoaded(pos) || Rat.countRatsNear(level, Vec3.atCenterOf(pos), Rat.VILLAGE_RANGE) >= VILLAGE_SHARE
                || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
            return 0;
        }
        int count = 1 + random.nextInt(2);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Rat rat = ModEntities.RAT.get().create(level, EntitySpawnReason.NATURAL);
            if (rat == null) {
                continue;
            }
            rat.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            rat.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            level.addFreshEntityWithPassengers(rat);
            spawned++;
        }
        return spawned;
    }
}
