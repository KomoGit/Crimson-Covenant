package com.zestudio.crimsoncovenant.world;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.zestudio.crimsoncovenant.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/// A Crooked Tree and its fenced yard, in the woods, on ground level enough to lay the yard over and not under water.
/// See CrookedTreePiece for the layout.
public class CrookedTreeStructure extends Structure {
    public static final MapCodec<CrookedTreeStructure> CODEC = simpleCodec(CrookedTreeStructure::new);
    /// Ground that dips or climbs more than this under the yard is too rough
    private static final int MAX_SLOPE = 6;

    public CrookedTreeStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int middleX = chunk.getMiddleBlockX();
        int middleZ = chunk.getMiddleBlockZ();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int step = CrookedTreePiece.RADIUS / 2;
        for (int dx = -CrookedTreePiece.RADIUS; dx <= CrookedTreePiece.RADIUS; dx += step) {
            for (int dz = -CrookedTreePiece.RADIUS; dz <= CrookedTreePiece.RADIUS; dz += step) {
                int x = middleX + dx;
                int z = middleZ + dz;
                int floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                // Under water
                if (surface != floor) {
                    return Optional.empty();
                }
                lowest = Math.min(lowest, floor);
                highest = Math.max(highest, floor);
            }
        }
        if (highest - lowest > MAX_SLOPE) {
            return Optional.empty();
        }
        int ground = context.chunkGenerator().getFirstOccupiedHeight(middleX, middleZ, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState()) - 1;
        BlockPos origin = new BlockPos(middleX, ground, middleZ);
        Rotation rotation = Rotation.getRandom(context.random());
        return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(new CrookedTreePiece(origin, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.CROOKED_TREE.get();
    }
}
