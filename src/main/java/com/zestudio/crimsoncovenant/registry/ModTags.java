package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;

public final class ModTags {
    private ModTags() {}

    public static final class Entities {
        /// Mobs vampires attack on sight (players are handled separately)
        public static final TagKey<EntityType<?>> VAMPIRE_PREY = TagKey.create(Registries.ENTITY_TYPE, CrimsonCovenant.id("vampire_prey"));
        /// Silver arrows hit these harder and slow them: undead and werewolves
        public static final TagKey<EntityType<?>> WEAK_TO_SILVER = TagKey.create(Registries.ENTITY_TYPE, CrimsonCovenant.id("weak_to_silver"));
        /// Too powerful for the Possess skill: bosses, minibosses and the master vampires and hunters
        public static final TagKey<EntityType<?>> CANNOT_BE_POSSESSED = TagKey.create(Registries.ENTITY_TYPE, CrimsonCovenant.id("cannot_be_possessed"));

        private Entities() {}
    }

    public static final class DamageTypes {
        /// The only damage that can hurt a Ghost (plus /kill and the void)
        public static final TagKey<DamageType> HURTS_GHOSTS = TagKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("hurts_ghosts"));

        private DamageTypes() {}
    }
}
