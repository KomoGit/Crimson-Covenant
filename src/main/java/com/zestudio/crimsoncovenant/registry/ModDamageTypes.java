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

    private ModDamageTypes() {}
}
