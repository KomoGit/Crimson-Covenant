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

/// A Crooked Tree and its fenced tilting-yard, in the woods, on ground level enough to lay a long road over and not
/// under water. See CrookedTreePiece for the layout.
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
        Rotation rotation = Rotation.getRandom(context.random());
        // The yard is laid out from the middle of its near end, so step back half its length to centre it
        BlockPos probe = new BlockPos(middleX, 0, middleZ);
        BlockPos start = probe.subtract(HunterCabinPiece.turn(BlockPos.ZERO, rotation, 0, 0, CrookedTreePiece.LENGTH / 2));
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int x = -CrookedTreePiece.HALF_WIDTH; x <= CrookedTreePiece.HALF_WIDTH; x += CrookedTreePiece.HALF_WIDTH) {
            for (int z = 0; z <= CrookedTreePiece.LENGTH; z += CrookedTreePiece.LENGTH / 4) {
                BlockPos at = HunterCabinPiece.turn(start, rotation, x, 0, z);
                int y = context.chunkGenerator().getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(), context.randomState());
                int surface = context.chunkGenerator().getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                // Under water
                if (surface != y) {
                    return Optional.empty();
                }
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
            }
        }
        if (highest - lowest > MAX_SLOPE) {
            return Optional.empty();
        }
        int ground = context.chunkGenerator().getFirstOccupiedHeight(middleX, middleZ, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState()) - 1;
        BlockPos origin = new BlockPos(start.getX(), ground, start.getZ());
        return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(new CrookedTreePiece(origin, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.CROOKED_TREE.get();
    }
}
