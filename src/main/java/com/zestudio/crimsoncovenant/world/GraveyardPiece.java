package com.zestudio.crimsoncovenant.world;

import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModStructures;
import com.zestudio.crimsoncovenant.block.GravestoneBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/// The graveyard itself, one chunk square. A low stone-brick wall topped with iron bars runs all the way round,
/// broken by stone pillars every three blocks, with soul lanterns on the corners and a gate in the middle of the
/// south side. A gravel path leads in from the gate between up to twelve graves in three rows. Each grave is a
/// headstone facing the path, a mound of earth in front of it, and a chest of grave goods buried two blocks under
/// the mound. In the back corner stands a dead tree.
///
/// When it's built it also paints its chunk with the Graveyard biome, from a little below the ground upwards.
public class GraveyardPiece extends StructurePiece {
    public static final int SIZE = 16;
    /// The ground layer, counted up from the bottom of the piece; the chests sit below it
    private static final int GROUND = 3;
    private static final int CHEST_DEPTH = 2;
    private static final int CLEAR_HEIGHT = 10;
    public static final ResourceKey<LootTable> GRAVE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, CrimsonCovenant.id("chests/graveyard_grave"));
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, CrimsonCovenant.id("graveyard"));

    // Where the headstones can go; the mound runs two blocks south of each
    private static final int[] GRAVE_X = {2, 5, 10, 13};
    private static final int[] GRAVE_Z = {2, 6, 10};
    private static final float GRAVE_CHANCE = 0.8F;
    /// How many of the graves have a ghost resting in the stone (see GravestoneBlock)
    private static final float GHOST_CHANCE = 0.5F;
    private static final List<Block> HEADSTONES = List.of(ModBlocks.GRAVESTONE.get(), ModBlocks.MOSSY_GRAVESTONE.get(), ModBlocks.CRACKED_GRAVESTONE.get(),
            ModBlocks.STONE_CROSS.get(), ModBlocks.DEEPSLATE_OBELISK.get(), ModBlocks.STONE_TOMB.get());

    public GraveyardPiece(RandomSource random, BlockPos origin) {
        super(ModStructures.GRAVEYARD_PIECE.get(), 0, new BoundingBox(origin.getX(), origin.getY() - GROUND, origin.getZ(),
                origin.getX() + SIZE - 1, origin.getY() + CLEAR_HEIGHT, origin.getZ() + SIZE - 1));
        // South keeps the piece's own coordinates the same way round as the world's
        this.setOrientation(Direction.SOUTH);
    }

    public GraveyardPiece(CompoundTag tag) {
        super(ModStructures.GRAVEYARD_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    /// The world position of the ground block at `x, z` in the graveyard
    public BlockPos groundPos(int x, int z) {
        return this.getWorldPos(x, GROUND, z);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.layGround(level, random, chunkBB);
        this.buildWalls(level, random, chunkBB);
        for (int x : GRAVE_X) {
            for (int z : GRAVE_Z) {
                if (random.nextFloat() < GRAVE_CHANCE) {
                    this.digGrave(level, random, chunkBB, x, z);
                }
            }
        }
        this.plantDeadTree(level, chunkBB, 13, 13);
        this.paintBiome(level, chunkPos);
    }

    /// Places a block as given. (A piece's own placeBlock would mirror it: a south-facing piece flips every block's
    /// facing north-south, which would turn the headstones away from the gate.)
    private void set(WorldGenLevel level, BoundingBox chunkBB, BlockState state, int x, int y, int z) {
        BlockPos pos = this.getWorldPos(x, GROUND + y, z);
        if (chunkBB.isInside(pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean isPath(int x, int z) {
        return (x == 7 || x == 8) && z >= 1;
    }

    // Earth below, the ground layer, and nothing above but the odd tuft of grass
    private void layGround(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                this.fillColumnDown(level, Blocks.DIRT.defaultBlockState(), x, 0, z, chunkBB);
                for (int y = -GROUND + 1; y < 0; y++) {
                    this.set(level, chunkBB, Blocks.DIRT.defaultBlockState(), x, y, z);
                }
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    this.set(level, chunkBB, Blocks.AIR.defaultBlockState(), x, y, z);
                }
                BlockState ground;
                if (isPath(x, z)) {
                    ground = random.nextFloat() < 0.8F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    float roll = random.nextFloat();
                    ground = roll < 0.6F ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : roll < 0.85F ? Blocks.COARSE_DIRT.defaultBlockState()
                            : roll < 0.95F ? Blocks.PODZOL.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                }
                this.set(level, chunkBB, ground, x, 0, z);
                boolean inside = x > 0 && x < SIZE - 1 && z > 0 && z < SIZE - 1;
                if (inside && !isPath(x, z)) {
                    float roll = random.nextFloat();
                    if (ground.is(Blocks.GRASS_BLOCK) && roll < 0.1F) {
                        this.set(level, chunkBB, Blocks.SHORT_GRASS.defaultBlockState(), x, 1, z);
                    } else if (roll > 0.95F) {
                        this.set(level, chunkBB, Blocks.DEAD_BUSH.defaultBlockState(), x, 1, z);
                    }
                }
            }
        }
    }

    // ---- The wall ----

    private void buildWalls(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean northOrSouth = z == 0 || z == SIZE - 1;
                boolean eastOrWest = x == 0 || x == SIZE - 1;
                if (!northOrSouth && !eastOrWest) {
                    continue;
                }
                int along = northOrSouth ? x : z;
                // The gate, between the pillars either side of the path
                if (z == SIZE - 1 && (x == 7 || x == 8)) {
                    continue;
                }
                if (along % 3 == 0) {
                    this.buildPillar(level, random, chunkBB, x, z, northOrSouth && eastOrWest);
                } else {
                    this.buildFence(level, random, chunkBB, x, z, northOrSouth);
                }
            }
        }
    }

    private void buildPillar(WorldGenLevel level, RandomSource random, BoundingBox chunkBB, int x, int z, boolean corner) {
        for (int y = 1; y <= 2; y++) {
            float roll = random.nextFloat();
            BlockState brick = roll < 0.5F ? Blocks.STONE_BRICKS.defaultBlockState()
                    : roll < 0.8F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            this.set(level, chunkBB, brick, x, y, z);
        }
        this.set(level, chunkBB, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), x, 3, z);
        this.set(level, chunkBB, corner ? Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false)
                : Blocks.STONE_BRICK_SLAB.defaultBlockState(), x, 4, z);
    }

    // A knee-high wall with two rows of iron bars on top, running along x (north and south) or z (east and west)
    private void buildFence(WorldGenLevel level, RandomSource random, BoundingBox chunkBB, int x, int z, boolean runsAlongX) {
        Block wallBlock = random.nextFloat() < 0.3F ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL;
        BlockState wall = wallBlock.defaultBlockState().setValue(WallBlock.UP, false);
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        if (runsAlongX) {
            wall = wall.setValue(WallBlock.EAST, WallSide.LOW).setValue(WallBlock.WEST, WallSide.LOW);
            bars = bars.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
        } else {
            wall = wall.setValue(WallBlock.NORTH, WallSide.LOW).setValue(WallBlock.SOUTH, WallSide.LOW);
            bars = bars.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
        }
        this.set(level, chunkBB, wall, x, 1, z);
        this.set(level, chunkBB, bars, x, 2, z);
        this.set(level, chunkBB, bars, x, 3, z);
    }

    // ---- Graves ----

    private void digGrave(WorldGenLevel level, RandomSource random, BoundingBox chunkBB, int x, int z) {
        Block headstone = HEADSTONES.get(random.nextInt(HEADSTONES.size()));
        GravestoneBlock.Spirit spirit = random.nextFloat() < GHOST_CHANCE ? GravestoneBlock.Spirit.RESTING : GravestoneBlock.Spirit.NONE;
        this.set(level, chunkBB, headstone.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH).setValue(GravestoneBlock.SPIRIT, spirit), x, 1, z);
        for (int dz = 1; dz <= 2; dz++) {
            BlockState mound = random.nextFloat() < 0.7F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
            this.set(level, chunkBB, mound, x, 0, z + dz);
            this.set(level, chunkBB, Blocks.AIR.defaultBlockState(), x, 1, z + dz);
        }
        // The grave goods, under the mound
        this.createChest(level, chunkBB, random, x, GROUND - CHEST_DEPTH, z + 1, GRAVE_LOOT);

        float roll = random.nextFloat();
        if (roll < 0.25F) {
            this.set(level, chunkBB, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3)), x, 1, z + 2);
        } else if (roll < 0.4F) {
            this.set(level, chunkBB, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)), x, 1, z + 2);
        } else if (roll < 0.5F) {
            this.set(level, chunkBB, Blocks.COBWEB.defaultBlockState(), x + (random.nextBoolean() ? 1 : -1), 1, z);
        }
    }

    // A bare, crooked trunk with a couple of stubby branches
    private void plantDeadTree(WorldGenLevel level, BoundingBox chunkBB, int x, int z) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        for (int y = 1; y <= 4; y++) {
            this.set(level, chunkBB, log, x, y, z);
        }
        this.set(level, chunkBB, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), x - 1, 3, z);
        this.set(level, chunkBB, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), x, 4, z - 1);
        this.set(level, chunkBB, log, x - 2, 4, z);
    }

    // ---- The biome ----

    /// Makes the whole chunk, from a little below the graveyard's ground upwards, the Graveyard biome
    private void paintBiome(WorldGenLevel level, ChunkPos chunkPos) {
        Holder<Biome> graveyard = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(BIOME);
        ChunkAccess chunk = level.getChunk(chunkPos.x(), chunkPos.z());
        int fromQuartY = QuartPos.fromBlock(this.groundPos(0, 0).getY() - 8);
        chunk.fillBiomesFromNoise((quartX, quartY, quartZ) -> quartY >= fromQuartY ? graveyard : chunk.getNoiseBiome(quartX, quartY, quartZ));
        chunk.markUnsaved();
        // Built into a world that's already running (by /place, or a test): tell players' clients
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
        }
    }
}
