package com.zestudio.crimsoncovenant.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModStructures;
import com.zestudio.crimsoncovenant.world.CabinStyle;
import com.zestudio.crimsoncovenant.world.HunterCabinPiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/// Tests for the Hunter Cabin structure
final class HunterCabinGameTests {
    /// The hunters who moved into the cabin built last
    private static final List<Hunter> MOVED_IN = new ArrayList<>();

    private HunterCabinGameTests() {}

    /// Builds a cabin this many chunks west of the test, far enough out that it can't land on another test
    private static HunterCabinPiece build(GameTestHelper helper, int chunksAway, Rotation rotation, CabinStyle style) {
        ServerLevel level = helper.getLevel();
        BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = testOrigin.offset(-chunksAway * 16, 0, 0);
        HunterCabinPiece cabin = new HunterCabinPiece(origin, rotation, style);
        BoundingBox box = cabin.getBoundingBox();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        Consumer<EntityJoinLevelEvent> moveIn = event -> {
            if (event.getEntity() instanceof Hunter hunter) {
                MOVED_IN.add(hunter);
            }
        };
        MOVED_IN.clear();
        NeoForge.EVENT_BUS.addListener(moveIn);
        try {
            cabin.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(7L), box,
                    ChunkPos.containing(cabin.pos(3, 1, 3)), origin);
        } finally {
            NeoForge.EVENT_BUS.unregister(moveIn);
        }
        return cabin;
    }

    private static void checkCabin(GameTestHelper helper, HunterCabinPiece cabin, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        CabinStyle style = cabin.style();
        String name = style.getSerializedName() + " cabin: ";
        helper.assertTrue(level.getBlockState(cabin.pos(0, 1, 0)).is(style.frame), Component.literal(name + "corner posts of " + style.frame));
        helper.assertTrue(level.getBlockState(cabin.pos(1, 2, 0)).is(style.walls), Component.literal(name + "walls of " + style.walls));
        helper.assertTrue(level.getBlockState(cabin.pos(3, 8, 3)).is(style.roofSlab), Component.literal(name + "a roof ridge of " + style.roofSlab));
        helper.assertTrue(level.getBlockState(cabin.pos(3, 1, 6)).getBlock() instanceof DoorBlock door
                        && level.getBlockState(cabin.pos(3, 1, 6)).getValue(DoorBlock.FACING) == rotation.rotate(Direction.NORTH) && door == style.door,
                Component.literal(name + "a door in the front wall, turned with the cabin"));
        helper.assertTrue(level.getBlockState(cabin.pos(3, 1, 1)).is(ModBlocks.HUNTERS_TABLE.get()), Component.literal(name + "a Hunter's Table"));
        helper.assertTrue(level.getBlockEntity(cabin.pos(2, 1, 1)) instanceof ChestBlockEntity chest && HunterCabinPiece.GEAR_LOOT.equals(chest.getLootTable()),
                Component.literal(name + "a chest of hunting gear"));
        helper.assertTrue(level.getBlockEntity(cabin.pos(4, 1, 1)) instanceof BarrelBlockEntity barrel && HunterCabinPiece.SUPPLIES_LOOT.equals(barrel.getLootTable()),
                Component.literal(name + "a barrel of supplies"));
        helper.assertTrue(level.getBlockState(cabin.pos(1, 1, 1)).getBlock() instanceof BedBlock && level.getBlockState(cabin.pos(5, 1, 2)).getBlock() instanceof BedBlock,
                Component.literal(name + "two beds"));
        helper.assertTrue(level.getBlockEntity(cabin.pos(3, 3, 1)) instanceof BannerBlockEntity banner && !banner.getPatterns().layers().isEmpty(),
                Component.literal(name + "the hunters' banner on the back wall"));
        for (int x : new int[] {2, 4}) {
            BlockPos braid = cabin.pos(x, 2, 7);
            helper.assertTrue(level.getBlockState(braid).is(ModBlocks.GARLIC_BRAID.get()) && level.getBlockState(braid).canSurvive(level, braid),
                    Component.literal(name + "garlic hanging by the door"));
        }
        helper.assertTrue(level.getBlockState(cabin.pos(3, 2, 3)).isAir(), Component.literal(name + "room to stand inside"));

        // Bear traps scattered round the outside, and none on the way in
        long traps = HunterCabinPiece.trapSpots().stream()
                .filter(spot -> level.getBlockState(cabin.pos(spot.getX(), spot.getY(), spot.getZ())).is(ModBlocks.BEAR_TRAP.get())).count();
        helper.assertTrue(traps >= 2 && traps <= 4, Component.literal(name + "2 to 4 bear traps round the outside, found " + traps));
        for (int z = 7; z <= 10; z++) {
            helper.assertTrue(!level.getBlockState(cabin.pos(3, 1, z)).is(ModBlocks.BEAR_TRAP.get()), Component.literal(name + "no bear trap on the path or porch"));
        }

        // The party that lives there
        AABB inside = AABB.encapsulatingFullBlocks(cabin.pos(0, 0, 0), cabin.pos(6, 4, 6));
        List<Hunter> hunters = MOVED_IN.stream().filter(hunter -> inside.contains(hunter.position())).toList();
        helper.assertTrue(hunters.size() >= 2 && hunters.stream().anyMatch(Hunter::isLeader), Component.literal(name + "a hunter party with its leader, found " + hunters.size()));
        hunters.forEach(Hunter::discard);
    }

    static void cabinIsBuilt(GameTestHelper helper) {
        checkCabin(helper, build(helper, 170, Rotation.NONE, CabinStyle.OAK), Rotation.NONE);
        checkCabin(helper, build(helper, 176, Rotation.CLOCKWISE_90, CabinStyle.DESERT), Rotation.CLOCKWISE_90);
        checkCabin(helper, build(helper, 182, Rotation.COUNTERCLOCKWISE_90, CabinStyle.SNOWY), Rotation.COUNTERCLOCKWISE_90);
        helper.succeed();
    }

    static void cabinStyleFollowsTheBiome(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        Map<ResourceKey<Biome>, CabinStyle> expected = Map.ofEntries(
                Map.entry(Biomes.PLAINS, CabinStyle.OAK),
                Map.entry(Biomes.FOREST, CabinStyle.OAK),
                Map.entry(Biomes.TAIGA, CabinStyle.SPRUCE),
                Map.entry(Biomes.SNOWY_TAIGA, CabinStyle.SNOWY),
                Map.entry(Biomes.SNOWY_PLAINS, CabinStyle.SNOWY),
                Map.entry(Biomes.BIRCH_FOREST, CabinStyle.BIRCH),
                Map.entry(Biomes.DARK_FOREST, CabinStyle.DARK_OAK),
                Map.entry(Biomes.JUNGLE, CabinStyle.JUNGLE),
                Map.entry(Biomes.SAVANNA, CabinStyle.ACACIA),
                Map.entry(Biomes.DESERT, CabinStyle.DESERT),
                Map.entry(Biomes.WOODED_BADLANDS, CabinStyle.BADLANDS),
                Map.entry(Biomes.MANGROVE_SWAMP, CabinStyle.SWAMP),
                Map.entry(Biomes.CHERRY_GROVE, CabinStyle.CHERRY),
                Map.entry(Biomes.WINDSWEPT_HILLS, CabinStyle.MOUNTAIN));
        expected.forEach((biome, style) -> helper.assertValueEqual(CabinStyle.forBiome(biomes.getOrThrow(biome)), style, "cabin style in " + biome.identifier()));
        helper.assertTrue(helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ModStructures.HUNTER_CABIN_STRUCTURE).isPresent(),
                Component.literal("The Hunter Cabin should be registered as a structure"));
        helper.succeed();
    }
}
