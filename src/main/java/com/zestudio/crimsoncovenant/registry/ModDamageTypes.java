package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/// Damage types are data-driven: each key here has a JSON file in data/crimsoncovenant/damage_type/.
public final class ModDamageTypes {
    /// The Master Vampire's aura. Ignores armor, doesn't knock back.
    public static final ResourceKey<DamageType> LIFE_DRAIN = ResourceKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("life_drain"));
    /// Holy Flame's burn. Ignores armor, and isn't fire damage, so fire resistance and fire immunity don't help.
    public static final ResourceKey<DamageType> HOLY_FIRE = ResourceKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("holy_fire"));

    /// Blood skills cast by vampire players (Blood Spear, Blood Parade)
    public static final ResourceKey<DamageType> BLOOD_MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("blood_magic"));

    /// A bear trap's jaws snapping shut
    public static final ResourceKey<DamageType> BEAR_TRAP = ResourceKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("bear_trap"));

    /// Silver spikes, or a silver net
    public static final ResourceKey<DamageType> SILVER_TRAP = ResourceKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("silver_trap"));

    /// Silver dust breathed in, by the undead
    public static final ResourceKey<DamageType> SILVER_DUST = ResourceKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("silver_dust"));

    /// A rat trap snapping shut
    public static final ResourceKey<DamageType> RAT_TRAP = ResourceKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("rat_trap"));

    private ModDamageTypes() {}
}
