package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.AncientTranslatorBlock;
import com.zestudio.crimsoncovenant.block.DecorBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
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

    // ---- Gravestones ----

    private static final VoxelShape HEADSTONE = DecorBlock.shape(
            new double[] {3, 0, 5, 13, 2, 11}, new double[] {4, 2, 7, 12, 12, 9}, new double[] {5, 12, 7, 11, 13, 9}, new double[] {6, 13, 7, 10, 14, 9});
    private static final VoxelShape CROSS = DecorBlock.shape(
            new double[] {5, 0, 6, 11, 2, 10}, new double[] {7, 2, 7, 9, 15, 9}, new double[] {4, 9, 7, 12, 11, 9});
    private static final VoxelShape OBELISK = DecorBlock.shape(
            new double[] {3, 0, 3, 13, 2, 13}, new double[] {5, 2, 5, 11, 14, 11}, new double[] {6, 14, 6, 10, 15, 10}, new double[] {7, 15, 7, 9, 16, 9});
    private static final VoxelShape TOMB = DecorBlock.shape(
            new double[] {1, 0, 2, 15, 3, 14}, new double[] {7, 3, 4, 9, 4, 12}, new double[] {5, 3, 6, 11, 4, 8});

    public static final DeferredBlock<DecorBlock> GRAVESTONE = gravestone("gravestone", HEADSTONE, MapColor.STONE);
    public static final DeferredBlock<DecorBlock> MOSSY_GRAVESTONE = gravestone("mossy_gravestone", HEADSTONE, MapColor.STONE);
    public static final DeferredBlock<DecorBlock> CRACKED_GRAVESTONE = gravestone("cracked_gravestone", HEADSTONE, MapColor.STONE);
    public static final DeferredBlock<DecorBlock> STONE_CROSS = gravestone("stone_cross", CROSS, MapColor.STONE);
    public static final DeferredBlock<DecorBlock> DEEPSLATE_OBELISK = gravestone("deepslate_obelisk", OBELISK, MapColor.DEEPSLATE);
    public static final DeferredBlock<DecorBlock> STONE_TOMB = gravestone("stone_tomb", TOMB, MapColor.STONE);

    // ---- Villager workstations ----

    /// The Old Hunter's job site (hunter faction)
    public static final DeferredBlock<DecorBlock> HUNTERS_TABLE = BLOCKS.registerBlock("hunters_table",
            properties -> new DecorBlock(properties, DecorBlock.shape(
                    new double[] {0, 13, 0, 16, 16, 16}, new double[] {1, 0, 1, 3, 13, 3}, new double[] {13, 0, 1, 15, 13, 3},
                    new double[] {1, 0, 13, 3, 13, 15}, new double[] {13, 0, 13, 15, 13, 15}, new double[] {3, 16, 6, 13, 17, 10})),
            properties -> properties.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());

    /// The Ancient Butler's job site (vampire faction)
    public static final DeferredBlock<DecorBlock> BLOOD_ALTAR = BLOCKS.registerBlock("blood_altar",
            properties -> new DecorBlock(properties, DecorBlock.shape(
                    new double[] {1, 0, 1, 15, 3, 15}, new double[] {3, 3, 3, 13, 11, 13}, new double[] {1, 11, 1, 15, 14, 15}, new double[] {4, 14, 4, 12, 15, 12})),
            properties -> properties.mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.STONE).noOcclusion().lightLevel(state -> 4));

    // ---- Blood skills ----

    /// Translates Ancient Tomes into blood skills. Glows while it works.
    public static final DeferredBlock<AncientTranslatorBlock> ANCIENT_TRANSLATOR = BLOCKS.registerBlock("ancient_translator", AncientTranslatorBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0F, 1200.0F)
                    .lightLevel(state -> AncientTranslatorBlock.isTranslating(state) ? 10 : 5));

    private ModBlocks() {}

    private static DeferredBlock<DecorBlock> gravestone(String name, VoxelShape shape, MapColor color) {
        return BLOCKS.registerBlock(name, properties -> new DecorBlock(properties, shape),
                properties -> properties.mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F).noOcclusion());
    }
}
