package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.AncientTranslatorBlock;
import com.zestudio.crimsoncovenant.block.BearTrapBlock;
import com.zestudio.crimsoncovenant.block.DecorBlock;
import com.zestudio.crimsoncovenant.block.GarlicBraidBlock;
import com.zestudio.crimsoncovenant.block.GarlicCropBlock;
import com.zestudio.crimsoncovenant.block.BloodAltarBlock;
import com.zestudio.crimsoncovenant.block.SilverNetBlock;
import com.zestudio.crimsoncovenant.block.SilverNetTrapBlock;
import com.zestudio.crimsoncovenant.block.SilverSpikesBlock;
import com.zestudio.crimsoncovenant.block.RatBurrowBlock;
import com.zestudio.crimsoncovenant.block.RatTrapBlock;
import com.zestudio.crimsoncovenant.block.SilveredBottleBlock;
import com.zestudio.crimsoncovenant.block.HorsemansHeadBlock;
import com.zestudio.crimsoncovenant.block.CoffinBlock;
import com.zestudio.crimsoncovenant.block.IronMaidenBlock;
import com.zestudio.crimsoncovenant.block.BloodThroneBlock;
import com.zestudio.crimsoncovenant.block.CurseforgeBlock;
import com.zestudio.crimsoncovenant.block.GravestoneBlock;
import com.zestudio.crimsoncovenant.block.TormentedSoulBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// All of the mod's blocks are registered here. Each one also gets a BlockItem in ModItems.
// Block models live in assets/crimsoncovenant/models/block; the hitboxes below match their elements.
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CrimsonCovenant.MODID);

    // ---- Silver ore (generates at the same heights as iron) ----

    public static final DeferredBlock<Block> SILVER_ORE = BLOCKS.registerSimpleBlock("silver_ore", properties -> properties
            .mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final DeferredBlock<Block> DEEPSLATE_SILVER_ORE = BLOCKS.registerSimpleBlock("deepslate_silver_ore", properties -> properties
            .mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    /// Nine silver ingots, the same as a block of iron (a beacon takes it too)
    public static final DeferredBlock<Block> SILVER_BLOCK = BLOCKS.registerSimpleBlock("silver_block", properties -> properties
            .mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.IRON));

    // ---- Gravestones ----

    private static final VoxelShape HEADSTONE = DecorBlock.shape(
            new double[] {3, 0, 5, 13, 2, 11}, new double[] {4, 2, 7, 12, 12, 9}, new double[] {5, 12, 7, 11, 13, 9}, new double[] {6, 13, 7, 10, 14, 9});
    private static final VoxelShape CROSS = DecorBlock.shape(
            new double[] {5, 0, 6, 11, 2, 10}, new double[] {7, 2, 7, 9, 15, 9}, new double[] {4, 9, 7, 12, 11, 9});
    private static final VoxelShape OBELISK = DecorBlock.shape(
            new double[] {3, 0, 3, 13, 2, 13}, new double[] {5, 2, 5, 11, 14, 11}, new double[] {6, 14, 6, 10, 15, 10}, new double[] {7, 15, 7, 9, 16, 9});
    private static final VoxelShape TOMB = DecorBlock.shape(
            new double[] {1, 0, 2, 15, 3, 14}, new double[] {7, 3, 4, 9, 4, 12}, new double[] {5, 3, 6, 11, 4, 8});

    public static final DeferredBlock<GravestoneBlock> GRAVESTONE = gravestone("gravestone", HEADSTONE, MapColor.STONE);
    public static final DeferredBlock<GravestoneBlock> MOSSY_GRAVESTONE = gravestone("mossy_gravestone", HEADSTONE, MapColor.STONE);
    public static final DeferredBlock<GravestoneBlock> CRACKED_GRAVESTONE = gravestone("cracked_gravestone", HEADSTONE, MapColor.STONE);
    public static final DeferredBlock<GravestoneBlock> STONE_CROSS = gravestone("stone_cross", CROSS, MapColor.STONE);
    public static final DeferredBlock<GravestoneBlock> DEEPSLATE_OBELISK = gravestone("deepslate_obelisk", OBELISK, MapColor.DEEPSLATE);
    public static final DeferredBlock<GravestoneBlock> STONE_TOMB = gravestone("stone_tomb", TOMB, MapColor.STONE);

    // ---- Villager workstations ----

    /// The Plague Doctor's job site (hunter faction)
    public static final DeferredBlock<DecorBlock> HUNTERS_TABLE = BLOCKS.registerBlock("hunters_table",
            properties -> new DecorBlock(properties, DecorBlock.shape(
                    new double[] {0, 13, 0, 16, 16, 16}, new double[] {1, 0, 1, 3, 13, 3}, new double[] {13, 0, 1, 15, 13, 3},
                    new double[] {1, 0, 13, 3, 13, 15}, new double[] {13, 0, 13, 15, 13, 15}, new double[] {3, 16, 6, 13, 17, 10})),
            properties -> properties.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());

    /// The Ancient Butler's job site (vampire faction)
    public static final DeferredBlock<BloodAltarBlock> BLOOD_ALTAR = BLOCKS.registerBlock("blood_altar",
            properties -> new BloodAltarBlock(properties, DecorBlock.shape(
                    new double[] {1, 0, 1, 15, 3, 15}, new double[] {3, 3, 3, 13, 11, 13}, new double[] {1, 11, 1, 15, 14, 15}, new double[] {4, 14, 4, 12, 15, 12})),
            properties -> properties.mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.STONE).noOcclusion().lightLevel(state -> 4));

    // ---- Blood skills ----

    /// Translates Ancient Tomes into blood skills. Glows while it works.
    public static final DeferredBlock<AncientTranslatorBlock> ANCIENT_TRANSLATOR = BLOCKS.registerBlock("ancient_translator", AncientTranslatorBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0F, 1200.0F)
                    .lightLevel(state -> AncientTranslatorBlock.isTranslating(state) ? 10 : 5));

    // ---- Garlic ----

    /// Planted from a bulb of garlic. Its item is ModItems.GARLIC.
    public static final DeferredBlock<GarlicCropBlock> GARLIC_CROP = BLOCKS.registerBlock("garlic_crop", GarlicCropBlock::new,
            properties -> properties.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
    /// Hung from a ceiling, a wall or a door to keep the undead away (see vampirism.GarlicWard)
    public static final DeferredBlock<GarlicBraidBlock> GARLIC_BRAID = BLOCKS.registerBlock("garlic_braid", GarlicBraidBlock::new,
            properties -> properties.mapColor(MapColor.SAND).noCollision().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));

    // ---- Hunting gear ----

    /// Snaps shut on whatever steps in it and holds it until it's broken. Silver: bites the undead hardest.
    public static final DeferredBlock<BearTrapBlock> BEAR_TRAP = BLOCKS.registerBlock("bear_trap", BearTrapBlock::new,
            properties -> properties.mapColor(MapColor.METAL).noCollision().strength(2.0F, 6.0F).sound(SoundType.CHAIN).pushReaction(PushReaction.POPPED));

    /// Rats live in these: earth with a hole gnawed in it (see entity.Rat)
    public static final DeferredBlock<RatBurrowBlock> RAT_BURROW = BLOCKS.registerBlock("rat_burrow", RatBurrowBlock::new,
            properties -> properties.mapColor(MapColor.DIRT).strength(0.6F).sound(SoundType.ROOTED_DIRT));
    /// The same, gnawed into the side of planks or logs
    public static final DeferredBlock<RatBurrowBlock> RAT_HOLE = BLOCKS.registerBlock("rat_hole", RatBurrowBlock::new,
            properties -> properties.mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    /// A baited snap trap that kills rats
    public static final DeferredBlock<RatTrapBlock> RAT_TRAP = BLOCKS.registerBlock("rat_trap", RatTrapBlock::new,
            properties -> properties.mapColor(MapColor.WOOD).noCollision().strength(0.5F).sound(SoundType.WOOD).pushReaction(PushReaction.POPPED));

    /// Stabs up through whoever steps on it, then sinks back
    public static final DeferredBlock<SilverSpikesBlock> SILVER_SPIKES = BLOCKS.registerBlock("silver_spikes", SilverSpikesBlock::new,
            properties -> properties.mapColor(MapColor.METAL).noCollision().strength(2.0F, 6.0F).sound(SoundType.CHAIN).pushReaction(PushReaction.POPPED));
    /// Hangs from a ceiling and drops a Silver Net on whatever walks beneath
    public static final DeferredBlock<SilverNetTrapBlock> SILVER_NET_TRAP = BLOCKS.registerBlock("silver_net_trap", SilverNetTrapBlock::new,
            properties -> properties.mapColor(MapColor.WOOL).noCollision().strength(0.8F).sound(SoundType.WOOL).pushReaction(PushReaction.POPPED));
    /// What the net trap drops: tangles like a cobweb, and burns the undead
    public static final DeferredBlock<SilverNetBlock> SILVER_NET = BLOCKS.registerBlock("silver_net", SilverNetBlock::new,
            properties -> properties.mapColor(MapColor.WOOL).noCollision().strength(1.0F).sound(SoundType.WOOL).pushReaction(PushReaction.POPPED).noLootTable());

    /// A glass bottle lined with silver, which can hold a ghost; stands on the floor
    public static final DeferredBlock<SilveredBottleBlock> SILVERED_BOTTLE = BLOCKS.registerBlock("silvered_bottle", SilveredBottleBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(0.3F).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.POPPED)
                    .lightLevel(state -> state.getValue(SilveredBottleBlock.GHOST) ? SilveredBottleBlock.GHOST_LIGHT : 0));
    /// The Headless Horseman's head in a bottle, dropped when he dies; glows red
    public static final DeferredBlock<HorsemansHeadBlock> HORSEMANS_HEAD = BLOCKS.registerBlock("horsemans_head", HorsemansHeadBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_RED).strength(0.3F).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.POPPED)
                    .lightLevel(state -> HorsemansHeadBlock.LIGHT));

    /// A ghost crammed into a plain glass bottle, set down (see guide.Souls)
    public static final DeferredBlock<TormentedSoulBlock> TORMENTED_SOUL = BLOCKS.registerBlock("tormented_soul", TormentedSoulBlock::new,
            properties -> properties.mapColor(MapColor.NONE).strength(0.3F).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.POPPED)
                    .lightLevel(state -> TormentedSoulBlock.LIGHT));

    /// Slept in like a bed, at any hour; the only bed a vampire can use
    public static final DeferredBlock<CoffinBlock> COFFIN = BLOCKS.registerBlock("coffin", CoffinBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.WOOD).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED));
    /// Two blocks tall; holds a villager captive for a vampire to feed on
    public static final DeferredBlock<IronMaidenBlock> IRON_MAIDEN = BLOCKS.registerBlock("iron_maiden", IronMaidenBlock::new,
            properties -> properties.mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.IRON).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));

    /// Black stone, soaked velvet and skulls; sat on, it siphons the captives in Iron Maidens nearby
    public static final DeferredBlock<BloodThroneBlock> BLOOD_THRONE = BLOCKS.registerBlock("blood_throne", BloodThroneBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.POLISHED_DEEPSLATE).noOcclusion());

    /// An anvil a ghost was poured into: lays curses on weapons, and still works as an anvil, one that never wears out
    public static final DeferredBlock<CurseforgeBlock> CURSEFORGE = BLOCKS.registerBlock("curseforge", CurseforgeBlock::new,
            properties -> properties.mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(CurseforgeBlock.SOUNDS).noOcclusion()
                    .lightLevel(state -> 3));

    private ModBlocks() {}

    private static DeferredBlock<GravestoneBlock> gravestone(String name, VoxelShape shape, MapColor color) {
        return BLOCKS.registerBlock(name, properties -> new GravestoneBlock(properties, shape),
                properties -> properties.mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F).noOcclusion());
    }
}
