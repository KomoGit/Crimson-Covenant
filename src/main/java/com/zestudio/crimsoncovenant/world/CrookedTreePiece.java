package com.zestudio.crimsoncovenant.world;

import java.util.List;

import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/// A Crooked Tree: a dead, twisted tree of Crooked Logs beside a long straight dirt road, the whole of it fenced in, a
/// tilting-yard where the Headless Horseman can be called up and ridden against (see HorsemanSummoning). The road runs
/// out through gaps at both ends, and carved pumpkins stand watch on the fence posts.
///
/// Laid out facing south in its own coordinates: the yard is x -8 to 8 and z 0 to 44 inside its fence, the road down
/// the middle at x -1 to 1, the tree beside it at x 5, z 22, with the ground at y 0. It's turned to face any way.
public class CrookedTreePiece extends StructurePiece {
    public static final int HALF_WIDTH = 9;
    public static final int LENGTH = 44;
    private static final int CLEAR_HEIGHT = 30;
    private static final int FOUNDATION_DEPTH = 8;
    /// Every this many blocks along the fence, a post stands taller with a carved pumpkin on top
    private static final int POST_SPACING = 8;
    public static final int TREE_X = 5;
    public static final int TREE_Z = 22;
    /// The tree, as offsets from its foot: its trunk twisting up, then its bare branches
    private static final List<int[]> TRUNK = List.of(
            new int[] {0, 1, 0}, new int[] {1, 1, 0}, new int[] {0, 1, 1},
            new int[] {0, 2, 0},
            new int[] {0, 3, 0}, new int[] {1, 3, 0},
            new int[] {1, 4, 0},
            new int[] {1, 5, 0}, new int[] {1, 5, 1},
            new int[] {1, 6, 1},
            new int[] {1, 7, 1}, new int[] {0, 7, 1},
            new int[] {0, 8, 1},
            new int[] {0, 9, 1}, new int[] {-1, 9, 1},
            new int[] {-1, 10, 1});
    /// Branches: offset from the foot, and which way the log lies
    private static final List<int[]> BRANCHES = List.of(
            new int[] {2, 6, 1, 'x'}, new int[] {3, 6, 1, 'x'}, new int[] {3, 7, 1, 'y'}, new int[] {3, 8, 2, 'z'},
            new int[] {-1, 7, 1, 'x'}, new int[] {-2, 7, 1, 'x'}, new int[] {-2, 8, 0, 'z'}, new int[] {-3, 8, 0, 'x'},
            new int[] {-2, 10, 1, 'x'}, new int[] {-2, 11, 1, 'y'}, new int[] {-1, 11, 2, 'z'},
            new int[] {-1, 1, 0, 'x'}, new int[] {2, 1, 0, 'x'}, new int[] {0, 1, 2, 'z'});
    /// Twigs (bare fence posts) sticking up off the branch tips
    private static final List<int[]> TWIGS = List.of(new int[] {3, 9, 2}, new int[] {-3, 9, 0}, new int[] {-2, 12, 1}, new int[] {-1, 12, 2},
            new int[] {-1, 11, 1});

    private final BlockPos origin;
    private final Rotation rotation;

    public CrookedTreePiece(BlockPos origin, Rotation rotation) {
        super(ModStructures.CROOKED_TREE_PIECE.get(), 0, boundsOf(origin, rotation));
        this.origin = origin;
        this.rotation = rotation;
    }

