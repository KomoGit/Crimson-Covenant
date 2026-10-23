package com.zestudio.crimsoncovenant.gametest;

import java.util.Set;

import com.zestudio.crimsoncovenant.block.DecorBlock;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModStructures;
import com.zestudio.crimsoncovenant.world.GhostSpawner;
import com.zestudio.crimsoncovenant.world.GraveyardPiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/// Tests for the Graveyard structure and its biome
final class GraveyardGameTests {
    /// The graveyard is built this many chunks east of the test, well clear of any other test
    private static final int CHUNKS_AWAY = 24;

    private GraveyardGameTests() {}

    static void graveyardIsBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = ChunkPos.containing(testOrigin.offset(CHUNKS_AWAY * 16, 0, 0));
        BlockPos origin = new BlockPos(chunk.getMinBlockX(), testOrigin.getY(), chunk.getMinBlockZ());
        level.getChunk(chunk.x(), chunk.z());
        GraveyardPiece graveyard = new GraveyardPiece(RandomSource.create(42L), origin);
        BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ());
        graveyard.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(42L), chunkBox, chunk, origin);

        // The wall: soul lanterns on the corners, a stone wall with iron bars between the pillars, and a gate
        helper.assertTrue(level.getBlockState(graveyard.groundPos(0, 0).above(4)).is(Blocks.SOUL_LANTERN), Component.literal("Corner pillars carry soul lanterns"));
        helper.assertTrue(level.getBlockState(graveyard.groundPos(1, 0).above(1)).is(BlockTags.WALLS), Component.literal("A low wall runs between the pillars"));
        helper.assertTrue(level.getBlockState(graveyard.groundPos(1, 0).above(2)).is(Blocks.IRON_BARS), Component.literal("Iron bars top the wall"));
        helper.assertTrue(level.getBlockState(graveyard.groundPos(0, 1).above(3)).is(Blocks.IRON_BARS), Component.literal("The bars run down the sides too"));
        helper.assertTrue(level.getBlockState(graveyard.groundPos(7, 15).above(1)).isAir(), Component.literal("The gate is open"));

        // Every headstone has a chest of grave goods buried under its mound
        Set<Block> headstones = Set.of(ModBlocks.GRAVESTONE.get(), ModBlocks.MOSSY_GRAVESTONE.get(), ModBlocks.CRACKED_GRAVESTONE.get(),
                ModBlocks.STONE_CROSS.get(), ModBlocks.DEEPSLATE_OBELISK.get(), ModBlocks.STONE_TOMB.get());
        int graves = 0;
        for (int x : new int[] {2, 5, 10, 13}) {
            for (int z : new int[] {2, 6, 10}) {
                if (!headstones.contains(level.getBlockState(graveyard.groundPos(x, z).above()).getBlock())) {
                    continue;
                }
                graves++;
                helper.assertTrue(level.getBlockState(graveyard.groundPos(x, z).above()).getValue(DecorBlock.FACING) == Direction.SOUTH,
                        Component.literal("Headstones should face the gate"));
                BlockPos chestPos = graveyard.groundPos(x, z + 1).below(2);
                helper.assertTrue(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest && GraveyardPiece.GRAVE_LOOT.equals(chest.getLootTable()),
                        Component.literal("Grave at " + x + ", " + z + " should have a chest of grave goods two blocks under its mound"));
                helper.assertTrue(level.getBlockState(chestPos.above()).isSolid() && level.getBlockState(chestPos.above(2)).isSolid(),
                        Component.literal("The chest should be buried, not open to the sky"));
            }
        }
        helper.assertTrue(graves >= 4, Component.literal("Expected a graveyard full of graves, found " + graves));

        // Its chunk is the Graveyard biome
        helper.assertTrue(level.getBiome(graveyard.groundPos(8, 8).above()).is(GraveyardPiece.BIOME), Component.literal("The graveyard's chunk should be the Graveyard biome"));
        helper.succeed();
    }

    static void graveyardsAreHaunted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> graveyard = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.GRAVEYARD_STRUCTURE);
        helper.assertTrue(graveyard.value().spawnOverrides().containsKey(MobCategory.MONSTER), Component.literal("Undead should rise in graveyards"));
        helper.assertTrue(GhostSpawner.isHauntedKind(graveyard), Component.literal("Ghosts should haunt graveyards"));

        // The grave goods: a few things in every grave
        LootTable goods = level.getServer().reloadableRegistries().getLootTable(GraveyardPiece.GRAVE_LOOT);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
        boolean anyHandful = false;
        for (int i = 0; i < 20; i++) {
            var items = goods.getRandomItems(params);
            helper.assertTrue(items.stream().anyMatch(stack -> !stack.isEmpty()), Component.literal("Every grave should hold something"));
            anyHandful |= items.stream().anyMatch(stack -> stack.getCount() > 1);
        }
        helper.assertTrue(anyHandful, Component.literal("Grave goods should come in handfuls, not always one at a time"));
        helper.succeed();
    }
}
