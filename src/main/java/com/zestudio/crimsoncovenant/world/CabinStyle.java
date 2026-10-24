package com.zestudio.crimsoncovenant.world;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/// What a Hunter Cabin is built from, picked by the biome it stands in so it looks like it was put up from what grew
/// nearby: spruce in the taiga, sandstone in the desert, terracotta in the badlands, mangrove in the swamp, and so on.
/// Oak is the fallback for any biome (a modded one, say) that doesn't match one of the others.
public enum CabinStyle implements StringRepresentable {
    // name, frame (corner posts), walls, roof stairs, roof ridge slab, foundation, floor, fence, door, ground, path, extra touch
    OAK("oak", Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS,
            Blocks.OAK_FENCE, Blocks.OAK_DOOR, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Touch.NONE),
    SPRUCE("spruce", Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.MOSSY_COBBLESTONE, Blocks.SPRUCE_PLANKS,
            Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR, Blocks.PODZOL, Blocks.COARSE_DIRT, Touch.NONE),
    SNOWY("snowy", Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS,
            Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR, Blocks.SNOW_BLOCK, Blocks.COARSE_DIRT, Touch.SNOW),
    BIRCH("birch", Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.COBBLESTONE, Blocks.OAK_PLANKS,
            Blocks.BIRCH_FENCE, Blocks.BIRCH_DOOR, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Touch.NONE),
    DARK_OAK("dark_oak", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.MOSSY_COBBLESTONE, Blocks.SPRUCE_PLANKS,
            Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Touch.COBWEBS),
    JUNGLE("jungle", Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.BAMBOO_MOSAIC_STAIRS, Blocks.BAMBOO_MOSAIC_SLAB, Blocks.MOSSY_COBBLESTONE, Blocks.BAMBOO_PLANKS,
            Blocks.JUNGLE_FENCE, Blocks.JUNGLE_DOOR, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Touch.VINES),
    ACACIA("acacia", Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.TERRACOTTA, Blocks.ACACIA_PLANKS,
            Blocks.ACACIA_FENCE, Blocks.ACACIA_DOOR, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Touch.NONE),
    DESERT("desert", Blocks.CHISELED_SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.CUT_SANDSTONE, Blocks.BIRCH_PLANKS,
            Blocks.BIRCH_FENCE, Blocks.BIRCH_DOOR, Blocks.SAND, Blocks.SANDSTONE, Touch.NONE),
    BADLANDS("badlands", Blocks.CHISELED_RED_SANDSTONE, Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE), Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE,
            Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR, Blocks.RED_SAND, Blocks.COARSE_DIRT, Touch.NONE),
    SWAMP("swamp", Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.MUD_BRICKS, Blocks.MANGROVE_PLANKS,
            Blocks.MANGROVE_FENCE, Blocks.MANGROVE_DOOR, Blocks.GRASS_BLOCK, Blocks.MUD, Touch.VINES),
    CHERRY("cherry", Blocks.CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.STONE_BRICKS, Blocks.CHERRY_PLANKS,
            Blocks.CHERRY_FENCE, Blocks.CHERRY_DOOR, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Touch.NONE),
    MOUNTAIN("mountain", Blocks.SPRUCE_LOG, Blocks.COBBLESTONE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS,
            Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR, Blocks.STONE, Blocks.GRAVEL, Touch.NONE);

    public static final StringRepresentable.EnumCodec<CabinStyle> CODEC = StringRepresentable.fromEnum(CabinStyle::values);

    /// A little something extra a style adds once the cabin is built
    public enum Touch {
        NONE,
        /// Snow drifted over the yard and the porch
        SNOW,
        /// Vines climbing the outside walls
        VINES,
        /// Cobwebs in the corners and moss on the floor
        COBWEBS
    }

    private final String name;
    public final Block frame;
    public final Block walls;
    public final Block roofStairs;
    public final Block roofSlab;
    public final Block foundation;
    public final Block floor;
    public final Block fence;
    public final Block door;
    public final Block ground;
    public final Block path;
    public final Touch touch;

    CabinStyle(String name, Block frame, Block walls, Block roofStairs, Block roofSlab, Block foundation, Block floor, Block fence, Block door,
            Block ground, Block path, Touch touch) {
        this.name = name;
        this.frame = frame;
        this.walls = walls;
        this.roofStairs = roofStairs;
        this.roofSlab = roofSlab;
        this.foundation = foundation;
        this.floor = floor;
        this.fence = fence;
        this.door = door;
        this.ground = ground;
        this.path = path;
        this.touch = touch;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /// The style for a biome, read from its name, so it also fits modded biomes named after the vanilla ones
    public static CabinStyle forBiome(Holder<Biome> biome) {
        String name = biome.unwrapKey().map(ResourceKey::identifier).map(id -> id.getPath()).orElse("");
        if (name.contains("badlands") || name.contains("mesa")) {
            return BADLANDS;
        }
        if (name.contains("desert")) {
            return DESERT;
        }
        if (name.contains("jungle")) {
            return JUNGLE;
        }
        if (name.contains("savanna")) {
            return ACACIA;
        }
        if (name.contains("swamp") || name.contains("mangrove") || name.contains("marsh")) {
            return SWAMP;
        }
        if (name.contains("cherry")) {
            return CHERRY;
        }
        if (name.contains("dark_forest") || name.contains("pale_garden")) {
            return DARK_OAK;
        }
        if (name.contains("birch")) {
            return BIRCH;
        }
        if (name.contains("snowy") || name.contains("frozen") || name.contains("ice") || name.contains("grove") || name.contains("jagged")) {
            return SNOWY;
        }
        if (name.contains("taiga")) {
            return SPRUCE;
        }
        if (name.contains("peaks") || name.contains("windswept") || name.contains("stony")) {
            return MOUNTAIN;
        }
        return OAK;
    }
}
