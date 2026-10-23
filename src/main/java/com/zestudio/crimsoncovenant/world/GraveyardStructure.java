package com.zestudio.crimsoncovenant.world;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.zestudio.crimsoncovenant.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/// A walled graveyard filling exactly one chunk, on fairly level ground in grassy and wooded biomes. It paints its
/// chunk with the Graveyard biome, and its spawn overrides (in the structure's JSON) fill it with undead at night.
/// See GraveyardPiece for the layout.
public class GraveyardStructure extends Structure {
    public static final MapCodec<GraveyardStructure> CODEC = simpleCodec(GraveyardStructure::new);
    /// Ground that dips or climbs more than this across the chunk is too rough for a graveyard
    private static final int MAX_SLOPE = 5;

    public GraveyardStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 15; dx += 15) {
            for (int dz = 0; dz <= 15; dz += 15) {
                int y = context.chunkGenerator().getFirstOccupiedHeight(chunk.getMinBlockX() + dx, chunk.getMinBlockZ() + dz,
                        Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
            }
        }
        int center = context.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        int floor = context.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
                Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        // Too steep, or under water
        if (highest - lowest > MAX_SLOPE || center != floor) {
            return Optional.empty();
        }
        // The ground layer sits where the surface is at the chunk's middle
        BlockPos origin = new BlockPos(chunk.getMinBlockX(), center - 1, chunk.getMinBlockZ());
        return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(new GraveyardPiece(context.random(), origin))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.GRAVEYARD.get();
    }
}
