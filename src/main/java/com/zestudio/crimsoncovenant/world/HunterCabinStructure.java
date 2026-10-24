package com.zestudio.crimsoncovenant.world;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.zestudio.crimsoncovenant.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/// A Hunter Cabin on fairly level dry ground, facing any way, built in the style of the biome it stands in (see
/// CabinStyle). See HunterCabinPiece for the layout.
public class HunterCabinStructure extends Structure {
    public static final MapCodec<HunterCabinStructure> CODEC = simpleCodec(HunterCabinStructure::new);
    /// Ground that dips or climbs more than this under the cabin is too rough to build on
    private static final int MAX_SLOPE = 4;

    public HunterCabinStructure(Structure.StructureSettings settings) {
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
        for (int dx = -5; dx <= 5; dx += 5) {
            for (int dz = -5; dz <= 5; dz += 5) {
                int y = context.chunkGenerator().getFirstOccupiedHeight(middleX + dx, middleZ + dz, Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(), context.randomState());
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
            }
        }
        int surface = context.chunkGenerator().getFirstOccupiedHeight(middleX, middleZ, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        int floor = context.chunkGenerator().getFirstOccupiedHeight(middleX, middleZ, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        // Too steep, or under water
        if (highest - lowest > MAX_SLOPE || surface != floor) {
            return Optional.empty();
        }
        Rotation rotation = Rotation.getRandom(context.random());
        // The cabin is laid out from its back corner, so step back from the chunk's middle to centre it
        BlockPos centre = new BlockPos(middleX, surface - 1, middleZ);
        CabinStyle style = CabinStyle.forBiome(context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(middleX), QuartPos.fromBlock(surface),
                QuartPos.fromBlock(middleZ)));
        BlockPos origin = centre.subtract(HunterCabinPiece.turn(BlockPos.ZERO, rotation, 3, 0, 4));
        return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(new HunterCabinPiece(origin, rotation, style))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HUNTER_CABIN.get();
    }
}