    public CrookedTreePiece(CompoundTag tag) {
        super(ModStructures.CROOKED_TREE_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 0), tag.getIntOr("OZ", 0));
        this.rotation = Rotation.valueOf(tag.getStringOr("Rotation", Rotation.NONE.name()));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", this.origin.getX());
        tag.putInt("OY", this.origin.getY());
        tag.putInt("OZ", this.origin.getZ());
        tag.putString("Rotation", this.rotation.name());
    }

    private static BoundingBox boundsOf(BlockPos origin, Rotation rotation) {
        BlockPos a = HunterCabinPiece.turn(origin, rotation, -HALF_WIDTH - 1, -FOUNDATION_DEPTH, -2);
        BlockPos b = HunterCabinPiece.turn(origin, rotation, HALF_WIDTH + 1, CLEAR_HEIGHT, LENGTH + 2);
        return BoundingBox.fromCorners(a, b);
    }

    /// The world position of a spot in the yard's own coordinates
    public BlockPos pos(int x, int y, int z) {
        return HunterCabinPiece.turn(this.origin, this.rotation, x, y, z);
    }

    private void set(WorldGenLevel level, BoundingBox chunkBB, BlockState state, int x, int y, int z) {
        BlockPos pos = this.pos(x, y, z);
        if (chunkBB.isInside(pos)) {
            level.setBlock(pos, state.rotate(this.rotation), Block.UPDATE_CLIENTS);
        }
    }

    private void set(WorldGenLevel level, BoundingBox chunkBB, Block block, int x, int y, int z) {
        this.set(level, chunkBB, block.defaultBlockState(), x, y, z);
    }

    public static boolean onRoad(int x) {
        return x >= -1 && x <= 1;
    }

    /// Whether the fence runs through this spot (it's open where the road runs out)
    public static boolean isFence(int x, int z) {
        boolean side = (x == -HALF_WIDTH || x == HALF_WIDTH) && z >= -1 && z <= LENGTH + 1;
        boolean end = (z == -1 || z == LENGTH + 1) && x >= -HALF_WIDTH && x <= HALF_WIDTH && !onRoad(x);
        return side || end;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.layGround(level, random, chunkBB);
        this.buildFence(level, chunkBB);
        this.growTree(level, random, chunkBB);
    }

    // Foundations under the yard, a clear sky over it, the road down the middle and grass going to seed either side
    private void layGround(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        for (int x = -HALF_WIDTH - 1; x <= HALF_WIDTH + 1; x++) {
            for (int z = -2; z <= LENGTH + 2; z++) {
                for (int y = -1; y >= -FOUNDATION_DEPTH; y--) {
                    BlockPos pos = this.pos(x, y, z);
                    if (!chunkBB.isInside(pos)) {
                        break;
                    }
                    BlockState there = level.getBlockState(pos);
                    if (!there.isAir() && !there.liquid() && !there.canBeReplaced() && y < -1) {
                        break;
                    }
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                Block ground;
                if (onRoad(x)) {
                    ground = Blocks.DIRT_PATH;
                } else if (Math.abs(x) == 2 && random.nextInt(3) > 0) {
                    ground = Blocks.COARSE_DIRT;
                } else {
                    int roll = random.nextInt(10);
                    ground = roll == 0 ? Blocks.PODZOL : roll == 1 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
                }
                this.set(level, chunkBB, ground, x, 0, z);
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    this.set(level, chunkBB, Blocks.AIR, x, y, z);
                }
                // Dead grass and the odd bush, never on the road
                if (!onRoad(x) && !isFence(x, z) && ground == Blocks.GRASS_BLOCK && random.nextInt(6) == 0) {
                    this.set(level, chunkBB, random.nextInt(4) == 0 ? Blocks.DEAD_BUSH : Blocks.SHORT_GRASS, x, 1, z);
                }
            }
        }
    }

    // Dark oak fence all round, joined up, with a carved pumpkin keeping watch atop every eighth post
    private void buildFence(WorldGenLevel level, BoundingBox chunkBB) {
        for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
            for (int z = -1; z <= LENGTH + 1; z++) {
                if (!isFence(x, z)) {
                    continue;
                }
                BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState()
                        .setValue(FenceBlock.NORTH, isFence(x, z - 1))
                        .setValue(FenceBlock.SOUTH, isFence(x, z + 1))
                        .setValue(FenceBlock.EAST, isFence(x + 1, z))
                        .setValue(FenceBlock.WEST, isFence(x - 1, z));
                this.set(level, chunkBB, fence, x, 1, z);
                boolean corner = Math.abs(x) == HALF_WIDTH && (z == -1 || z == LENGTH + 1);
                if (corner || ((x == -HALF_WIDTH || x == HALF_WIDTH) && Math.floorMod(z, POST_SPACING) == 0)) {
                    this.set(level, chunkBB, Blocks.DARK_OAK_FENCE, x, 2, z);
                    Direction inward = x < 0 ? Direction.EAST : Direction.WEST;
                    this.set(level, chunkBB, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, inward), x, 3, z);
                }
            }
        }
    }

    // The Crooked Tree itself: a twisting trunk, bare branches and twigs, a few cobwebs, pumpkins rotting at its foot
    private void growTree(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        BlockState log = ModBlocks.CROOKED_LOG.get().defaultBlockState();
        for (int[] at : TRUNK) {
            this.set(level, chunkBB, log, TREE_X + at[0], at[1], TREE_Z + at[2]);
        }
        for (int[] at : BRANCHES) {
            Direction.Axis axis = at[3] == 'x' ? Direction.Axis.X : at[3] == 'z' ? Direction.Axis.Z : Direction.Axis.Y;
            this.set(level, chunkBB, log.setValue(RotatedPillarBlock.AXIS, axis), TREE_X + at[0], at[1], TREE_Z + at[2]);
        }
        for (int[] at : TWIGS) {
            this.set(level, chunkBB, Blocks.DARK_OAK_FENCE, TREE_X + at[0], at[1], TREE_Z + at[2]);
        }
        this.set(level, chunkBB, Blocks.COBWEB, TREE_X + 2, 5, TREE_Z + 1);
        this.set(level, chunkBB, Blocks.COBWEB, TREE_X - 2, 6, TREE_Z + 1);
        this.set(level, chunkBB, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.WEST), TREE_X - 1, 1, TREE_Z - 1);
        if (random.nextBoolean()) {
            this.set(level, chunkBB, Blocks.PUMPKIN, TREE_X + 2, 1, TREE_Z + 2);
        }
        this.set(level, chunkBB, Blocks.PODZOL, TREE_X + 1, 0, TREE_Z + 1);
        this.set(level, chunkBB, Blocks.PODZOL, TREE_X - 1, 0, TREE_Z);
    }
}
