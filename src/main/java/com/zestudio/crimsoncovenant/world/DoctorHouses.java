package com.zestudio.crimsoncovenant.world;

import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.mixin.StructureTemplatePoolAccessor;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/// Every kind of village can build a Plague Doctor's house: a little surgery with a Hunter's Table, a brewing stand
/// and a baited rat trap, in the village's own style. Its villager takes the table and becomes the village's Plague
/// Doctor. The houses (structure/village/<type>/plague_doctor_house) join each vanilla village's pool of houses as
/// the server starts.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class DoctorHouses {
    public static final List<String> VILLAGES = List.of("plains", "desert", "savanna", "snowy", "taiga");
    /// About as common as any one of the village's own workshops
    private static final int WEIGHT = 2;

    private DoctorHouses() {}

    public static String houseFor(String village) {
        return CrimsonCovenant.id("village/" + village + "/plague_doctor_house").toString();
    }

    @SubscribeEvent
    static void addToVillages(ServerAboutToStartEvent event) {
        addToVillages(event.getServer().registryAccess());
    }

    public static void addToVillages(RegistryAccess registries) {
        Registry<StructureTemplatePool> pools = registries.lookupOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processors = registries.lookupOrThrow(Registries.PROCESSOR_LIST);
        for (String village : VILLAGES) {
            StructureTemplatePool pool = pools.getValue(Identifier.withDefaultNamespace("village/" + village + "/houses"));
            if (pool == null) {
                continue;
            }
            var templates = ((StructureTemplatePoolAccessor) pool).crimsoncovenant$templates();
            String location = houseFor(village);
            if (templates.stream().anyMatch(element -> element.toString().contains(location))) {
                continue;
            }
            // Plains houses weather with moss like their neighbours
            String processorList = village.equals("plains") ? "mossify_10_percent" : "empty";
            Holder<StructureProcessorList> weathering = processors.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace(processorList)));
            StructurePoolElement house = StructurePoolElement.legacy(location, weathering).apply(StructureTemplatePool.Projection.RIGID);
            for (int i = 0; i < WEIGHT; i++) {
                templates.add(house);
            }
        }
    }
}
