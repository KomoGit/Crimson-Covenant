package com.zestudio.crimsoncovenant.world;

import java.util.ArrayList;
import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.GarlicBraidBlock;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/// A Hunter Cabin: a one-room log cabin under a pitched roof, with a porch out front and a little yard. Two beds,
/// the hunters' gear chest and a barrel of supplies, a Hunter's Table under the hunters' banner, a crafting table and
/// a brewing stand. Garlic braids hang either side of the door to keep vampires off, and a few bear traps lie
/// scattered round the outside, along the walls and in the yard, though never on the path or the porch. A hunter
/// party lives in it: their leader, a hunter, and sometimes a chemist.
///
/// The cabin is laid out facing south in its own coordinates, x 0-6 and z 0-6 for the cabin itself, the porch at
/// z 7-8 and the yard out to z 10, with the floor at y 0. It's turned to face any way when it's built, and built from
/// whatever its CabinStyle says.
public class HunterCabinPiece extends StructurePiece {
    public static final ResourceKey<LootTable> GEAR_LOOT = ResourceKey.create(Registries.LOOT_TABLE, CrimsonCovenant.id("chests/hunter_cabin"));
    public static final ResourceKey<LootTable> SUPPLIES_LOOT = ResourceKey.create(Registries.LOOT_TABLE, CrimsonCovenant.id("chests/hunter_cabin_supplies"));
    // The area the cabin clears and builds over, in its own coordinates
    private static final int MIN_X = -1;
    private static final int MAX_X = 7;
    private static final int MIN_Z = -1;
    private static final int MAX_Z = 10;
    private static final int HEIGHT = 9;
    private static final int FOUNDATION_DEPTH = 12;
    private static final int MIN_TRAPS = 2;
    private static final int MAX_TRAPS = 4;
    private static final float CHEMIST_CHANCE = 0.4F;

    private final BlockPos origin;
    private final Rotation rotation;
    private final CabinStyle style;

    public HunterCabinPiece(BlockPos origin, Rotation rotation, CabinStyle style) {
        super(ModStructures.HUNTER_CABIN_PIECE.get(), 0, boundsOf(origin, rotation));
        this.origin = origin;
        this.rotation = rotation;
        this.style = style;
    }

