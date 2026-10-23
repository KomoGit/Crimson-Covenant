package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, CrimsonCovenant.MODID);

    /// Thrown by Master Hunters as a splash potion. Burns undead, harmless to everything else.
    public static final DeferredHolder<Potion, Potion> HOLY_WATER = POTIONS.register("holy_water",
            () -> new Potion("holy_water", new MobEffectInstance(ModEffects.HOLY_WATER)));

    private ModPotions() {}
}
