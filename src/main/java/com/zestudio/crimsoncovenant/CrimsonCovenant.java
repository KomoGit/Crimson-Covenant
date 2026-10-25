package com.zestudio.crimsoncovenant;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.zestudio.crimsoncovenant.gametest.ModGameTests;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModCreativeTabs;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModMenus;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.registry.ModRecipes;
import com.zestudio.crimsoncovenant.registry.ModStructures;
import com.zestudio.crimsoncovenant.registry.ModVillagers;
import com.zestudio.crimsoncovenant.registry.ModBlockEntities;
import com.zestudio.crimsoncovenant.guide.LockedTomes;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// The value here must match the modId entry in META-INF/neoforge.mods.toml
@Mod(CrimsonCovenant.MODID)
public class CrimsonCovenant {
    public static final String MODID = "crimsoncovenant";
    public static final Logger LOGGER = LogUtils.getLogger();

    // FML passes in the mod event bus and container automatically
    public CrimsonCovenant(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);
        ModPotions.POTIONS.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModVillagers.POI_TYPES.register(modEventBus);
        ModStructures.STRUCTURE_TYPES.register(modEventBus);
        ModStructures.STRUCTURE_PIECES.register(modEventBus);
        ModVillagers.PROFESSIONS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        LockedTomes.LOOT_MODIFIERS.register(modEventBus);
        ModGameTests.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Crimson Covenant is awakening...");
    }

    // Shorthand for building "crimsoncovenant:<path>" ids
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