    public HunterCabinPiece(CompoundTag tag) {
        super(ModStructures.HUNTER_CABIN_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 0), tag.getIntOr("OZ", 0));
        this.rotation = Rotation.valueOf(tag.getStringOr("Rotation", Rotation.NONE.name()));
        this.style = CabinStyle.CODEC.byName(tag.getStringOr("Style", CabinStyle.OAK.getSerializedName()), CabinStyle.OAK);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", this.origin.getX());
        tag.putInt("OY", this.origin.getY());
        tag.putInt("OZ", this.origin.getZ());
        tag.putString("Rotation", this.rotation.name());
        tag.putString("Style", this.style.getSerializedName());
    }

    public CabinStyle style() {
        return this.style;
    }

    private static BoundingBox boundsOf(BlockPos origin, Rotation rotation) {
        BlockPos a = turn(origin, rotation, MIN_X, -FOUNDATION_DEPTH, MIN_Z);
        BlockPos b = turn(origin, rotation, MAX_X, HEIGHT, MAX_Z);
        return BoundingBox.fromCorners(a, b);
    }

    /// Where a spot in a cabin's own coordinates lands, for a cabin laid out from `origin` and turned by `rotation`
    static BlockPos turn(BlockPos origin, Rotation rotation, int x, int y, int z) {
        return switch (rotation) {
            case NONE -> origin.offset(x, y, z);
            case CLOCKWISE_90 -> origin.offset(-z, y, x);
            case CLOCKWISE_180 -> origin.offset(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> origin.offset(z, y, -x);
        };
    }

    /// The world position of a spot in the cabin's own coordinates
    public BlockPos pos(int x, int y, int z) {
        return turn(this.origin, this.rotation, x, y, z);
    }

    /// Places a block given as if the cabin faced south, turned the way the cabin really faces
    private void set(WorldGenLevel level, BoundingBox chunkBB, BlockState state, int x, int y, int z) {
        BlockPos pos = this.pos(x, y, z);
        if (chunkBB.isInside(pos)) {
            level.setBlock(pos, state.rotate(this.rotation), Block.UPDATE_CLIENTS);
        }
    }

    private void set(WorldGenLevel level, BoundingBox chunkBB, Block block, int x, int y, int z) {
        this.set(level, chunkBB, block.defaultBlockState(), x, y, z);
    }

    private static boolean inCabin(int x, int z) {
        return x >= 0 && x <= 6 && z >= 0 && z <= 6;
    }

    private static boolean onPorch(int x, int z) {
        return x >= 0 && x <= 6 && (z == 7 || z == 8);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.clearAndLayGround(level, random, chunkBB);
        this.buildWalls(level, chunkBB);
        this.buildRoof(level, chunkBB);
        this.buildPorch(level, chunkBB);
        this.furnish(level, random, chunkBB);
        this.addTouch(level, random, chunkBB);
        this.scatterTraps(level, chunkBB);
        this.moveHuntersIn(level, random, chunkBB);
    }

    // Foundations under the cabin and porch, ground and a path everywhere else, and nothing in the way above
    private void clearAndLayGround(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                boolean built = inCabin(x, z) || onPorch(x, z);
                BlockState below = built ? this.style.foundation.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                for (int y = -1; y >= -FOUNDATION_DEPTH; y--) {
                    BlockPos pos = this.pos(x, y, z);
                    if (!chunkBB.isInside(pos)) {
                        break;
                    }
                    BlockState there = level.getBlockState(pos);
                    if (!there.isAir() && !there.liquid() && !there.canBeReplaced() && y < -1) {
                        break;
                    }
                    level.setBlock(pos, below, Block.UPDATE_CLIENTS);
                }
                Block surface = built ? this.style.floor : (x == 3 && z > 8 ? this.style.path : this.style.ground);
                this.set(level, chunkBB, surface, x, 0, z);
                for (int y = 1; y <= HEIGHT; y++) {
                    this.set(level, chunkBB, Blocks.AIR, x, y, z);
                }
            }
        }
    }

    // Log posts on the corners, walls between, windows either side and either side of the door
    private void buildWalls(WorldGenLevel level, BoundingBox chunkBB) {
        BlockState frame = this.style.frame.defaultBlockState();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
                boolean wall = x == 0 || x == 6 || z == 0 || z == 6;
                for (int y = 1; y <= 3; y++) {
                    if (corner) {
                        this.set(level, chunkBB, frame, x, y, z);
                    } else if (wall) {
                        this.set(level, chunkBB, this.style.walls, x, y, z);
                    }
                }
                // The ceiling, which is also the attic floor
                this.set(level, chunkBB, corner ? frame : this.style.walls.defaultBlockState(), x, 4, z);
            }
        }
        BlockState northSouthPane = Blocks.GLASS_PANE.defaultBlockState().setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
        BlockState eastWestPane = Blocks.GLASS_PANE.defaultBlockState().setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
        this.set(level, chunkBB, northSouthPane, 0, 2, 3);
        this.set(level, chunkBB, northSouthPane, 6, 2, 3);
        this.set(level, chunkBB, eastWestPane, 1, 2, 6);
        this.set(level, chunkBB, eastWestPane, 5, 2, 6);
        BlockState door = this.style.door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        this.set(level, chunkBB, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3, 1, 6);
        this.set(level, chunkBB, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3, 2, 6);
    }

    // A pitched roof running front to back, overhanging by a block, with the gable ends filled in
    private void buildRoof(WorldGenLevel level, BoundingBox chunkBB) {
        BlockState eastFacing = this.style.roofStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState westFacing = this.style.roofStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        for (int z = -1; z <= 7; z++) {
            for (int step = 0; step <= 3; step++) {
                int y = 4 + step;
                this.set(level, chunkBB, eastFacing, -1 + step, y, z);
                this.set(level, chunkBB, westFacing, 7 - step, y, z);
                // The gables, front and back
                if ((z == 0 || z == 6) && step >= 1) {
                    for (int x = step; x <= 6 - step; x++) {
                        this.set(level, chunkBB, this.style.walls, x, y, z);
                    }
                }
            }
            this.set(level, chunkBB, this.style.roofSlab, 3, 8, z);
        }
    }

    // A railed porch with an opening for the path, and a lantern on the corner post
    private void buildPorch(WorldGenLevel level, BoundingBox chunkBB) {
        for (int x = 0; x <= 6; x++) {
            if (x == 3) {
                continue;
            }
            BlockState fence = this.style.fence.defaultBlockState()
                    .setValue(CrossCollisionBlock.EAST, x != 2 && x != 6)
                    .setValue(CrossCollisionBlock.WEST, x != 0 && x != 4);
            this.set(level, chunkBB, fence, x, 1, 8);
        }
        this.set(level, chunkBB, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 0, 2, 8);
        this.set(level, chunkBB, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 6, 2, 8);
        // Garlic either side of the door, for the vampires
        BlockState braid = ModBlocks.GARLIC_BRAID.get().defaultBlockState().setValue(GarlicBraidBlock.FACING, Direction.SOUTH).setValue(GarlicBraidBlock.HANGING, false);
        this.set(level, chunkBB, braid, 2, 2, 7);
        this.set(level, chunkBB, braid, 4, 2, 7);
    }

    private void furnish(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        // Beds against the back wall, heads to the wall
        for (int x : new int[] {1, 5}) {
            BlockState bed = Blocks.BED.pick(DyeColor.BROWN).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            this.set(level, chunkBB, bed.setValue(BedBlock.PART, BedPart.HEAD), x, 1, 1);
            this.set(level, chunkBB, bed.setValue(BedBlock.PART, BedPart.FOOT), x, 1, 2);
        }
        // The gear chest and supply barrel either side of the Hunter's Table, under the hunters' banner
        this.placeContainer(level, random, chunkBB, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 2, 1, 1, GEAR_LOOT);
        this.set(level, chunkBB, ModBlocks.HUNTERS_TABLE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3, 1, 1);
        this.placeContainer(level, random, chunkBB, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 4, 1, 1, SUPPLIES_LOOT);
        this.hangBanner(level, chunkBB, 3, 3, 1);
        // By the door: somewhere to craft, and somewhere to brew
        this.set(level, chunkBB, Blocks.CRAFTING_TABLE, 1, 1, 5);
        this.set(level, chunkBB, Blocks.BREWING_STAND, 5, 1, 5);
        this.set(level, chunkBB, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3, 3, 3);
    }

    /// Where bear traps can go: all round the outside of the cabin, along the walls, behind it and in the yard's
    /// corners, but never on the path, the porch or right by the door
    public static List<BlockPos> trapSpots() {
        List<BlockPos> spots = new ArrayList<>();
        for (int z = 0; z <= 6; z++) {
            spots.add(new BlockPos(MIN_X, 1, z));
            spots.add(new BlockPos(MAX_X, 1, z));
        }
        for (int x = 0; x <= 6; x++) {
            spots.add(new BlockPos(x, 1, MIN_Z));
        }
        for (int x : new int[] {0, 1, 5, 6}) {
            for (int z = 9; z <= MAX_Z; z++) {
                spots.add(new BlockPos(x, 1, z));
            }
        }
        return spots;
    }

    /// A few bear traps for whatever comes prowling. Chosen the same way for every chunk the cabin spans (by the
    /// cabin's own position, not the chunk's random), so a cabin across two chunks still gets one set of traps, and
    /// no two side by side.
    private void scatterTraps(WorldGenLevel level, BoundingBox chunkBB) {
        RandomSource random = RandomSource.create(this.origin.asLong());
        List<BlockPos> spots = trapSpots();
        List<BlockPos> chosen = new ArrayList<>();
        int wanted = MIN_TRAPS + random.nextInt(MAX_TRAPS - MIN_TRAPS + 1);
        while (chosen.size() < wanted && !spots.isEmpty()) {
            BlockPos spot = spots.remove(random.nextInt(spots.size()));
            if (chosen.stream().noneMatch(other -> other.distManhattan(spot) <= 2)) {
                chosen.add(spot);
            }
        }
        for (BlockPos spot : chosen) {
            BlockState trap = ModBlocks.BEAR_TRAP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
            this.set(level, chunkBB, trap, spot.getX(), spot.getY(), spot.getZ());
        }
    }

    private void placeContainer(WorldGenLevel level, RandomSource random, BoundingBox chunkBB, BlockState state, int x, int y, int z, ResourceKey<LootTable> loot) {
        BlockPos pos = this.pos(x, y, z);
        if (chunkBB.isInside(pos)) {
            this.set(level, chunkBB, state, x, y, z);
            RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
        }
    }

    private void hangBanner(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z) {
        BlockPos pos = this.pos(x, y, z);
        if (!chunkBB.isInside(pos)) {
            return;
        }
        this.set(level, chunkBB, Blocks.WALL_BANNER.pick(DyeColor.RED).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH), x, y, z);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BannerBlockEntity banner) {
            banner.applyComponentsFromItemStack(Hunter.createHunterBanner(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        }
    }

    private void addTouch(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        switch (this.style.touch) {
            case SNOW -> {
                for (int x = MIN_X; x <= MAX_X; x++) {
                    for (int z = MIN_Z; z <= MAX_Z; z++) {
                        boolean open = !inCabin(x, z) && !(onPorch(x, z) && z == 8 && x != 3) && !(x == 3 && z > 8);
                        if (open && !(onPorch(x, z) && (x == 2 || x == 4) && z == 7)) {
                            this.set(level, chunkBB, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(2)), x, 1, z);
                        }
                    }
                }
            }
            case VINES -> {
                for (int z = 0; z <= 6; z++) {
                    for (int y = 1; y <= 3; y++) {
                        if (random.nextFloat() < 0.45F) {
                            this.set(level, chunkBB, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true), -1, y, z);
                        }
                        if (random.nextFloat() < 0.45F) {
                            this.set(level, chunkBB, Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true), 7, y, z);
                        }
                    }
                }
            }
            case COBWEBS -> {
                this.set(level, chunkBB, Blocks.COBWEB, 1, 3, 1);
                this.set(level, chunkBB, Blocks.COBWEB, 5, 3, 5);
                for (int x = 1; x <= 5; x++) {
                    for (int z = 3; z <= 4; z++) {
                        if (random.nextFloat() < 0.3F) {
                            this.set(level, chunkBB, Blocks.MOSS_CARPET, x, 1, z);
                        }
                    }
                }
            }
            case NONE -> {
            }
        }
    }

    // The party that lives here, standing about inside; only spawned with the chunk the cabin's middle is in
    private void moveHuntersIn(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        if (!chunkBB.isInside(this.pos(3, 1, 3))) {
            return;
        }
        this.moveIn(level, ModEntities.HUNTER.get(), true, 3, 4);
        this.moveIn(level, ModEntities.HUNTER.get(), false, 2, 3);
        if (random.nextFloat() < CHEMIST_CHANCE) {
            this.moveIn(level, ModEntities.HUNTER_CHEMIST.get(), false, 4, 3);
        }
    }

    private void moveIn(WorldGenLevel level, EntityType<? extends Hunter> type, boolean leader, int x, int z) {
        Hunter hunter = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (hunter == null) {
            return;
        }
        BlockPos pos = this.pos(x, 1, z);
        hunter.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        hunter.setLeader(leader);
        hunter.setPersistenceRequired();
        hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        level.addFreshEntityWithPassengers(hunter);
    }
}
