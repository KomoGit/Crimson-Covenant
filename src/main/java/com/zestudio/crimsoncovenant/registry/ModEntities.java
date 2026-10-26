package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.BloodBat;
import com.zestudio.crimsoncovenant.entity.BloodOrb;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.CrimsonLightning;
import com.zestudio.crimsoncovenant.entity.Gargoyle;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.entity.HemorrhageZone;
import com.zestudio.crimsoncovenant.entity.GhostSteed;
import com.zestudio.crimsoncovenant.entity.HalloweenSpirit;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;
import com.zestudio.crimsoncovenant.entity.PumpkinBomb;
import com.zestudio.crimsoncovenant.entity.SpectralHorseman;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.HunterChemist;
import com.zestudio.crimsoncovenant.entity.MasterHunter;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.SilverArrow;
import com.zestudio.crimsoncovenant.entity.SwarmBat;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.VampireBat;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.entity.MiasmaCloud;
import com.zestudio.crimsoncovenant.entity.PlagueDoctor;
import com.zestudio.crimsoncovenant.entity.SilverDustCloud;
import com.zestudio.crimsoncovenant.entity.ThroneSeat;
import com.zestudio.crimsoncovenant.entity.SpiritHand;
import com.zestudio.crimsoncovenant.entity.SpiritWyrms;
import com.zestudio.crimsoncovenant.entity.HauntedObject;
import com.zestudio.crimsoncovenant.entity.WhipLash;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
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

    /// Throws potions instead of fighting hand to hand. Comes along with hunter parties.
    public static final DeferredHolder<EntityType<?>, EntityType<HunterChemist>> HUNTER_CHEMIST = ENTITY_TYPES.registerEntityType("hunter_chemist", HunterChemist::new, MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8));

    public static final DeferredHolder<EntityType<?>, EntityType<MasterHunter>> MASTER_HUNTER = ENTITY_TYPES.registerEntityType("master_hunter", MasterHunter::new, MobCategory.MONSTER,
            builder -> builder.sized(0.65F, 2.0F).eyeHeight(1.8F).clientTrackingRange(10));

    public static final DeferredHolder<EntityType<?>, EntityType<Vampire>> VAMPIRE = ENTITY_TYPES.registerEntityType("vampire", Vampire::new, MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<VampireBat>> VAMPIRE_BAT = ENTITY_TYPES.registerEntityType("vampire_bat", VampireBat::new, MobCategory.MONSTER,
            builder -> builder.sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(5).notInPeaceful());

    /// Vermin of the villages and woods
    public static final DeferredHolder<EntityType<?>, EntityType<Rat>> RAT = ENTITY_TYPES.registerEntityType("rat", Rat::new, MobCategory.CREATURE,
            builder -> builder.sized(0.5F, 0.35F).eyeHeight(0.25F).clientTrackingRange(8));
    /// The healer who comes when the Miasma settles over a village
    public static final DeferredHolder<EntityType<?>, EntityType<PlagueDoctor>> PLAGUE_DOCTOR = ENTITY_TYPES.registerEntityType("plague_doctor", PlagueDoctor::new, MobCategory.CREATURE,
            builder -> builder.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
    /// The green fog over a rat-infested village. Invisible itself: it's drawn by its particles.
    public static final DeferredHolder<EntityType<?>, EntityType<MiasmaCloud>> MIASMA_CLOUD = ENTITY_TYPES.registerEntityType("miasma_cloud", MiasmaCloud::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).fireImmune());

    /// Revenant's Grip: the ghostly hand that fights beside a hunter
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritHand>> SPIRIT_HAND = ENTITY_TYPES.registerEntityType("spirit_hand", SpiritHand::new, MobCategory.MISC,
            builder -> builder.noLootTable().noSummon().sized(0.8F, 0.8F).clientTrackingRange(10).updateInterval(1).fireImmune());
    /// The Wrath of the Twin Wyrms: two spirit wyrms tearing along a line
    /// A whip's lash cracking out: only a sight and a sound
    public static final DeferredHolder<EntityType<?>, EntityType<WhipLash>> WHIP_LASH = ENTITY_TYPES.registerEntityType("whip_lash", WhipLash::new, MobCategory.MISC,
            builder -> builder.noLootTable().noSummon().noSave().sized(0.1F, 0.1F).clientTrackingRange(8).updateInterval(1).fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritWyrms>> SPIRIT_WYRMS = ENTITY_TYPES.registerEntityType("spirit_wyrms", SpiritWyrms::new, MobCategory.MISC,
            builder -> builder.noLootTable().noSummon().sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2).fireImmune());

    /// The unseen seat on a Blood Throne
    public static final DeferredHolder<EntityType<?>, EntityType<ThroneSeat>> THRONE_SEAT = ENTITY_TYPES.registerEntityType("throne_seat", ThroneSeat::new, MobCategory.MISC,
            builder -> builder.noLootTable().noSummon().sized(0.25F, 0.1F).clientTrackingRange(10).updateInterval(20).fireImmune());

    public static final DeferredHolder<EntityType<?>, EntityType<SilverDustCloud>> SILVER_DUST_CLOUD = ENTITY_TYPES.registerEntityType("silver_dust_cloud", SilverDustCloud::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).fireImmune());

    public static final DeferredHolder<EntityType<?>, EntityType<HeadlessHorseman>> HEADLESS_HORSEMAN = ENTITY_TYPES.registerEntityType("headless_horseman", HeadlessHorseman::new, MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0F).ridingOffset(-0.7F).clientTrackingRange(10).notInPeaceful());
    /// The Horseman's ghost, riding by night round his head on a pike (see SpectralHorseman)
    public static final DeferredHolder<EntityType<?>, EntityType<SpectralHorseman>> SPECTRAL_HORSEMAN = ENTITY_TYPES.registerEntityType("spectral_horseman", SpectralHorseman::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0F).ridingOffset(-0.7F).clientTrackingRange(10).fireImmune());
    /// A horse's ghost: the Spectral Horseman's mount, or the Headless Horseman's horse risen (see GhostSteed)
    public static final DeferredHolder<EntityType<?>, EntityType<GhostSteed>> GHOST_STEED = ENTITY_TYPES.registerEntityType("ghost_steed", GhostSteed::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.31875F).clientTrackingRange(10).fireImmune());
    /// What rises from the fallen Headless Horseman (see HalloweenSpirit)
    public static final DeferredHolder<EntityType<?>, EntityType<HalloweenSpirit>> HALLOWEEN_SPIRIT = ENTITY_TYPES.registerEntityType("halloween_spirit", HalloweenSpirit::new, MobCategory.MONSTER,
            builder -> builder.sized(0.8F, 2.4F).eyeHeight(2.1F).clientTrackingRange(10).fireImmune());
    /// The Headless Horseman's thrown jack o'lantern (see PumpkinBomb)
    public static final DeferredHolder<EntityType<?>, EntityType<PumpkinBomb>> PUMPKIN_BOMB = ENTITY_TYPES.registerEntityType("pumpkin_bomb", PumpkinBomb::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(5));

    // Spawned by WerewolfSpawner on full-moon nights, not by biome
    public static final DeferredHolder<EntityType<?>, EntityType<Werewolf>> WEREWOLF = ENTITY_TYPES.registerEntityType("werewolf", Werewolf::new, MobCategory.MONSTER,
            builder -> builder.sized(0.7F, 2.1F).eyeHeight(1.85F).clientTrackingRange(10).notInPeaceful());

    // Spawned by GhostSpawner inside mineshafts and abandoned villages, and risen from graveyards' gravestones, not by biome
    public static final DeferredHolder<EntityType<?>, EntityType<Ghost>> GHOST = ENTITY_TYPES.registerEntityType("ghost", Ghost::new, MobCategory.MONSTER,
            builder -> builder.sized(0.8F, 1.6F).eyeHeight(1.3F).clientTrackingRange(8).fireImmune().notInPeaceful());
    /// Something a ghost has slipped into, floating about
    public static final DeferredHolder<EntityType<?>, EntityType<HauntedObject>> HAUNTED_OBJECT = ENTITY_TYPES.registerEntityType("haunted_object", HauntedObject::new,
            MobCategory.MISC, builder -> builder.noLootTable().noSummon().sized(0.9F, 0.9F).clientTrackingRange(8).fireImmune());

    public static final DeferredHolder<EntityType<?>, EntityType<SilverArrow>> SILVER_ARROW = ENTITY_TYPES.registerEntityType("silver_arrow", SilverArrow::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));

    // Blood skill projectiles
    public static final DeferredHolder<EntityType<?>, EntityType<BloodSpear>> BLOOD_SPEAR = ENTITY_TYPES.registerEntityType("blood_spear", BloodSpear::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    public static final DeferredHolder<EntityType<?>, EntityType<BloodOrb>> BLOOD_ORB = ENTITY_TYPES.registerEntityType("blood_orb", BloodOrb::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(1.5F, 1.5F).clientTrackingRange(6).updateInterval(2));
    /// A Blood Parade kill's bat of blood, chaining from body to body (see BloodBat)
    public static final DeferredHolder<EntityType<?>, EntityType<BloodBat>> BLOOD_BAT = ENTITY_TYPES.registerEntityType("blood_bat", BloodBat::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(1));

    // The Master Vampire's bat swarm attack. Short-lived and never saved.
    // The Boiling Hemorrhage skill's patch of boiling blood. Invisible itself: it's drawn by its particles.
    /// The upgraded Blood Spear's red lightning: lives a few ticks, drawn by CrimsonLightningRenderer
    public static final DeferredHolder<EntityType<?>, EntityType<CrimsonLightning>> CRIMSON_LIGHTNING = ENTITY_TYPES.registerEntityType("crimson_lightning", CrimsonLightning::new, MobCategory.MISC,
            builder -> builder.noLootTable().noSave().sized(0.0F, 0.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE).fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<HemorrhageZone>> HEMORRHAGE_ZONE = ENTITY_TYPES.registerEntityType("hemorrhage_zone", HemorrhageZone::new, MobCategory.MISC,
            builder -> builder.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20).fireImmune());

    public static final DeferredHolder<EntityType<?>, EntityType<SwarmBat>> SWARM_BAT =ENTITY_TYPES.registerEntityType("swarm_bat", SwarmBat::new, MobCategory.MISC,
            builder -> builder.sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(8).noSave().fireImmune());

    public static final DeferredHolder<EntityType<?>, EntityType<MasterVampire>> MASTER_VAMPIRE = ENTITY_TYPES.registerEntityType("master_vampire", MasterVampire::new, MobCategory.MONSTER,
            builder -> builder.sized(0.7F, 2.2F).eyeHeight(2.0F).clientTrackingRange(10).notInPeaceful());

    // Stone guardians, carved and woken by players (see item.GargoyleStatueItem), never spawned by the world.
    // The hunters' is made with silver, the vampires' with blood.
    public static final DeferredHolder<EntityType<?>, EntityType<Gargoyle>> CATHEDRAL_GARGOYLE = ENTITY_TYPES.registerEntityType("cathedral_gargoyle",
            (type, level) -> new Gargoyle(type, level, Gargoyle.Side.HUNTER), MobCategory.MISC,
            builder -> builder.sized(0.7F, 1.9F).eyeHeight(1.6F).clientTrackingRange(10));
    public static final DeferredHolder<EntityType<?>, EntityType<Gargoyle>> BLOOD_GARGOYLE = ENTITY_TYPES.registerEntityType("blood_gargoyle",
            (type, level) -> new Gargoyle(type, level, Gargoyle.Side.VAMPIRE), MobCategory.MISC,
            builder -> builder.sized(0.7F, 1.9F).eyeHeight(1.6F).clientTrackingRange(10));

    private ModEntities() {}

    /// Master vampires only rise on their own during a blood moon (spawn eggs and commands still work any time)
    public static boolean checkMasterVampireSpawnRules(EntityType<? extends Monster> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        boolean natural = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
        return (!natural || BloodMoon.isActive(level.getLevel())) && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    /// Vampire bats roost in caves: underground, below sea level and in the dark, on the same stone vanilla bats
    /// spawn on
    public static boolean checkVampireBatSpawnRules(EntityType<VampireBat> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return Mob.checkMobSpawnRules(type, level, reason, pos, random);
        }
        return pos.getY() < level.getSeaLevel() && pos.getY() < level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).getY()
                && level.getBlockState(pos.below()).is(BlockTags.BATS_SPAWNABLE_ON) && Monster.isDarkEnoughToSpawn(level, pos, random)
                && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    /// Rats turn up on the forest floor, light or dark
    public static boolean checkRatSpawnRules(EntityType<Rat> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.DIRT) && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    @SubscribeEvent
    static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HUNTER.get(), Hunter.createAttributes().build());
        event.put(HUNTER_CHEMIST.get(), HunterChemist.createAttributes().build());
        event.put(MASTER_HUNTER.get(), MasterHunter.createAttributes().build());
        event.put(VAMPIRE.get(), Vampire.createAttributes().build());
        event.put(MASTER_VAMPIRE.get(), MasterVampire.createAttributes().build());
        event.put(VAMPIRE_BAT.get(), VampireBat.createAttributes().build());
        event.put(SWARM_BAT.get(), Bat.createAttributes().build());
        event.put(HEADLESS_HORSEMAN.get(), HeadlessHorseman.createAttributes().build());
        event.put(SPECTRAL_HORSEMAN.get(), HeadlessHorseman.createAttributes().build());
        event.put(GHOST_STEED.get(), ZombieHorse.createAttributes().build());
        event.put(HALLOWEEN_SPIRIT.get(), HalloweenSpirit.createAttributes().build());
        event.put(GHOST.get(), Ghost.createAttributes().build());
        event.put(HAUNTED_OBJECT.get(), HauntedObject.createAttributes().build());
        event.put(RAT.get(), Rat.createAttributes().build());
        event.put(PLAGUE_DOCTOR.get(), PlagueDoctor.createAttributes().build());
        event.put(WEREWOLF.get(), Werewolf.createAttributes().build());
        event.put(CATHEDRAL_GARGOYLE.get(), Gargoyle.createAttributes().build());
        event.put(BLOOD_GARGOYLE.get(), Gargoyle.createAttributes().build());
    }

    @SubscribeEvent
    static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // Standard hostile-mob rules: only in darkness, so in caves and on the surface at night.
        // Where they spawn is set in data/crimsoncovenant/neoforge/biome_modifier/vampire_spawns.json
        event.register(VAMPIRE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(MASTER_VAMPIRE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEntities::checkMasterVampireSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // Caves only, see data/crimsoncovenant/neoforge/biome_modifier/vampire_spawns.json
        event.register(VAMPIRE_BAT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEntities::checkVampireBatSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // Forests, see data/crimsoncovenant/neoforge/biome_modifier/rat_spawns.json (villages get theirs from world.RatSpawner)
        event.register(RAT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEntities::checkRatSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
