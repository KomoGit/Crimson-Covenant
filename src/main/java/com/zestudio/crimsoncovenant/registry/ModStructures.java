package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.world.GraveyardPiece;
import com.zestudio.crimsoncovenant.world.GraveyardStructure;
import com.zestudio.crimsoncovenant.world.HunterCabinPiece;
import com.zestudio.crimsoncovenant.world.HunterCabinStructure;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// The mod's structures. Where they generate, and what spawns in them, is data: see data/crimsoncovenant/worldgen.
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, CrimsonCovenant.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, CrimsonCovenant.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<GraveyardStructure>> GRAVEYARD = STRUCTURE_TYPES.register("graveyard",
            () -> () -> GraveyardStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> GRAVEYARD_PIECE = STRUCTURE_PIECES.register("graveyard",
            () -> (StructurePieceType.ContextlessType) GraveyardPiece::new);

    public static final DeferredHolder<StructureType<?>, StructureType<HunterCabinStructure>> HUNTER_CABIN = STRUCTURE_TYPES.register("hunter_cabin",
            () -> () -> HunterCabinStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> HUNTER_CABIN_PIECE = STRUCTURE_PIECES.register("hunter_cabin",
            () -> (StructurePieceType.ContextlessType) HunterCabinPiece::new);

    /// The graveyard structure itself (worldgen/structure/graveyard.json)
    public static final ResourceKey<Structure> GRAVEYARD_STRUCTURE = ResourceKey.create(Registries.STRUCTURE, CrimsonCovenant.id("graveyard"));

    /// The Hunter Cabin structure itself (worldgen/structure/hunter_cabin.json)
    public static final ResourceKey<Structure> HUNTER_CABIN_STRUCTURE = ResourceKey.create(Registries.STRUCTURE, CrimsonCovenant.id("hunter_cabin"));

    private ModStructures() {}
}
