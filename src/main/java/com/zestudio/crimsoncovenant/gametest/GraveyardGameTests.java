package com.zestudio.crimsoncovenant.gametest;

import java.util.Set;

import com.zestudio.crimsoncovenant.block.DecorBlock;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModStructures;
import com.zestudio.crimsoncovenant.world.GhostSpawner;
import com.zestudio.crimsoncovenant.world.GraveyardPiece;
import com.zestudio.crimsoncovenant.block.GravestoneBlock;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.entity.HauntedObject;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/// Tests for the Graveyard structure and its biome
final class GraveyardGameTests {
    /// The graveyard is built this many chunks east of the test, well clear of any other test
    private static final int CHUNKS_AWAY = 160;

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
        int ghosts = 0;
        for (int x : new int[] {2, 5, 10, 13}) {
            for (int z : new int[] {2, 6, 10}) {
                if (!headstones.contains(level.getBlockState(graveyard.groundPos(x, z).above()).getBlock())) {
                    continue;
                }
                graves++;
                if (level.getBlockState(graveyard.groundPos(x, z).above()).getValue(GravestoneBlock.SPIRIT) == GravestoneBlock.Spirit.RESTING) {
                    ghosts++;
                }
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
        helper.assertTrue(ghosts > 0 && ghosts < graves, Component.literal("Some of the graves (not all) should have a ghost resting in the stone, found " + ghosts));

        // Its chunk is the Graveyard biome
        helper.assertTrue(level.getBiome(graveyard.groundPos(8, 8).above()).is(GraveyardPiece.BIOME), Component.literal("The graveyard's chunk should be the Graveyard biome"));
        helper.succeed();
    }

    static void graveyardsAreHaunted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> graveyard = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.GRAVEYARD_STRUCTURE);
        helper.assertTrue(graveyard.value().spawnOverrides().containsKey(MobCategory.MONSTER), Component.literal("Undead should rise in graveyards"));
        // Its ghosts are its own, bound to its gravestones: none are added to it, so it can't be farmed
        helper.assertTrue(!GhostSpawner.isHauntedKind(graveyard), Component.literal("The ghost spawner shouldn't add ghosts to graveyards"));

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

    static void graveyardGhostsRestInTheirGraves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A stone set down by a player has nothing in it, and nothing ever rises from it
        BlockState placed = ModBlocks.GRAVESTONE.get().defaultBlockState();
        helper.assertTrue(placed.getValue(GravestoneBlock.SPIRIT) == GravestoneBlock.Spirit.NONE && !placed.isRandomlyTicking(),
                Component.literal("A placed gravestone should hold no ghost"));

        BlockPos grave = new BlockPos(2, 1, 2);
        helper.setBlock(grave, placed.setValue(GravestoneBlock.SPIRIT, GravestoneBlock.Spirit.RESTING));
        BlockPos stone = helper.absolutePos(grave);
        // By day, it rests
        GravestoneBlock.tend(level, stone, level.getBlockState(stone), false);
        helper.assertBlockProperty(grave, GravestoneBlock.SPIRIT, GravestoneBlock.Spirit.RESTING);
        // At night it rises, bound to its stone
        GravestoneBlock.tend(level, stone, level.getBlockState(stone), true);
        helper.assertBlockProperty(grave, GravestoneBlock.SPIRIT, GravestoneBlock.Spirit.ROAMING);
        Ghost ghost = GravestoneBlock.ghostOf(level, stone);
        helper.assertTrue(ghost != null && ghost.isPersistenceRequired(), Component.literal("A ghost should rise from the stone, bound to it, and never fade away"));
        // At dawn it goes back in
        ghost.enterGrave();
        helper.assertTrue(ghost.isRemoved(), Component.literal("At dawn the ghost should go back into its stone"));
        helper.assertBlockProperty(grave, GravestoneBlock.SPIRIT, GravestoneBlock.Spirit.RESTING);
        // Destroyed, it's gone for good: the stone lies empty, and nothing rises from it again
        Ghost risen = GravestoneBlock.rise(level, stone, level.getBlockState(stone));
        helper.assertTrue(risen != null, Component.literal("The ghost should rise again the next night"));
        risen.hurtServer(level, level.damageSources().source(ModDamageTypes.HOLY_FIRE), 1000.0F);
        helper.assertBlockProperty(grave, GravestoneBlock.SPIRIT, GravestoneBlock.Spirit.NONE);
        GravestoneBlock.tend(level, stone, level.getBlockState(stone), true);
        helper.assertTrue(GravestoneBlock.ghostOf(level, stone) == null, Component.literal("Nothing should rise from an empty stone"));
        helper.succeed();
    }

    static void ghostsSetThingsFloating(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos table = new BlockPos(3, 1, 3);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        Ghost ghost = helper.spawnWithNoFreeWill(ModEntities.GHOST.get(), new BlockPos(3, 2, 3));
        HauntedObject floating = ghost.possess(helper.absolutePos(table));
        helper.assertTrue(floating != null && floating.getHeld().is(Blocks.CRAFTING_TABLE), Component.literal("The ghost should slip into the crafting table"));
        helper.assertTrue(ghost.isRemoved(), Component.literal("The ghost should be inside the table now"));
        helper.assertBlockNotPresent(Blocks.CRAFTING_TABLE, table);
        // In time it settles back into its place, and the ghost slips out
        floating.settleBack(level);
        helper.assertBlockPresent(Blocks.CRAFTING_TABLE, table);
        helper.assertTrue(floating.isRemoved() && !helper.getEntities(ModEntities.GHOST.get()).isEmpty(), Component.literal("The ghost should slip back out"));

        // Holy water drives it out for good, and the table drops where it was
        Ghost again = helper.getEntities(ModEntities.GHOST.get()).getFirst();
        HauntedObject second = again.possess(helper.absolutePos(table));
        second.hurtServer(level, level.damageSources().source(ModDamageTypes.HOLY_FIRE), 1.0F);
        helper.assertTrue(second.isRemoved() && helper.getEntities(ModEntities.GHOST.get()).isEmpty(), Component.literal("Holy fire should drive the ghost out for good"));
        helper.assertTrue(helper.getEntities(EntityTypes.ITEM).stream().anyMatch(item -> item.getItem().is(Items.CRAFTING_TABLE)),
                Component.literal("The table should drop where it was"));

        // Nothing holding anything (a chest) can be haunted
        helper.setBlock(table.east(), Blocks.CHEST);
        Ghost third = helper.spawnWithNoFreeWill(ModEntities.GHOST.get(), new BlockPos(4, 2, 3));
        helper.assertTrue(third.possess(helper.absolutePos(table.east())) == null, Component.literal("A ghost shouldn't slip into a chest"));
        helper.getEntities(EntityTypes.ITEM).forEach(Entity::discard);
        third.discard();
        helper.succeed();
    }
}
