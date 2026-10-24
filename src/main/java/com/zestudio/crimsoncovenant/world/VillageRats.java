package com.zestudio.crimsoncovenant.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/// New villages come with rats already living in them: a few burrows, dug into the ground or gnawed into the walls
/// about the village, each with a family of rats inside, safe from the village cats until night falls and they come out
/// to forage (see Rat). A village is noted when the chunk it starts in first generates, and settled once the land round
/// its middle has generated too, since its houses go up a chunk at a time.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class VillageRats {
    private static final int MIN_BURROWS = 2;
    private static final int MAX_BURROWS = 3;
    private static final int MIN_FAMILY = 2;
    private static final int MAX_FAMILY = 3;
    /// How far from a village's middle its burrows are dug
    private static final int SPREAD = 24;
    private static final int SEARCH = 4;
    // How far down from the top of a column to look for the earth under it
    private static final int MAX_DESCENT = 16;
    private static final int CHECK_INTERVAL = 40;
    // Villages waiting for the land round them to generate, by level
    private static final Map<ServerLevel, List<BlockPos>> WAITING = new WeakHashMap<>();

    private VillageRats() {}

    @SubscribeEvent
    static void villageFounded(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> start : event.getChunk().getAllStarts().entrySet()) {
            if (start.getValue().isValid() && structures.wrapAsHolder(start.getKey()).is(StructureTags.VILLAGE)) {
                synchronized (WAITING) {
                    WAITING.computeIfAbsent(level, key -> new ArrayList<>()).add(start.getValue().getBoundingBox().getCenter());
                }
            }
        }
    }

    @SubscribeEvent
    static void settleWaitingVillages(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        List<BlockPos> ready = new ArrayList<>();
        synchronized (WAITING) {
            List<BlockPos> waiting = WAITING.get(level);
            if (waiting == null) {
                return;
            }
            waiting.removeIf(centre -> {
                boolean built = level.hasChunksAt(centre.getX() - SPREAD - SEARCH, centre.getZ() - SPREAD - SEARCH, centre.getX() + SPREAD + SEARCH,
                        centre.getZ() + SPREAD + SEARCH);
                if (built) {
                    ready.add(centre);
                }
                return built;
            });
        }
        for (BlockPos centre : ready) {
            settle(level, centre, SPREAD, level.getRandom());
        }
    }

    /// Digs a few burrows within `spread` of `centre` and moves a family of rats into each. Returns how many burrows
    /// there are now.
    public static int settle(ServerLevel level, BlockPos centre, int spread, RandomSource random) {
        int wanted = MIN_BURROWS + random.nextInt(MAX_BURROWS - MIN_BURROWS + 1);
        int dug = 0;
        for (int attempt = 0; attempt < wanted * 4 && dug < wanted; attempt++) {
            BlockPos column = centre.offset(random.nextInt(spread * 2 + 1) - spread, 0, random.nextInt(spread * 2 + 1) - spread);
            Optional<Rat.BurrowSpot> spot = Rat.findBurrowSpot(level, ground(level, column), SEARCH);
            if (spot.isPresent() && Rat.settleIn(level, spot.get(), MIN_FAMILY + random.nextInt(MAX_FAMILY - MIN_FAMILY + 1)) > 0) {
                dug++;
            }
        }
        return dug;
    }

    // Down past any roof to the earth, where rats dig (and the foot of the walls they gnaw into)
    private static BlockPos ground(ServerLevel level, BlockPos column) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        for (int depth = 0; depth <= MAX_DESCENT; depth++) {
            BlockPos pos = top.below(depth);
            if (level.getBlockState(pos.below()).is(ModTags.Blocks.RATS_BURROW_IN) && level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return top;
    }
}
