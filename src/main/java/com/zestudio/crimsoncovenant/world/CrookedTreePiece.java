package com.zestudio.crimsoncovenant.world;

import java.util.List;

import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
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

/// A Crooked Tree: a dead, twisted tree of Crooked Logs on a bare mound in the middle of a square fenced yard, where the
/// Headless Horseman can be called up and fought (see HorsemanSummoning). A dirt road runs in a ring round the tree for
/// him to ride, and straight out through gates north and south. Carved pumpkins watch from the fence posts, and a
/// scarecrow stands in each corner.
///
/// Laid out in its own coordinates round the tree's foot at 0, 0, 0 (the ground at y 0): the fence runs RADIUS blocks
/// out each way, the ring road RING blocks out. It's turned to face any way.
public class CrookedTreePiece extends StructurePiece {
    public static final int RADIUS = 20;
    public static final int RING = 13;
    private static final double RING_WIDTH = 1.6;
    /// How far past the gates the roads run out
    private static final int ROAD_OUT = 3;
    private static final int MOUND = 4;
    private static final int CLEAR_HEIGHT = 30;
    private static final int FOUNDATION_DEPTH = 8;
    /// Every this many blocks along the fence, a post stands taller with a carved pumpkin on top
    private static final int POST_SPACING = 5;
    private static final int SCARECROW = 16;
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
        BlockPos a = HunterCabinPiece.turn(origin, rotation, -RADIUS - 1, -FOUNDATION_DEPTH, -RADIUS - ROAD_OUT);
        BlockPos b = HunterCabinPiece.turn(origin, rotation, RADIUS + 1, CLEAR_HEIGHT, RADIUS + ROAD_OUT);
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

    /// Whether the road runs over this spot: the ring round the tree, or the way out to either gate
    public static boolean onRoad(int x, int z) {
        double fromTree = Mth.length(x, z);
        boolean ring = fromTree >= RING - RING_WIDTH && fromTree < RING + RING_WIDTH;
        boolean outward = Math.abs(x) <= 1 && Math.abs(z) >= RING && Math.abs(z) <= RADIUS + ROAD_OUT;
        return ring || outward;
    }

    /// Whether the fence runs through this spot (it's open at the gates, where the roads run out)
    public static boolean isFence(int x, int z) {
        boolean side = Math.abs(x) == RADIUS && Math.abs(z) <= RADIUS;
        boolean end = Math.abs(z) == RADIUS && Math.abs(x) <= RADIUS && Math.abs(x) > 1;
        return side || end;
    }

    private static boolean inYard(int x, int z) {
        return Math.abs(x) <= RADIUS + 1 && Math.abs(z) <= RADIUS + 1;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.layGround(level, random, chunkBB);
        this.buildFence(level, chunkBB);
        this.raiseScarecrows(level, chunkBB);
        this.growTree(level, random, chunkBB);
    }

    // Foundations under the yard, a clear sky over it, the road, a bare mound round the tree and dying grass elsewhere
    private void layGround(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - ROAD_OUT; z <= RADIUS + ROAD_OUT; z++) {
                boolean road = onRoad(x, z);
                if (!inYard(x, z) && !road) {
                    continue;
                }
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
                double fromTree = Mth.length(x, z);
                Block ground;
                if (road) {
                    ground = Blocks.DIRT_PATH;
                } else if (fromTree <= MOUND) {
                    ground = random.nextInt(3) == 0 ? Blocks.COARSE_DIRT : Blocks.PODZOL;
                } else {
                    int roll = random.nextInt(12);
                    ground = roll == 0 ? Blocks.PODZOL : roll <= 2 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
                }
                this.set(level, chunkBB, ground, x, 0, z);
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    this.set(level, chunkBB, Blocks.AIR, x, y, z);
                }
                if (!road && !isFence(x, z) && ground == Blocks.GRASS_BLOCK && random.nextInt(7) == 0) {
                    this.set(level, chunkBB, random.nextInt(3) == 0 ? Blocks.DEAD_BUSH : Blocks.SHORT_GRASS, x, 1, z);
                }
            }
        }
    }

    // Dark oak fence all round, joined up, with carved pumpkins keeping watch from the taller posts and either side of
    // each gate
    private void buildFence(WorldGenLevel level, BoundingBox chunkBB) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (!isFence(x, z)) {
                    continue;
                }
                BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState()
                        .setValue(FenceBlock.NORTH, isFence(x, z - 1))
                        .setValue(FenceBlock.SOUTH, isFence(x, z + 1))
                        .setValue(FenceBlock.EAST, isFence(x + 1, z))
                        .setValue(FenceBlock.WEST, isFence(x - 1, z));
                this.set(level, chunkBB, fence, x, 1, z);
                boolean gatepost = Math.abs(z) == RADIUS && Math.abs(x) == 2;
                boolean post = Math.floorMod(x, POST_SPACING) == 0 && Math.floorMod(z, POST_SPACING) == 0;
                if (gatepost || post) {
                    this.set(level, chunkBB, Blocks.DARK_OAK_FENCE, x, 2, z);
                    this.set(level, chunkBB, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, inward(x, z)), x, 3, z);
                }
            }
        }
    }

    // The way from the fence at x, z towards the tree, along whichever side it's on
    private static Direction inward(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? Direction.WEST : Direction.EAST;
        }
        return z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    // A scarecrow in each corner: a post, a bale of hay for a body, and a carved pumpkin for a head, staring at the tree
    private void raiseScarecrows(WorldGenLevel level, BoundingBox chunkBB) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = sx * SCARECROW;
                int z = sz * SCARECROW;
                this.set(level, chunkBB, Blocks.DARK_OAK_FENCE, x, 1, z);
                this.set(level, chunkBB, Blocks.HAY_BLOCK, x, 2, z);
                this.set(level, chunkBB, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, inward(x, z)), x, 3, z);
            }
        }
    }

    // The Crooked Tree itself: a twisting trunk, bare branches and twigs, a few cobwebs, pumpkins rotting at its foot
    private void growTree(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        BlockState log = ModBlocks.CROOKED_LOG.get().defaultBlockState();
        for (int[] at : TRUNK) {
            this.set(level, chunkBB, log, at[0], at[1], at[2]);
        }
        for (int[] at : BRANCHES) {
            Direction.Axis axis = at[3] == 'x' ? Direction.Axis.X : at[3] == 'z' ? Direction.Axis.Z : Direction.Axis.Y;
            this.set(level, chunkBB, log.setValue(RotatedPillarBlock.AXIS, axis), at[0], at[1], at[2]);
        }
        for (int[] at : TWIGS) {
            this.set(level, chunkBB, Blocks.DARK_OAK_FENCE, at[0], at[1], at[2]);
        }
        this.set(level, chunkBB, Blocks.COBWEB, 2, 5, 1);
        this.set(level, chunkBB, Blocks.COBWEB, -2, 6, 1);
        this.set(level, chunkBB, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH), -1, 1, -1);
        if (random.nextBoolean()) {
            this.set(level, chunkBB, Blocks.PUMPKIN, 2, 1, 2);
        }
    }
}
