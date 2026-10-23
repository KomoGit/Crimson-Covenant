package com.zestudio.crimsoncovenant.registry;

import com.google.common.collect.ImmutableSet;
import com.zestudio.crimsoncovenant.CrimsonCovenant;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// The two faction villagers. A village can only host one faction (see FactionVillagers).
/// The Ancient Butler sells Ancient Tomes of the purchasable blood skills; the Old Hunter has no trades yet.
/// Trades are data-driven: see data/crimsoncovenant/trade_set and villager_trade.
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CrimsonCovenant.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, CrimsonCovenant.MODID);

    // Job sites: the block a villager claims to take on the profession
    public static final DeferredHolder<PoiType, PoiType> HUNTERS_TABLE_POI = jobSite("hunters_table", ModBlocks.HUNTERS_TABLE);
    public static final DeferredHolder<PoiType, PoiType> BLOOD_ALTAR_POI = jobSite("blood_altar", ModBlocks.BLOOD_ALTAR);

    /// Hunter faction
    public static final DeferredHolder<VillagerProfession, VillagerProfession> OLD_HUNTER = profession("old_hunter", HUNTERS_TABLE_POI.getKey(), SoundEvents.VILLAGER_WORK_FLETCHER,
            Int2ObjectMaps.emptyMap());
    /// Vampire faction
    public static final DeferredHolder<VillagerProfession, VillagerProfession> ANCIENT_BUTLER = profession("ancient_butler", BLOOD_ALTAR_POI.getKey(), SoundEvents.VILLAGER_WORK_CLERIC,
            Int2ObjectMap.ofEntries(
                    Int2ObjectMap.entry(1, tradeSet("ancient_butler/level_1")),
                    Int2ObjectMap.entry(2, tradeSet("ancient_butler/level_2")),
                    Int2ObjectMap.entry(3, tradeSet("ancient_butler/level_3")),
                    Int2ObjectMap.entry(4, tradeSet("ancient_butler/level_4"))));

    private ModVillagers() {}

    private static DeferredHolder<PoiType, PoiType> jobSite(String name, DeferredBlock<? extends Block> block) {
        return POI_TYPES.register(name, () -> new PoiType(ImmutableSet.copyOf(block.get().getStateDefinition().getPossibleStates()), 1, 1));
    }

    private static ResourceKey<TradeSet> tradeSet(String path) {
        return ResourceKey.create(Registries.TRADE_SET, CrimsonCovenant.id(path));
    }

    private static DeferredHolder<VillagerProfession, VillagerProfession> profession(String name, ResourceKey<PoiType> jobSite, SoundEvent workSound,
            Int2ObjectMap<ResourceKey<TradeSet>> tradeSets) {
        return PROFESSIONS.register(name, () -> new VillagerProfession(
                Component.translatable("entity." + CrimsonCovenant.MODID + ".villager." + name),
                poi -> poi.is(jobSite),
                poi -> poi.is(jobSite),
                ImmutableSet.of(),
                ImmutableSet.of(),
                workSound,
                tradeSets));
    }
}
