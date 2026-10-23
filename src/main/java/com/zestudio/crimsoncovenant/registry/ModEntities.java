package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.BloodOrb;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.entity.HemorrhageZone;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.MasterHunter;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.SilverArrow;
import com.zestudio.crimsoncovenant.entity.SwarmBat;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.VampireBat;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.world.BloodMoon;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(CrimsonCovenant.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Hunter>> HUNTER = ENTITY_TYPES.registerEntityType("hunter", Hunter::new, MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8));

    public static final DeferredHolder<EntityType<?>, EntityType<MasterHunter>> MASTER_HUNTER = ENTITY_TYPES.registerEntityType("master_hunter", MasterHunter::new, MobCategory.MONSTER,
            builder -> builder.sized(0.65F, 2.0F).eyeHeight(1.8F).clientTrackingRange(10));

    public static final DeferredHolder<EntityType<?>, EntityType<Vampire>> VAMPIRE = ENTITY_TYPES.registerEntityType("vampire", Vampire::new, MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<VampireBat>> VAMPIRE_BAT = ENTITY_TYPES.registerEntityType("vampire_bat", VampireBat::new, MobCategory.MONSTER,
            builder -> builder.sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(5).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<HeadlessHorseman>> HEADLESS_HORSEMAN = ENTITY_TYPES.registerEntityType("headless_horseman", HeadlessHorseman::new, MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0F).ridingOffset(-0.7F).clientTrackingRange(10).notInPeaceful());

    // Spawned by WerewolfSpawner on full-moon nights, not by biome
    public static final DeferredHolder<EntityType<?>, EntityType<Werewolf>> WEREWOLF = ENTITY_TYPES.registerEntityType("werewolf", Werewolf::new, MobCategory.MONSTER,
            builder -> builder.sized(0.7F, 2.1F).eyeHeight(1.85F).clientTrackingRange(10).notInPeaceful());

    // Spawned by GhostSpawner inside mineshafts and abandoned villages, not by biome
    public static final DeferredHolder<EntityType<?>, EntityType<Ghost>> GHOST = ENTITY_TYPES.registerEntityType("ghost", Ghost::new, MobCategory.MONSTER,
            builder -> builder.sized(0.8F, 1.6F).eyeHeight(1.3F).clientTrackingRange(8).fireImmune().notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<SilverArrow>> SILVER_ARROW = ENTITY_TYPES.registerEntityType("silver_arrow", SilverArrow::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));

    // Blood skill projectiles
    public static final DeferredHolder<EntityType<?>, EntityType<BloodSpear>> BLOOD_SPEAR = ENTITY_TYPES.registerEntityType("blood_spear", BloodSpear::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    public static final DeferredHolder<EntityType<?>, EntityType<BloodOrb>> BLOOD_ORB = ENTITY_TYPES.registerEntityType("blood_orb", BloodOrb::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(1.5F, 1.5F).clientTrackingRange(6).updateInterval(2));

    // The Master Vampire's bat swarm attack. Short-lived and never saved.
    // The Boiling Hemorrhage skill's patch of boiling blood. Invisible itself: it's drawn by its particles.
    public static final DeferredHolder<EntityType<?>, EntityType<HemorrhageZone>> HEMORRHAGE_ZONE = ENTITY_TYPES.registerEntityType("hemorrhage_zone", HemorrhageZone::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20).fireImmune());

    public static final DeferredHolder<EntityType<?>, EntityType<SwarmBat>> SWARM_BAT =ENTITY_TYPES.registerEntityType("swarm_bat", SwarmBat::new, MobCategory.MISC,
            builder -> builder.sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(8).noSave().fireImmune());

    public static final DeferredHolder<EntityType<?>, EntityType<MasterVampire>> MASTER_VAMPIRE = ENTITY_TYPES.registerEntityType("master_vampire", MasterVampire::new, MobCategory.MONSTER,
            builder -> builder.sized(0.7F, 2.2F).eyeHeight(2.0F).clientTrackingRange(10).notInPeaceful());

    private ModEntities() {}

    /// Master vampires only rise on their own during a blood moon (spawn eggs and commands still work any time)
    public static boolean checkMasterVampireSpawnRules(EntityType<? extends Monster> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        boolean natural = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
        return (!natural || BloodMoon.isActive(level.getLevel())) && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @SubscribeEvent
    static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HUNTER.get(), Hunter.createAttributes().build());
        event.put(MASTER_HUNTER.get(), MasterHunter.createAttributes().build());
        event.put(VAMPIRE.get(), Vampire.createAttributes().build());
        event.put(MASTER_VAMPIRE.get(), MasterVampire.createAttributes().build());
        event.put(VAMPIRE_BAT.get(), VampireBat.createAttributes().build());
        event.put(SWARM_BAT.get(), Bat.createAttributes().build());
        event.put(HEADLESS_HORSEMAN.get(), HeadlessHorseman.createAttributes().build());
        event.put(GHOST.get(), Ghost.createAttributes().build());
        event.put(WEREWOLF.get(), Werewolf.createAttributes().build());
    }

    @SubscribeEvent
    static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // Standard hostile-mob rules: only in darkness, so in caves and on the surface at night.
        // Where they spawn is set in data/crimsoncovenant/neoforge/biome_modifier/vampire_spawns.json
        event.register(VAMPIRE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(MASTER_VAMPIRE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEntities::checkMasterVampireSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // Forests only, see data/crimsoncovenant/neoforge/biome_modifier/headless_horseman_spawns.json
        event.register(HEADLESS_HORSEMAN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
