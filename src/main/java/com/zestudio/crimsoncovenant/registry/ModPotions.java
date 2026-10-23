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

    /// Brewed by hunters from nether wart (an Awkward Potion) and a Token of Blood. Melee attackers are hurt back.
    /// Redstone makes it last longer, glowstone makes it hit back harder. It's only ever drunk: there's no splash or
    /// lingering Black Blood.
    public static final DeferredHolder<Potion, Potion> BLACK_BLOOD = POTIONS.register("black_blood",
            () -> new Potion("black_blood", new MobEffectInstance(ModEffects.BLACK_BLOOD, 3600)));
    public static final DeferredHolder<Potion, Potion> LONG_BLACK_BLOOD = POTIONS.register("long_black_blood",
            () -> new Potion("black_blood", new MobEffectInstance(ModEffects.BLACK_BLOOD, 9600)));
    public static final DeferredHolder<Potion, Potion> STRONG_BLACK_BLOOD = POTIONS.register("strong_black_blood",
            () -> new Potion("black_blood", new MobEffectInstance(ModEffects.BLACK_BLOOD, 1800, 1)));

    /// Brewed from garlic and an Awkward Potion, and meant to be thrown: undead caught in it are weakened.
    /// Redstone and glowstone as usual; gunpowder and dragon's breath make it splash and linger.
    public static final DeferredHolder<Potion, Potion> GARLIC = POTIONS.register("garlic",
            () -> new Potion("garlic", new MobEffectInstance(ModEffects.GARLIC, 1800)));
    public static final DeferredHolder<Potion, Potion> LONG_GARLIC = POTIONS.register("long_garlic",
            () -> new Potion("garlic", new MobEffectInstance(ModEffects.GARLIC, 4800)));
    public static final DeferredHolder<Potion, Potion> STRONG_GARLIC = POTIONS.register("strong_garlic",
            () -> new Potion("garlic", new MobEffectInstance(ModEffects.GARLIC, 900, 1)));

    /// Ectoplasm boiled down with an Awkward Potion: the drinker turns into a ghost for a while. Redstone makes it
    /// last longer. Drink-only, like Black Blood.
    public static final DeferredHolder<Potion, Potion> EPHEMERAL = POTIONS.register("ephemeral",
            () -> new Potion("ephemeral", new MobEffectInstance(ModEffects.EPHEMERAL, 600)));
    public static final DeferredHolder<Potion, Potion> LONG_EPHEMERAL = POTIONS.register("long_ephemeral",
            () -> new Potion("ephemeral", new MobEffectInstance(ModEffects.EPHEMERAL, 1800)));

    /// Silver dust in an Awkward Potion, made to be thrown (gunpowder) or left lingering (dragon's breath): undead it
    /// touches flee from where it landed. Harmless to the living.
    public static final DeferredHolder<Potion, Potion> SILVER = POTIONS.register("silver",
            () -> new Potion("silver", new MobEffectInstance(ModEffects.SILVER_DREAD, 300)));

    private ModPotions() {}
}
