package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

public final class ModTags {
    private ModTags() {}

    public static final class Entities {
        /// Mobs vampires attack on sight (players are handled separately)
        public static final TagKey<EntityType<?>> VAMPIRE_PREY = TagKey.create(Registries.ENTITY_TYPE, CrimsonCovenant.id("vampire_prey"));
        /// Creatures with an entry in the guide's bestiary, once seen (see guide.Bestiary)
        public static final TagKey<EntityType<?>> BESTIARY = TagKey.create(Registries.ENTITY_TYPE, CrimsonCovenant.id("bestiary"));
        /// Who an Iron Maiden can be used on, to take them captive
        public static final TagKey<EntityType<?>> IRON_MAIDEN_CAPTIVES = TagKey.create(Registries.ENTITY_TYPE, CrimsonCovenant.id("iron_maiden_captives"));
        /// Silver arrows hit these harder and slow them: undead and werewolves
        public static final TagKey<EntityType<?>> WEAK_TO_SILVER = TagKey.create(Registries.ENTITY_TYPE, CrimsonCovenant.id("weak_to_silver"));
        /// Too powerful for the Possess skill: bosses, minibosses and the master vampires and hunters
        public static final TagKey<EntityType<?>> CANNOT_BE_POSSESSED = TagKey.create(Registries.ENTITY_TYPE, CrimsonCovenant.id("cannot_be_possessed"));

        private Entities() {}
    }

    public static final class Blocks {
        /// Earth rats can dig their burrows into
        public static final TagKey<Block> RATS_BURROW_IN = TagKey.create(Registries.BLOCK, CrimsonCovenant.id("rats_burrow_in"));
        /// Wood rats can gnaw their way into, from the side only
        public static final TagKey<Block> RATS_GNAW_INTO = TagKey.create(Registries.BLOCK, CrimsonCovenant.id("rats_gnaw_into"));
        /// What rats eat when they raid at night: crops, and hay, pumpkins, melons, berries and the like
        public static final TagKey<Block> RATS_EAT = TagKey.create(Registries.BLOCK, CrimsonCovenant.id("rats_eat"));
        /// Ordinary things a ghost can slip into, to set them floating about (see entity.HauntedObject)
        public static final TagKey<Block> HAUNTABLE = TagKey.create(Registries.BLOCK, CrimsonCovenant.id("hauntable"));

        private Blocks() {}
    }

    public static final class Items {
        /// What a Rat Trap can be baited with
        public static final TagKey<Item> RAT_BAIT = TagKey.create(Registries.ITEM, CrimsonCovenant.id("rat_bait"));
        /// What rats eat off the ground besides anything edible
        public static final TagKey<Item> RATS_SCAVENGE = TagKey.create(Registries.ITEM, CrimsonCovenant.id("rats_scavenge"));
        /// What the melee curses can be laid on
        public static final TagKey<Item> CURSEABLE_MELEE = TagKey.create(Registries.ITEM, CrimsonCovenant.id("curseable_melee"));
        /// What the projectile curses can be laid on
        public static final TagKey<Item> CURSEABLE_RANGED = TagKey.create(Registries.ITEM, CrimsonCovenant.id("curseable_ranged"));

        private Items() {}
    }

    public static final class DamageTypes {
        /// The only damage that can hurt a Ghost (plus /kill and the void)
        public static final TagKey<DamageType> HURTS_GHOSTS = TagKey.create(Registries.DAMAGE_TYPE, CrimsonCovenant.id("hurts_ghosts"));

        private DamageTypes() {}
    }
}
