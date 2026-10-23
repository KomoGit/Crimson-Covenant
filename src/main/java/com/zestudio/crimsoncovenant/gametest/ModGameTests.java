package com.zestudio.crimsoncovenant.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// Registers the mod's in-game automated tests. Run them all with `./gradlew runGameTestServer`,
/// or in a dev world with `/test runall`. Every test runs in the 9x4x9 stone-floored arena structure.
public final class ModGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, CrimsonCovenant.MODID);
    private static final List<TestEntry> TESTS = new ArrayList<>();
    private static final int DEFAULT_MAX_TICKS = 200;

    private record TestEntry(DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks, Batch batch) {}

    private enum Batch { SHARED, ISOLATED, WITH_PLAYER }

    static {
        test("hunter_party_has_one_leader", HunterGameTests::partyHasOneLeader);
        test("hunter_party_spawns_from_above_ground", HunterGameTests::partySpawnsFromAboveGround);
        isolatedTest("hunter_spawner_knows_when_its_night", HunterGameTests::spawnerKnowsWhenItsNight);
        test("hunter_targets_undead", HunterGameTests::targetsUndead);
        test("hunter_ignores_animals", HunterGameTests::ignoresAnimals);
        test("hunter_crossbow_shoots", HunterGameTests::crossbowShoots);
        test("hunter_targets_vampire", HunterGameTests::targetsVampire);
        test("hunter_drops_token_of_blood", HunterGameTests::dropsTokenOfBloodWhenKilledByPlayer);
        test("master_hunter_leads_with_shield_and_smite_axe", HunterGameTests::masterHunterLeadsWithShieldAndSmiteAxe);
        test("master_hunter_blocks_from_the_front", HunterGameTests::masterHunterBlocksFromTheFront);
        test("master_hunter_throws_holy_water", HunterGameTests::masterHunterThrowsHolyWater);

        test("undead_attack_hunters", HunterGameTests::undeadAttackHunters);
        test("hunter_becomes_master_by_killing_master_vampire", HunterGameTests::hunterBecomesMasterByKillingMasterVampire);
        test("master_hunter_drinks_regeneration_after_battle", HunterGameTests::masterHunterDrinksRegenerationAfterBattle, 400);
        test("master_hunter_throws_lingering_at_groups", HunterGameTests::masterHunterThrowsLingeringAtGroups, 400);

        test("holy_water_burns_only_undead", VampireGameTests::holyWaterBurnsOnlyUndead);
        test("holy_water_slows_and_strips_resistance", VampireGameTests::holyWaterSlowsAndStripsResistance);
        test("lingering_holy_water_burns_undead", VampireGameTests::lingeringHolyWaterBurnsUndead);
        test("vampire_becomes_master_by_killing_master_hunter", VampireGameTests::vampireBecomesMasterByKillingMasterHunter);
        test("master_vampire_blood_armor", VampireGameTests::masterVampireBloodArmor);
        test("master_vampire_bat_swarm", VampireGameTests::masterVampireBatSwarm, 300);
        test("headless_horseman_rides_with_golden_spear", VampireGameTests::headlessHorsemanRidesWithGoldenSpear);
        test("headless_horseman_hunts_hunters", VampireGameTests::headlessHorsemanHuntsHunters);
        test("holy_flame_looks_like_fire", WorldGameTests::holyFlameLooksLikeFire);
        test("horseman_dies_with_horse", WorldGameTests::horsemanDiesWithHorse);
        test("vampire_seeks_shelter_at_dawn", WorldGameTests::vampireSeeksShelterAtDawn, 400);
        test("master_vampire_cant_drain_under_holy_flame", WorldGameTests::masterVampireCantDrainUnderHolyFlame);
        test("blood_moon_timing", WorldGameTests::bloodMoonTiming);
        isolatedTest("blood_moon_gives_vampires_bloodlust", WorldGameTests::bloodMoonGivesVampiresBloodlust);
        test("silver_arrows_hurt_undead_more", WorldGameTests::silverArrowsHurtUndeadMore);
        test("silver_is_craftable_and_brewable", WorldGameTests::silverIsCraftableAndBrewable);
        test("silver_ore_generates_like_iron", WorldGameTests::silverOreGeneratesLikeIron);
        test("gravestones_face_the_player", WorldGameTests::gravestonesFaceThePlayer);
        test("villages_keep_to_one_faction", WorldGameTests::villagesKeepToOneFaction);
        test("werewolf_hunts_villagers", WorldGameTests::werewolfHuntsVillagers);
        test("werewolf_flees_master_hunters", WorldGameTests::werewolfFleesMasterHunters);
        test("werewolf_vanishes_with_the_moon", WorldGameTests::werewolfVanishesWithTheMoon);
        test("hunters_hunt_werewolves", WorldGameTests::huntersHuntWerewolves);
        test("ghost_only_hurt_by_holy_water", VampireGameTests::ghostOnlyHurtByHolyWater);
        test("ghost_passes_through_walls", VampireGameTests::ghostPassesThroughWalls);
        test("ghost_gives_nausea", VampireGameTests::ghostGivesNausea);
        test("holy_flame_survives_water", VampireGameTests::holyFlameSurvivesWater);
        test("vampire_bat_attacks_prey", VampireGameTests::vampireBatAttacksPrey);
        test("vampire_drops_token_of_blood", VampireGameTests::dropsTokenOfBloodWhenKilledByPlayer);

        test("vampire_heals_by_killing", VampireGameTests::healsByKilling);
        test("vampire_has_resistance", VampireGameTests::hasResistance);
        test("vampire_ignores_animals_when_healthy", VampireGameTests::ignoresAnimalsWhenHealthy);
        test("vampire_attacks_villager", VampireGameTests::attacksVillager);
        test("vampire_breaks_doors", VampireGameTests::breaksDoors, 600);
        // Not registered yet: test worlds are at noon, and vampires won't roam through sunlight, so this one
        // needs a night-time test environment first. VampireGameTests::finishesBreakingDoorWithoutTarget
        test("vampire_kill_infects_player", VampireGameTests::killInfectsPlayer);
        test("dying_infected_turns_player", VampireGameTests::dyingInfectedTurnsPlayer);
        test("master_vampire_drains_life_nearby", VampireGameTests::masterDrainsLifeNearby);
        test("master_vampire_summons_minions_when_low", VampireGameTests::masterSummonsMinionsWhenLow);
        test("master_vampire_spares_its_minions", VampireGameTests::masterSparesItsMinions);
        test("vampire_spawns_in_overworld_biomes", VampireGameTests::spawnsInOverworldBiomes);
        test("infection_turns_player_into_vampire", VampireGameTests::infectionTurnsPlayer);
        test("infection_survives_milk", VampireGameTests::infectionSurvivesMilk);

        test("kills_fill_the_blood_bar", SkillGameTests::killsFillTheBloodBar);
        test("blood_refills_slowly", SkillGameTests::bloodRefillsSlowly);
        test("skills_cost_blood_and_need_a_vampire", SkillGameTests::skillsCostBloodAndNeedAVampire);
        test("dash_launches_the_player", SkillGameTests::dashLaunchesThePlayer);
        test("armor_of_ancient_matches_diamond", SkillGameTests::armorOfAncientMatchesDiamond);
        test("daywalker_lasts_a_day", SkillGameTests::daywalkerLastsADay);
        test("holy_water_dispels_skills", SkillGameTests::holyWaterDispelsSkills);
        test("blood_spear_refunds_on_kill", SkillGameTests::bloodSpearRefundsOnKill);
        test("blood_spear_is_thrown_like_a_trident", SkillGameTests::bloodSpearIsThrownLikeATrident);
        test("blood_spear_drives_and_slams", SkillGameTests::bloodSpearDrivesAndSlams);
        test("blood_parade_drains_hostiles", SkillGameTests::bloodParadeDrainsHostiles, 400);
        test("blood_parade_bounces_off_walls", SkillGameTests::bloodParadeBouncesOffWalls);
        test("sanguine_table_upgrades_skills", SkillGameTests::sanguineTableUpgradesSkills);
        test("crimson_lightning_strikes_spear_victims", SkillGameTests::crimsonLightningStrikesSpearVictims);
        test("spreading_boil_grows_with_deaths", SkillGameTests::spreadingBoilGrowsWithDeaths);
        playerTest("enthralled_creatures_serve", SkillGameTests::enthralledCreaturesServe, DEFAULT_MAX_TICKS);
        test("plain_possession_leaves_no_thrall", SkillGameTests::plainPossessionLeavesNoThrall);
        test("blood_wings_fly_longer_and_dive", SkillGameTests::bloodWingsFlyLongerAndDive);
        playerTest("afterimage_strike_slashes_through", SkillGameTests::afterimageStrikeSlashesThrough, DEFAULT_MAX_TICKS);
        test("thorned_shell_hurts_attackers", SkillGameTests::thornedShellHurtsAttackers);
        playerTest("leeching_swarm_brings_blood", SkillGameTests::leechingSwarmBringsBlood, DEFAULT_MAX_TICKS);
        test("panic_spreads_from_the_fleeing", SkillGameTests::panicSpreadsFromTheFleeing);
        test("final_feast_heals_the_next_blow", SkillGameTests::finalFeastHealsTheNextBlow);
        test("returning_step_leaves_an_anchor", SkillGameTests::returningStepLeavesAnAnchor);
        test("thirsting_blade_feeds_on_kills", SkillGameTests::thirstingBladeFeedsOnKills);
        test("grave_march_raises_the_fallen", SkillGameTests::graveMarchRaisesTheFallen);
        test("marked_prey_takes_more_damage", SkillGameTests::markedPreyTakesMoreDamage);
        test("skills_stay_through_death", SkillGameTests::skillsStayThroughDeath);
        test("translator_turns_tomes_into_skills", SkillGameTests::translatorTurnsTomesIntoSkills);
        test("broken_translator_gives_the_tome_back", SkillGameTests::brokenTranslatorGivesTheTomeBack);
        test("translator_recipe_uses_tokens_of_blood", SkillGameTests::translatorRecipeUsesTokensOfBlood);
        test("old_hunter_trades_for_silver_coins", SkillGameTests::oldHunterTradesForSilverCoins);
        test("ancient_butler_sells_tomes", SkillGameTests::ancientButlerSellsTomes);
        test("tomes_turn_up_in_chests", SkillGameTests::tomesTurnUpInChests);
        test("skills_are_worn_not_held", SkillGameTests::skillsAreWornNotHeld);
        test("vampires_dont_hunger", SkillGameTests::vampiresDontHunger);
        test("defy_death_costs_blood_per_blow", SkillGameTests::defyDeathCostsBloodPerBlow);
        test("shadow_step_returns_to_the_anchor", SkillGameTests::shadowStepReturnsToTheAnchor);
        test("vampires_only_drink_the_living", SkillGameTests::vampiresOnlyDrinkTheLiving);
        playerTest("bat_swarm_hits_whats_ahead", SkillGameTests::batSwarmHitsWhatsAhead, DEFAULT_MAX_TICKS);
        playerTest("night_of_terror_sends_mobs_running", SkillGameTests::nightOfTerrorSendsMobsRunning, DEFAULT_MAX_TICKS);
        test("possess_becomes_the_creature", SkillGameTests::possessBecomesTheCreature);
        test("possessed_creeper_explodes", SkillGameTests::possessedCreeperExplodes);
        test("vampires_defend_vampire_players", SkillGameTests::vampiresDefendVampirePlayers);
        test("ephemeral_sword_conjures_and_fades", SkillGameTests::ephemeralSwordConjuresAndFades);
        test("possessed_skeleton_draws_its_bow", SkillGameTests::possessedSkeletonDrawsItsBow);
        test("masters_hunt_vampires_too", SkillGameTests::mastersHuntVampiresToo);
        test("masters_only_rise_under_a_blood_moon", SkillGameTests::mastersOnlyRiseUnderABloodMoon);
        test("master_vampires_drop_rare_tomes", SkillGameTests::masterVampiresDropRareTomes);
        test("boiling_hemorrhage_scalds_and_shares", SkillGameTests::boilingHemorrhageScaldsAndShares);
        test("death_march_walks_straight_on", SkillGameTests::deathMarchWalksStraightOn);
        test("blood_bottles_refill_vampires", SkillGameTests::bloodBottlesRefillVampires);
        test("black_blood_hurts_attackers_back", SkillGameTests::blackBloodHurtsAttackersBack);
        test("hunters_drink_black_blood_for_a_fight", SkillGameTests::huntersDrinkBlackBloodForAFight);
        test("black_blood_burns_undead_biters", SkillGameTests::blackBloodBurnsUndeadBiters);
        test("vampires_refuse_black_blood", SkillGameTests::vampiresRefuseBlackBlood);
        test("stalkers_gaze_lasts_and_washes_off", SkillGameTests::stalkersGazeLastsAndWashesOff);
        test("garlic_braid_repels_undead", SkillGameTests::garlicBraidRepelsUndead);
        test("garlic_weakens_undead", SkillGameTests::garlicWeakensUndead);
        test("garlic_reeks_to_vampire_players", SkillGameTests::garlicReeksToVampirePlayers);
        test("garlic_grows_and_brews", SkillGameTests::garlicGrowsAndBrews);
        test("ephemeral_passes_through_walls", SkillGameTests::ephemeralPassesThroughWalls);
        test("ghosts_cant_be_hit", SkillGameTests::ghostsCantBeHit);
        test("silvering_at_the_smithing_table", SilverGameTests::silveringAtTheSmithingTable);
        test("silvered_weapons_wound_undead", SilverGameTests::silveredWeaponsWoundUndead);
        test("silver_wound_stops_blood_coming_back", SilverGameTests::silverWoundStopsBloodComingBack);
        test("old_hunter_refuses_vampires", SilverGameTests::oldHunterRefusesVampires);
        test("vampire_bats_are_undead_cave_dwellers", SilverGameTests::vampireBatsAreUndeadCaveDwellers);
        test("orphaned_minions_crumble", SkillGameTests::orphanedMinionsCrumble);
        test("graveyard_is_built", GraveyardGameTests::graveyardIsBuilt);
        test("graveyards_are_haunted", GraveyardGameTests::graveyardsAreHaunted);
        test("bear_trap_snaps_and_holds", TrapGameTests::bearTrapSnapsAndHolds);
        test("silver_spikes_stab", TrapGameTests::silverSpikesStab);
        test("silver_net_trap_catches_passers_by", TrapGameTests::silverNetTrapCatchesPassersBy);
        test("hunters_shoot_silver_arrows", TrapGameTests::huntersShootSilverArrows);
        test("master_vampires_tear_free_of_traps", TrapGameTests::masterVampiresTearFreeOfTraps);
        test("hunters_free_trapped_hunters", TrapGameTests::huntersFreeTrappedHunters);
        test("hunters_throw_garlic_at_undead", TrapGameTests::huntersThrowGarlicAtUndead);
        test("master_vampires_cast_blood_skills", TrapGameTests::masterVampiresCastBloodSkills);
        test("chemists_throw_the_right_potions", TrapGameTests::chemistsThrowTheRightPotions);
        test("chemists_heal_hurt_hunters", TrapGameTests::chemistsHealHurtHunters);
        test("silver_splash_scatters_undead", TrapGameTests::silverSplashScattersUndead);
    }

    private ModGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> function) {
        test(name, function, DEFAULT_MAX_TICKS);
    }

    private static void test(String name, Consumer<GameTestHelper> function, int maxTicks) {
        TESTS.add(new TestEntry(TEST_FUNCTIONS.register(name, () -> function), maxTicks, Batch.SHARED));
    }

    /// For tests that change world-wide state (the clock, the blood moon): they run in a batch of their own, so
    /// their side effects can't leak into other tests running at the same time
    private static void isolatedTest(String name, Consumer<GameTestHelper> function) {
        TESTS.add(new TestEntry(TEST_FUNCTIONS.register(name, () -> function), DEFAULT_MAX_TICKS, Batch.ISOLATED));
    }

    /// For tests that keep a (mock) player in the world for a while. While any player exists, the game despawns
    /// monsters far from it, which would pull mobs out of other tests, so these get a batch of their own too.
    private static void playerTest(String name, Consumer<GameTestHelper> function, int maxTicks) {
        TESTS.add(new TestEntry(TEST_FUNCTIONS.register(name, () -> function), maxTicks, Batch.WITH_PLAYER));
    }

    public static void register(IEventBus modEventBus) {
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(ModGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> shared = event.registerEnvironment(CrimsonCovenant.id("default"));
        // Tests are batched by environment, so an environment of their own keeps isolated tests apart
        Holder<TestEnvironmentDefinition<?>> isolated = event.registerEnvironment(CrimsonCovenant.id("isolated"));
        Holder<TestEnvironmentDefinition<?>> withPlayer = event.registerEnvironment(CrimsonCovenant.id("with_player"));
        for (TestEntry test : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> key = test.function().getKey();
            Holder<TestEnvironmentDefinition<?>> environment = switch (test.batch()) {
                case SHARED -> shared;
                case ISOLATED -> isolated;
                case WITH_PLAYER -> withPlayer;
            };
            event.registerTest(key.identifier(), new FunctionGameTestInstance(key,
                    new TestData<>(environment, CrimsonCovenant.id("arena"), test.maxTicks(), 0, true)));
        }
    }
}
