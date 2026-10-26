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

    private enum Batch { SHARED, ISOLATED, WITH_PLAYER, CALM }

    static {
        test("hunter_party_has_one_leader", HunterGameTests::partyHasOneLeader);
        test("hunter_party_spawns_from_above_ground", HunterGameTests::partySpawnsFromAboveGround);
        isolatedTest("hunter_spawner_knows_when_its_night", HunterGameTests::spawnerKnowsWhenItsNight);
        test("hunter_targets_undead", HunterGameTests::targetsUndead);
        test("hunter_ignores_animals", HunterGameTests::ignoresAnimals);
        playerTest("hunter_rank_commands_set_ranks", HunterGameTests::rankCommandsSetRanks, DEFAULT_MAX_TICKS);
        test("hunter_crossbow_shoots", HunterGameTests::crossbowShoots);
        test("hunter_targets_vampire", HunterGameTests::targetsVampire);
        test("hunter_drops_token_of_blood", HunterGameTests::dropsTokenOfBloodWhenKilledByPlayer);
        test("master_hunter_drops_token_of_master", HunterGameTests::masterHunterDropsTokenOfMaster);
        test("join_the_hunters_with_a_silver_coin", HunterRankGameTests::joinTheHuntersWithASilverCoin);
        test("rise_through_the_hunter_ranks", HunterRankGameTests::riseThroughTheRanks);
        test("crafting_hunter_gear_needs_the_rank", HunterRankGameTests::craftingHunterGearNeedsTheRank);
        test("silvering_needs_a_slayer", HunterRankGameTests::silveringNeedsASlayer);
        test("brewing_hunter_potions_needs_the_rank", HunterRankGameTests::brewingHunterPotionsNeedsTheRank, 600);
        test("hunter_gear_ranks", HunterRankGameTests::gearRanks);
        test("master_hunter_leads_with_shield_and_smite_axe", HunterGameTests::masterHunterLeadsWithShieldAndSmiteAxe);
        test("master_hunter_blocks_from_the_front", HunterGameTests::masterHunterBlocksFromTheFront);
        test("master_hunter_throws_holy_water", HunterGameTests::masterHunterThrowsHolyWater);

        test("undead_attack_hunters", HunterGameTests::undeadAttackHunters);
        test("hunter_becomes_master_by_killing_master_vampire", HunterGameTests::hunterBecomesMasterByKillingMasterVampire);
        test("master_hunter_drinks_regeneration_after_battle", HunterGameTests::masterHunterDrinksRegenerationAfterBattle, 400);
        test("master_hunter_throws_lingering_at_groups", HunterGameTests::masterHunterThrowsLingeringAtGroups, 400);

        playerTest("vampires_grow_stronger_with_age", VampireGameTests::vampiresGrowStrongerWithAge, DEFAULT_MAX_TICKS);
        playerTest("old_vampires_make_a_last_stand", VampireGameTests::oldVampiresMakeALastStand, DEFAULT_MAX_TICKS);
        playerTest("ancients_are_kin_to_the_night", VampireGameTests::ancientsAreKinToTheNight, DEFAULT_MAX_TICKS);
        isolatedTest("blood_parade_only_under_a_blood_moon", VampireGameTests::bloodParadeOnlyUnderABloodMoon);
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
        test("horseman_drops_his_glowing_head", WorldGameTests::horsemanDropsHisGlowingHead);
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
        test("gargoyle_statues_wake_only_for_their_side", GargoyleGameTests::statuesWakeOnlyForTheirSide);
        test("gargoyle_makers_who_turn_are_makers_no_more", GargoyleGameTests::makersTurnedAreMakersNoMore);
        test("sunlight_turns_gargoyles_to_stone", GargoyleGameTests::sunlightTurnsGargoylesToStone);
        test("cathedral_gargoyle_hunts_the_undead_with_silver", GargoyleGameTests::cathedralGargoyleHuntsTheUndeadWithSilver);
        test("blood_gargoyle_drinks_from_the_living", GargoyleGameTests::bloodGargoyleDrinksFromTheLiving);
        test("hunters_and_vampires_fight_gargoyles", GargoyleGameTests::huntersAndVampiresFightGargoyles);
        test("ghost_only_hurt_by_holy_water", VampireGameTests::ghostOnlyHurtByHolyWater);
        test("ghost_passes_through_walls", VampireGameTests::ghostPassesThroughWalls);
        test("ghost_gives_nausea", VampireGameTests::ghostGivesNausea);
        test("holy_flame_survives_water", VampireGameTests::holyFlameSurvivesWater);
        test("vampire_bat_attacks_prey", VampireGameTests::vampireBatAttacksPrey);
        test("vampire_drops_token_of_blood", VampireGameTests::dropsTokenOfBloodWhenKilledByPlayer);
        test("master_vampire_drops_token_of_master", VampireGameTests::masterVampireDropsTokenOfMaster);

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
        test("older_vampires_hold_more_blood", SkillGameTests::olderVampiresHoldMoreBlood);
        test("skills_cost_blood_and_need_a_vampire", SkillGameTests::skillsCostBloodAndNeedAVampire);
        test("dash_launches_the_player", SkillGameTests::dashLaunchesThePlayer);
        test("armor_of_ancient_matches_diamond", SkillGameTests::armorOfAncientMatchesDiamond);
        test("daywalker_lasts_a_day", SkillGameTests::daywalkerLastsADay);
        test("holy_water_dispels_skills", SkillGameTests::holyWaterDispelsSkills);
        test("blood_spear_refunds_on_kill", SkillGameTests::bloodSpearRefundsOnKill);
        test("blood_spear_is_thrown_like_a_trident", SkillGameTests::bloodSpearIsThrownLikeATrident);
        test("blood_spear_drives_and_slams", SkillGameTests::bloodSpearDrivesAndSlams);
        test("blood_spear_pierces_through", SkillGameTests::bloodSpearPiercesThrough);
        playerTest("whips_reach_far_and_slam", SkillGameTests::whipsReachFarAndSlam, DEFAULT_MAX_TICKS);
        playerTest("whip_lash_lands_the_blow", SkillGameTests::whipLashLandsTheBlow, DEFAULT_MAX_TICKS);
        playerTest("whip_pulls_creatures_in", SkillGameTests::whipPullsCreaturesIn, DEFAULT_MAX_TICKS);
        test("blood_parade_drains_hostiles", SkillGameTests::bloodParadeDrainsHostiles, 400);
        test("blood_bats_chain_until_their_reach_runs_out", SkillGameTests::bloodBatsChainUntilTheirReachRunsOut, 200);
        test("blood_parade_bounces_off_walls", SkillGameTests::bloodParadeBouncesOffWalls);
        test("blood_altar_upgrades_skills", SkillGameTests::bloodAltarUpgradesSkills);
        test("crimson_lightning_strikes_spear_victims", SkillGameTests::crimsonLightningStrikesSpearVictims);
        test("spreading_boil_grows_with_deaths", SkillGameTests::spreadingBoilGrowsWithDeaths);
        playerTest("enthralled_creatures_serve", SkillGameTests::enthralledCreaturesServe, DEFAULT_MAX_TICKS);
        test("plain_possession_leaves_no_thrall", SkillGameTests::plainPossessionLeavesNoThrall);
        test("crimson_shroud_glides_on_blood_charges", SkillGameTests::crimsonShroudGlidesOnBloodCharges);
        test("vampire_skills_hit_hunters", SkillGameTests::vampireSkillsHitHunters);
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
        calmTest("translator_turns_tomes_into_skills", SkillGameTests::translatorTurnsTomesIntoSkills, 100);
        test("blood_skills_come_in_tiers", SkillGameTests::bloodSkillsComeInTiers);
        test("curseforge_lays_and_deepens_curses", CurseGameTests::curseforgeLaysAndDeepensCurses);
        test("a_ghost_turns_an_anvil_into_a_curseforge", CurseGameTests::aGhostTurnsAnAnvilIntoACurseforge);
        test("unleashing_asks_rank_and_time", CurseGameTests::unleashingAsksRankAndTime);
        test("shackles_and_gale_on_the_blow", CurseGameTests::shacklesAndGaleOnTheBlow);
        playerTest("the_banished_cant_hurt_or_heal", CurseGameTests::theBanishedCantHurtOrHeal, DEFAULT_MAX_TICKS);
        test("ravenous_maw_feeds_and_starves", CurseGameTests::ravenousMawFeedsAndStarves);
        test("gravecaller_raises_the_slain", CurseGameTests::gravecallerRaisesTheSlain);
        playerTest("revenants_grip_fights_alongside", CurseGameTests::revenantsGripFightsAlongside, DEFAULT_MAX_TICKS);
        playerTest("wraithseeker_arrows_find_their_mark", CurseGameTests::wraithseekerArrowsFindTheirMark, DEFAULT_MAX_TICKS);
        playerTest("wraithseeker_trick_shots_come_round", CurseGameTests::wraithseekerTrickShotsComeRound, DEFAULT_MAX_TICKS);
        playerTest("twin_wyrms_tear_through_walls", CurseGameTests::twinWyrmsTearThroughWalls, DEFAULT_MAX_TICKS);
        playerTest("haunted_weapons_cant_be_mended", CurseGameTests::hauntedWeaponsCantBeMended, DEFAULT_MAX_TICKS);
        playerTest("gravequake_throws_them_down", CurseGameTests::gravequakeThrowsThemDown, DEFAULT_MAX_TICKS);
        playerTest("wraiths_tether_hauls_you_in", CurseGameTests::wraithsTetherHaulsYouIn, DEFAULT_MAX_TICKS);
        playerTest("hauntings_cost_souls", CurseGameTests::hauntingsCostSouls, DEFAULT_MAX_TICKS);
        playerTest("the_fallen_can_be_struck_where_they_lie", CurseGameTests::theFallenCanBeStruckWhereTheyLie, DEFAULT_MAX_TICKS);
        test("broken_translator_gives_the_tome_back", SkillGameTests::brokenTranslatorGivesTheTomeBack);
        test("translator_recipe_uses_tokens_of_blood", SkillGameTests::translatorRecipeUsesTokensOfBlood);
        test("plague_doctor_trades_for_coins_and_emeralds", SkillGameTests::plagueDoctorTradesForCoinsAndEmeralds);
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
        test("vampires_refuse_garlic", SkillGameTests::vampiresRefuseGarlic);
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
        test("plague_doctor_refuses_vampires", SilverGameTests::plagueDoctorRefusesVampires);
        test("silvered_bottles_hold_ghosts", SilverGameTests::silveredBottlesHoldGhosts);
        test("silver_ore_kicks_up_dust", SilverGameTests::silverOreKicksUpDust);
        test("silver_is_minted_and_stored", SilverGameTests::silverIsMintedAndStored);
        isolatedTest("coffins_let_you_sleep_by_day", CoffinGameTests::coffinsLetYouSleepByDay);
        test("vampires_sleep_only_in_coffins", CoffinGameTests::vampiresSleepOnlyInCoffins);
        calmTest("kin_dont_keep_vampires_awake", CoffinGameTests::kinDontKeepVampiresAwake, 100);
        test("iron_maidens_take_villagers_captive", CoffinGameTests::ironMaidensTakeVillagersCaptive);
        test("witches_and_plague_doctors_can_be_taken_too", CoffinGameTests::witchesAndPlagueDoctorsCanBeTakenToo);
        test("captives_run_dry", CoffinGameTests::captivesRunDry);
        test("vampires_feed_on_captives_in_their_sleep", CoffinGameTests::vampiresFeedOnCaptivesInTheirSleep);
        test("vampire_bats_are_undead_cave_dwellers", SilverGameTests::vampireBatsAreUndeadCaveDwellers);
        test("orphaned_minions_crumble", SkillGameTests::orphanedMinionsCrumble);
        test("graveyard_is_built", GraveyardGameTests::graveyardIsBuilt);
        test("graveyards_are_haunted", GraveyardGameTests::graveyardsAreHaunted);
        playerTest("souls_unlock_skills", GuideGameTests::soulsUnlockSkills, DEFAULT_MAX_TICKS);
        playerTest("translations_fill_the_codex", GuideGameTests::translationsFillTheCodex, DEFAULT_MAX_TICKS);
        playerTest("locked_tomes_stay_hidden", GuideGameTests::lockedTomesStayHidden, DEFAULT_MAX_TICKS);
        playerTest("glass_bottles_torment_ghosts", GuideGameTests::glassBottlesTormentGhosts, DEFAULT_MAX_TICKS);
        playerTest("guide_books_chain", GuideGameTests::guideBooksChain, DEFAULT_MAX_TICKS);
        playerTest("guides_are_kept_on_death", GuideGameTests::guidesAreKeptOnDeath, DEFAULT_MAX_TICKS);
        playerTest("bestiary_fills_as_you_see", GuideGameTests::bestiaryFillsAsYouSee, DEFAULT_MAX_TICKS);
        test("graveyard_ghosts_rest_in_their_graves", GraveyardGameTests::graveyardGhostsRestInTheirGraves);
        test("ghosts_set_things_floating", GraveyardGameTests::ghostsSetThingsFloating);
        test("hunter_cabin_is_built", HunterCabinGameTests::cabinIsBuilt);
        test("rats_eat_crops_and_have_litters", RatGameTests::ratsEatCropsAndHaveLitters, 600);
        test("rats_eat_rotten_flesh", RatGameTests::ratsEatRottenFlesh);
        test("rats_eat_hay_and_pumpkins", RatGameTests::ratsEatHayAndPumpkins);
        test("rats_live_in_burrows_and_defend_them", RatGameTests::ratsLiveInBurrowsAndDefendThem);
        test("rats_find_somewhere_to_burrow", RatGameTests::burrowSpotsAreFound);
        test("miasma_over_an_infested_village", RatGameTests::miasmaOverAnInfestedVillage);
        test("rat_traps_kill_rats", RatGameTests::ratTrapsKillRats);
        test("rats_take_the_bait", RatGameTests::ratsTakeTheBait, 600);
        test("iron_golems_hunt_rats", RatGameTests::ironGolemsHuntRats);
        test("cats_catch_rats", RatGameTests::catsCatchRats, 400);
        test("rats_chew_before_they_eat", RatGameTests::ratsChewBeforeTheyEat, 400);
        test("young_rats_grow_and_live_with_parents", RatGameTests::youngRatsGrowFasterAndLiveWithTheirParents);
        test("farm_animals_cry_out_at_rats", RatGameTests::farmAnimalsCryOutAtRats);
        test("miasma_rises_from_burrows", RatGameTests::miasmaRisesFromBurrows);
        test("rats_gnaw_into_wood_from_the_side", RatGameTests::ratsGnawIntoWoodFromTheSide);
        test("cleanse_cures_the_sick", PlagueDoctorGameTests::cleanseCuresTheSick);
        test("thrown_cleanse_clears_the_miasma", PlagueDoctorGameTests::thrownCleanseClearsTheMiasma);
        test("drunk_cleanse_cures_the_curse", PlagueDoctorGameTests::drunkCleanseCuresTheCurse);
        test("vampires_dont_feel_the_cold", VampireGameTests::vampiresDontFeelTheCold);
        test("vampires_toggle_night_vision", VampireGameTests::vampiresToggleNightVision);
        test("plague_doctor_cures_for_emeralds", PlagueDoctorGameTests::doctorCuresForEmeralds);
        test("plague_doctor_sells_cleanse", PlagueDoctorGameTests::doctorSellsCleanse);
        test("plague_doctor_villagers_cure_too", PlagueDoctorGameTests::doctorVillagersCureToo);
        test("villages_build_doctors_houses", PlagueDoctorGameTests::villagesBuildDoctorsHouses);
        test("doctor_villagers_hang_garlic_over_doors", PlagueDoctorGameTests::doctorVillagersHangGarlicOverDoors);
        calmTest("wandering_doctor_hangs_garlic_over_doors", PlagueDoctorGameTests::wanderingDoctorHangsGarlicOverDoors, 600);
        test("blood_bottles_curse_the_living", VampireGameTests::bloodBottlesCurseTheLiving);
        calmTest("plague_doctor_traps_burrows", PlagueDoctorGameTests::doctorTrapsBurrows, 600);
        test("plague_doctor_wards_off_undead", PlagueDoctorGameTests::doctorWardsOffUndeadWithSilver, 300);
        test("rats_bolt_from_villagers", RatGameTests::ratsBoltFromVillagers, 300);
        test("villagers_killed_by_rats_rise_as_zombies", RatGameTests::villagersKilledByRatsRiseAsZombies);
        test("rats_hide_at_home_from_danger", RatGameTests::ratsHideAtHomeFromDanger, 300);
        isolatedTest("rat_swarms_hunt_livestock", RatGameTests::ratSwarmsHuntLivestock, 400);
        test("rats_eat_any_dropped_food", RatGameTests::ratsEatAnyDroppedFood, 300);
        test("rats_are_worth_a_little_experience", RatGameTests::ratsAreWorthALittleExperience);
        calmTest("rats_turn_up_in_villages", RatGameTests::ratsTurnUpInVillages, 100);
        calmTest("new_villages_have_rats_burrowed_in", RatGameTests::newVillagesHaveRatsBurrowedIn, 100);
        isolatedTest("rats_shelter_from_the_rain", RatGameTests::ratsShelterFromTheRain);
        // On its own: a doctor from another test running alongside would count as one already come
        isolatedTest("plague_doctor_answers_the_call", PlagueDoctorGameTests::doctorAnswersTheCall);
        test("plague_saps_the_living_not_the_undead", RatGameTests::plagueSapsTheLivingButNotTheUndead);
        test("only_vampires_open_the_skills", RatGameTests::onlyVampiresOpenTheSkills);
        test("hunter_cabin_style_follows_the_biome", HunterCabinGameTests::cabinStyleFollowsTheBiome);
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
        isolatedTest(name, function, DEFAULT_MAX_TICKS);
    }

    /// Also for tests that count the mobs around them (a rat swarm), which other tests' mobs would throw off
    private static void isolatedTest(String name, Consumer<GameTestHelper> function, int maxTicks) {
        TESTS.add(new TestEntry(TEST_FUNCTIONS.register(name, () -> function), maxTicks, Batch.ISOLATED));
    }

    /// For tests that keep a (mock) player in the world for a while. While any player exists, the game despawns
    /// monsters far from it, which would pull mobs out of other tests, so these get a batch of their own too.
    private static void playerTest(String name, Consumer<GameTestHelper> function, int maxTicks) {
        TESTS.add(new TestEntry(TEST_FUNCTIONS.register(name, () -> function), maxTicks, Batch.WITH_PLAYER));
    }

    /// For tests of a skittish mob going about its business (the wandering Plague Doctor), which the monsters and
    /// doors of other tests nearby would scare off or lead astray
    private static void calmTest(String name, Consumer<GameTestHelper> function, int maxTicks) {
        TESTS.add(new TestEntry(TEST_FUNCTIONS.register(name, () -> function), maxTicks, Batch.CALM));
    }

    public static void register(IEventBus modEventBus) {
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(ModGameTests::registerTests);
    }

    // One for each environment: the registry won't take the same one twice
    private static TestEnvironmentDefinition<?> clearSkies() {
        return new TestEnvironmentDefinition.Weather(TestEnvironmentDefinition.Weather.Type.CLEAR);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Clear skies for every test: a storm darkens noon enough to throw off the ones about daylight
        Holder<TestEnvironmentDefinition<?>> shared = event.registerEnvironment(CrimsonCovenant.id("default"), clearSkies());
        // Tests are batched by environment, so an environment of their own keeps isolated tests apart
        Holder<TestEnvironmentDefinition<?>> isolated = event.registerEnvironment(CrimsonCovenant.id("isolated"), clearSkies());
        Holder<TestEnvironmentDefinition<?>> withPlayer = event.registerEnvironment(CrimsonCovenant.id("with_player"), clearSkies());
        Holder<TestEnvironmentDefinition<?>> calm = event.registerEnvironment(CrimsonCovenant.id("calm"), clearSkies());
        for (TestEntry test : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> key = test.function().getKey();
            Holder<TestEnvironmentDefinition<?>> environment = switch (test.batch()) {
                case SHARED -> shared;
                case ISOLATED -> isolated;
                case WITH_PLAYER -> withPlayer;
                case CALM -> calm;
            };
            event.registerTest(key.identifier(), new FunctionGameTestInstance(key,
                    new TestData<>(environment, CrimsonCovenant.id("arena"), test.maxTicks(), 0, true)));
        }
    }
}
