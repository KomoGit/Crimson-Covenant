package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CrimsonCovenant.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.crimsoncovenant"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.HUNTER_SPAWN_EGG.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // The blood orb is only what the Blood Parade looks like in flight, and the blood spear only comes from its skill
                ModItems.ITEMS.getEntries().stream().filter(item -> item != ModItems.BLOOD_ORB && item != ModItems.BLOOD_SPEAR && item != ModItems.BLOOD_CHARGE).forEach(item -> output.accept(item.get()));
                output.accept(PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.HOLY_WATER));
                output.accept(PotionContents.createItemStack(Items.LINGERING_POTION, ModPotions.HOLY_WATER));
                // Black Blood is only ever drunk, so there's no splash or lingering kind
                output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.BLACK_BLOOD));
                output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.LONG_BLACK_BLOOD));
                output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.STRONG_BLACK_BLOOD));
                for (Item container : new Item[] {Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION}) {
                    output.accept(PotionContents.createItemStack(container, ModPotions.GARLIC));
                    output.accept(PotionContents.createItemStack(container, ModPotions.LONG_GARLIC));
                    output.accept(PotionContents.createItemStack(container, ModPotions.STRONG_GARLIC));
                }
                output.accept(PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.SILVER));
                output.accept(PotionContents.createItemStack(Items.LINGERING_POTION, ModPotions.SILVER));
                for (Item container : new Item[] {Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION}) {
                    output.accept(PotionContents.createItemStack(container, ModPotions.CLEANSE));
                }
                output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.EPHEMERAL));
                output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.LONG_EPHEMERAL));
            })
            .build());

    private ModCreativeTabs() {}
}
