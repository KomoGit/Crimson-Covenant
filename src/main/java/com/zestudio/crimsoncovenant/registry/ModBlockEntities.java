package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.RatBurrowBlockEntity;
import com.zestudio.crimsoncovenant.block.IronMaidenBlockEntity;
import com.zestudio.crimsoncovenant.block.SilveredBottleBlockEntity;
import com.zestudio.crimsoncovenant.block.AncientTranslatorBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CrimsonCovenant.MODID);

    /// The rats living in a Rat Burrow
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RatBurrowBlockEntity>> RAT_BURROW = BLOCK_ENTITY_TYPES.register("rat_burrow",
            () -> new BlockEntityType<>(RatBurrowBlockEntity::new, ModBlocks.RAT_BURROW.get(), ModBlocks.RAT_HOLE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IronMaidenBlockEntity>> IRON_MAIDEN = BLOCK_ENTITY_TYPES.register("iron_maiden",
            () -> new BlockEntityType<>(IronMaidenBlockEntity::new, ModBlocks.IRON_MAIDEN.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AncientTranslatorBlockEntity>> ANCIENT_TRANSLATOR = BLOCK_ENTITY_TYPES.register("ancient_translator",
            () -> new BlockEntityType<>(AncientTranslatorBlockEntity::new, ModBlocks.ANCIENT_TRANSLATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SilveredBottleBlockEntity>> SILVERED_BOTTLE = BLOCK_ENTITY_TYPES.register("silvered_bottle",
            () -> new BlockEntityType<>(SilveredBottleBlockEntity::new, ModBlocks.SILVERED_BOTTLE.get()));

    private ModBlockEntities() {}
}
