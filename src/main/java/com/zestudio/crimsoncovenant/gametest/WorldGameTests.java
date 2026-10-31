package com.zestudio.crimsoncovenant.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.DecorBlock;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.MasterHunter;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.SilverArrow;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.hunter.HunterGear;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.item.WerewolfBelt;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.registry.ModVillagers;
import com.zestudio.crimsoncovenant.skill.MoonPowers;
import com.zestudio.crimsoncovenant.skill.NightOfTerror;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.world.FactionVillagers;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/// GameTests for silver, decor blocks, the blood moon, faction villagers, werewolves and the newer
/// Holy Water, Horseman and vampire behaviours. Registered in ModGameTests.
final class WorldGameTests {
    private WorldGameTests() {}

    // ---- Holy Water & Horseman ----

    static void holyFlameLooksLikeFire(GameTestHelper helper) {
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(4, 1, 4));
        husk.addEffect(new MobEffectInstance(ModEffects.HOLY_FLAME, 100, 0, false, false, true));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(showsFlames(husk), Component.literal("Holy Flame should show as the regular burning look"));
            helper.assertTrue(husk.getRemainingFireTicks() <= 0, Component.literal("...without real fire that water could put out"));
            helper.succeed();
        });
    }

    // Clients draw flames from the synced "on fire" flag, which is what Holy Flame sets (the server's isOnFire()
    // only checks for real fire)
    private static boolean showsFlames(net.minecraft.world.entity.Entity entity) {
        try {
            java.lang.reflect.Method getSharedFlag = net.minecraft.world.entity.Entity.class.getDeclaredMethod("getSharedFlag", int.class);
            getSharedFlag.setAccessible(true);
            return (boolean) getSharedFlag.invoke(entity, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void horsemansHeadGlowsInItsJar(GameTestHelper helper) {
        BlockPos stand = new BlockPos(2, 1, 2);
        helper.setBlock(stand, ModBlocks.HORSEMANS_HEAD.get());
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(stand)).getLightEmission() > 0, Component.literal("The bottled head should glow when set down"));
        helper.succeed();
    }

    // ---- Vampires ----

    static void vampireSeeksShelterAtDawn(GameTestHelper helper) {
        // A roof over the far end of the arena; the rest is open to the (test world's) noon sun
        for (int x = 5; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        Vampire vampire = helper.spawn(ModEntities.VAMPIRE.get(), new BlockPos(1, 1, 4));
        vampire.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        vampire.targetSelector.removeAllGoals(goal -> true);
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.relativeVec(vampire.position()).x > 5.0, Component.literal("Vampire should run for the roofed shelter (at "
                    + helper.relativeVec(vampire.position()) + ", health " + vampire.getHealth() + ", path " + vampire.getNavigation().getPath() + ")"));
            helper.assertTrue(!vampire.isExposedToSun(), Component.literal("Vampire should be out of the sun"));
        });
    }

    static void masterVampireCantDrainUnderHolyFlame(GameTestHelper helper) {
        // Villager behind glass, well inside the drain range
        for (int z = 0; z < 9; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.GLASS);
            }
        }
        MasterVampire master = helper.spawn(ModEntities.MASTER_VAMPIRE.get(), new BlockPos(2, 1, 4));
        master.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        master.addEffect(new MobEffectInstance(ModEffects.HOLY_FLAME, 200));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 4));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), Component.literal("A Master Vampire burning with Holy Flame can't drain life"));
            helper.succeed();
        });
    }

    // ---- Blood moon ----

    static void bloodMoonTiming(GameTestHelper helper) {
        long night = 14000L;
        helper.assertTrue(BloodMoon.shouldRise(night, MoonPhase.FULL_MOON, 16, 0), Component.literal("Full moon on day 16 should bring the first blood moon"));
        helper.assertTrue(!BloodMoon.shouldRise(night, MoonPhase.FULL_MOON, 8, 0), Component.literal("Too soon: only 8 days since the start"));
        helper.assertTrue(!BloodMoon.shouldRise(night, MoonPhase.WANING_GIBBOUS, 17, 0), Component.literal("Needs a full moon"));
        helper.assertTrue(!BloodMoon.shouldRise(6000L, MoonPhase.FULL_MOON, 16, 0), Component.literal("Needs to be night"));
        helper.assertTrue(BloodMoon.shouldRise(night, MoonPhase.FULL_MOON, 32, 16), Component.literal("16 days after the last one is long enough"));
        helper.assertTrue(!BloodMoon.shouldRise(night, MoonPhase.FULL_MOON, 24, 16), Component.literal("8 days after the last one is too soon"));
        // It always lasts until the next dawn: tonight's if it's before dawn, otherwise tomorrow's
        helper.assertTrue(BloodMoon.nextDawn(24000L * 5 + 14000L) == 24000L * 5 + 23000L, Component.literal("Rising at night, it should end at that night's dawn"));
        helper.assertTrue(BloodMoon.nextDawn(24000L * 5 + 6000L) == 24000L * 5 + 23000L, Component.literal("Started at noon, it should last through the night"));
        helper.assertTrue(BloodMoon.nextDawn(24000L * 5 + 23500L) == 24000L * 6 + 23000L, Component.literal("Started just after dawn, it should last until the next one"));
        helper.succeed();
    }

    static void bloodMoonGivesVampiresBloodlust(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(4, 1, 4));
        vampire.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        double normalSpeed = vampire.getAttributeValue(Attributes.MOVEMENT_SPEED);
        BloodMoon.start(level);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vampire.hasEffect(ModEffects.BLOODLUST), Component.literal("Vampires under a blood moon should get Bloodlust")))
                // End it straight away so it doesn't spill into other tests
                .thenExecute(() -> BloodMoon.stop(level))
                .thenExecute(() -> helper.assertTrue(vampire.getAttributeValue(Attributes.MOVEMENT_SPEED) > normalSpeed, Component.literal("Bloodlust should speed vampires up")))
                .thenSucceed();
    }

    // ---- Silver ----

    static void silverArrowsHurtUndeadMore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk plainTarget = spawnSturdyHusk(helper, new BlockPos(7, 1, 2));
        Husk silverTarget = spawnSturdyHusk(helper, new BlockPos(7, 1, 6));
        fire(helper, new Arrow(level, 0, 0, 0, new ItemStack(Items.ARROW), null), new BlockPos(3, 1, 2));
        fire(helper, new SilverArrow(level, 0, 0, 0, new ItemStack(ModItems.SILVER_ARROW.get()), null), new BlockPos(3, 1, 6));
        helper.succeedWhen(() -> {
            float plainDamage = plainTarget.getMaxHealth() - plainTarget.getHealth();
            float silverDamage = silverTarget.getMaxHealth() - silverTarget.getHealth();
            helper.assertTrue(plainDamage > 0 && silverDamage > 0, Component.literal("Both arrows should hit"));
            helper.assertTrue(silverDamage > plainDamage, Component.literal("Silver should hurt undead more: " + silverDamage + " vs " + plainDamage));
            helper.assertTrue(silverTarget.hasEffect(MobEffects.SLOWNESS) && !plainTarget.hasEffect(MobEffects.SLOWNESS),
                    Component.literal("Silver should slow undead"));
        });
    }

    private static Husk spawnSturdyHusk(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, pos);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        husk.setHealth(100.0F);
        return husk;
    }

    private static void fire(GameTestHelper helper, AbstractArrow arrow, BlockPos from) {
        BlockPos start = helper.absolutePos(from);
        arrow.setPos(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        arrow.shoot(1.0, 0.0, 0.0, 1.5F, 0.0F);
        helper.getLevel().addFreshEntity(arrow);
    }

    static void silverIsCraftableAndBrewable(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String recipe : new String[] {"brewing/potion_water_silver_dust", "brewing/splash_potion_water_silver_dust", "silver_ingot_from_smelting_raw_silver",
                "silver_nugget", "silver_dust", "silver_arrow", "gravestone_from_stonecutting"}) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id(recipe))).isPresent(), Component.literal("Missing recipe " + recipe));
        }
        // Holy Water is brewed from Silver Dust only, never a whole ingot
        for (String recipe : new String[] {"brewing/potion_water_silver_ingot", "brewing/splash_potion_water_silver_ingot", "brewing/lingering_potion_water_silver_ingot"}) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id(recipe))).isEmpty(), Component.literal("Unexpected recipe " + recipe));
        }
        helper.succeed();
    }

    static void silverOreGeneratesLikeIron(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        var placedFeatures = helper.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        var silver = placedFeatures.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, CrimsonCovenant.id("ore_silver_middle")));
        var ores = biomes.getOrThrow(Biomes.PLAINS).value().getGenerationSettings().features().get(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal());
        helper.assertTrue(ores.contains(silver), Component.literal("Silver ore should generate in the overworld's ore step"));
        helper.succeed();
    }

    // ---- Decor ----

    static void gravestonesFaceThePlayer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, ModBlocks.GRAVESTONE.get().defaultBlockState().setValue(DecorBlock.FACING, Direction.EAST));
        BlockState state = helper.getBlockState(pos);
        AABB bounds = state.getShape(helper.getLevel(), helper.absolutePos(pos)).bounds();
        helper.assertTrue(bounds.maxY < 1.0, Component.literal("A gravestone shouldn't be a full block tall"));
        helper.assertTrue(bounds.getXsize() < bounds.getZsize(), Component.literal("Facing east, the headstone should be thin along X"));
        helper.succeed();
    }

    // ---- Faction villagers ----

    static void villagesKeepToOneFaction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.assertTrue(!FactionVillagers.otherFactionNearby(level, center, FactionVillagers.Faction.VAMPIRES), Component.literal("No hunters around yet"));
        Villager doctor = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
        doctor.setVillagerData(doctor.getVillagerData().withProfession(
                level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(ModVillagers.PLAGUE_DOCTOR.getKey())));
        helper.assertTrue(FactionVillagers.otherFactionNearby(level, center, FactionVillagers.Faction.VAMPIRES),
                Component.literal("A Plague Doctor nearby should keep the vampires' Blood Altar out"));
        helper.assertTrue(!FactionVillagers.otherFactionNearby(level, center, FactionVillagers.Faction.HUNTERS),
                Component.literal("The hunters' own workstation is fine"));
        helper.succeed();
    }

    // ---- Werewolves ----

    static void werewolfHuntsVillagers(GameTestHelper helper) {
        Werewolf werewolf = helper.spawn(ModEntities.WEREWOLF.get(), new BlockPos(2, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 4));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(werewolf.getTarget() == villager, Component.literal("Werewolves should hunt villagers")))
                .thenExecute(werewolf::discard)
                .thenSucceed();
    }

    static void werewolfFleesMasterHunters(GameTestHelper helper) {
        MasterHunter master = helper.spawnWithNoFreeWill(ModEntities.MASTER_HUNTER.get(), new BlockPos(1, 1, 4));
        Werewolf werewolf = helper.spawn(ModEntities.WEREWOLF.get(), new BlockPos(3, 1, 4));
        werewolf.targetSelector.removeAllGoals(goal -> true);
        double start = werewolf.distanceTo(master);
        helper.succeedWhen(() -> helper.assertTrue(werewolf.distanceTo(master) > start + 2.5, Component.literal("Werewolves should run from Master Hunters")));
    }

    static void werewolfVanishesWithTheMoon(GameTestHelper helper) {
        Werewolf werewolf = helper.spawnWithNoFreeWill(ModEntities.WEREWOLF.get(), new BlockPos(4, 1, 4));
        werewolf.setMoonBound(true);
        // The test world sits at noon, so the moon is long gone
        helper.succeedWhen(() -> helper.assertTrue(werewolf.isRemoved(), Component.literal("A moon-bound werewolf should vanish by day")));
    }

    static void huntersHuntWerewolves(GameTestHelper helper) {
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(2, 1, 4));
        Werewolf werewolf = helper.spawnWithNoFreeWill(ModEntities.WEREWOLF.get(), new BlockPos(6, 1, 4));
        helper.succeedWhen(() -> helper.assertTrue(hunter.getTarget() == werewolf, Component.literal("Hunters should hunt werewolves")));
    }

    static void werewolvesLeaveTeethAndHide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Werewolf werewolf = helper.spawnWithNoFreeWill(ModEntities.WEREWOLF.get(), new BlockPos(4, 1, 4));
        LootTable loot = level.getServer().reloadableRegistries().getLootTable(werewolf.getLootTable().orElseThrow());
        LootParams byPlayer = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, werewolf)
                .withParameter(LootContextParams.ORIGIN, werewolf.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                .create(LootContextParamSets.ENTITY);
        LootParams byAccident = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, werewolf)
                .withParameter(LootContextParams.ORIGIN, werewolf.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        int teeth = 0;
        int hides = 0;
        for (int i = 0; i < 1000; i++) {
            for (ItemStack stack : loot.getRandomItems(byPlayer)) {
                teeth += stack.is(ModItems.WEREWOLF_TOOTH.get()) ? 1 : 0;
                hides += stack.is(ModItems.WEREWOLF_HIDE.get()) ? 1 : 0;
            }
            helper.assertTrue(loot.getRandomItems(byAccident).isEmpty(), Component.literal("A werewolf only leaves anything for a player's kill"));
        }
        // Each is an even chance a kill, on its own roll
        helper.assertTrue(teeth > 400 && teeth < 600, Component.literal("Expected about 500 kills in 1000 to leave teeth, got " + teeth));
        helper.assertTrue(hides > 400 && hides < 600, Component.literal("Expected about 500 kills in 1000 to leave hide, got " + hides));
        werewolf.discard();
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    static void teethBrewIntoFrenzy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var recipes = level.getServer().getRecipeManager();
        for (String recipe : new String[] {"potion_awkward_werewolf_tooth", "potion_frenzy_redstone", "potion_frenzy_glowstone_dust", "potion_frenzy_gunpowder",
                "potion_long_frenzy_gunpowder", "potion_strong_frenzy_gunpowder", "splash_potion_frenzy_dragon_breath", "splash_potion_long_frenzy_dragon_breath",
                "splash_potion_strong_frenzy_dragon_breath"}) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id("brewing/" + recipe))).isPresent(), Component.literal("Missing recipe " + recipe));
        }
        BrewingInput awkward = new BrewingInput(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD), new ItemStack(ModItems.WEREWOLF_TOOTH.get()));
        ItemStack frenzy = level.recipeAccess().getRecipeFor(RecipeType.BREWING, awkward, level).orElseThrow().value().assemble(awkward);
        helper.assertTrue(frenzy.is(Items.POTION) && frenzy.get(DataComponents.POTION_CONTENTS).is(ModPotions.FRENZY), Component.literal("A tooth in an Awkward Potion should brew Frenzy"));
        BrewingInput glowing = new BrewingInput(frenzy, new ItemStack(Items.GLOWSTONE_DUST));
        ItemStack strong = level.recipeAccess().getRecipeFor(RecipeType.BREWING, glowing, level).orElseThrow().value().assemble(glowing);
        helper.assertTrue(strong.get(DataComponents.POTION_CONTENTS).is(ModPotions.STRONG_FRENZY), Component.literal("Glowstone should strengthen Frenzy"));
        BrewingInput powdered = new BrewingInput(strong, new ItemStack(Items.GUNPOWDER));
        ItemStack splash = level.recipeAccess().getRecipeFor(RecipeType.BREWING, powdered, level).orElseThrow().value().assemble(powdered);
        helper.assertTrue(splash.is(Items.SPLASH_POTION) && splash.get(DataComponents.POTION_CONTENTS).is(ModPotions.STRONG_FRENZY), Component.literal("Gunpowder should make it splash"));
        helper.assertTrue(HunterGear.requiredRank(frenzy) == 0 && HunterGear.requiredRank(PotionContents.createItemStack(Items.LINGERING_POTION, ModPotions.FRENZY)) == 0,
                Component.literal("Frenzy is no craft of the hunters"));
        helper.succeed();
    }

    static void frenzyMakesADrinkerStrongAndFast(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(ModPotions.FRENZY.value().getEffects().getFirst()));
        helper.assertTrue(player.hasEffect(ModEffects.FRENZY), Component.literal("The Potion of Frenzy should give the Frenzy effect"));
        assertClose(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), damage + 3.0, "Frenzy should hit as hard as Strength I");
        assertClose(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 1.2, "Frenzy should run as fast as Speed I");
        player.removeEffect(ModEffects.FRENZY);
        player.addEffect(new MobEffectInstance(ModPotions.STRONG_FRENZY.value().getEffects().getFirst()));
        assertClose(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), damage + 6.0, "Strong Frenzy should hit as hard as Strength II");
        assertClose(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 1.4, "Strong Frenzy should run as fast as Speed II");
        player.removeEffect(ModEffects.FRENZY);
        assertClose(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), damage, "It should all go when the Frenzy does");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static void assertClose(GameTestHelper helper, double value, double expected, String message) {
        helper.assertTrue(Math.abs(value - expected) < 0.001, Component.literal(message + ": expected " + expected + ", got " + value));
    }

    static void frenzyScattersTheHarmless(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 4));
        Vec3 start = cow.position();
        cow.addEffect(new MobEffectInstance(ModEffects.FRENZY, 150));
        helper.assertTrue(NightOfTerror.isTerrified(cow), Component.literal("Frenzy should send harmless creatures running"));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(cow.position().distanceTo(start) > 2.0, Component.literal("The cow should scatter")))
                .thenExecute(cow::discard)
                .thenSucceed();
    }

    static void frenziedMonstersTurnOnEachOther(GameTestHelper helper) {
        Zombie frenzied = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 4));
        Zombie other = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        helper.assertTrue(!NightOfTerror.isTerrified(frenzied), Component.literal("Nothing should have frightened it yet"));
        frenzied.addEffect(new MobEffectInstance(ModEffects.FRENZY, 400));
        helper.assertTrue(!NightOfTerror.isTerrified(frenzied), Component.literal("Frenzy doesn't send monsters running"));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(frenzied.getTarget() == other, Component.literal("A frenzied zombie should go for the other"));
                    helper.assertTrue(other.getHealth() < other.getMaxHealth(), Component.literal("...and bite it"));
                    helper.assertTrue(other.getTarget() == frenzied, Component.literal("...and be bitten back"));
                })
                .thenExecute(() -> {
                    frenzied.discard();
                    other.discard();
                })
                .thenSucceed();
    }

    static void thrownFrenzySetsACrowdOnEachOther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 4));
        Zombie another = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 4));
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 5));
        BlockPos above = helper.absolutePos(new BlockPos(4, 3, 4));
        ThrownSplashPotion potion = new ThrownSplashPotion(level, above.getX() + 0.5, above.getY(), above.getZ() + 0.5,
                PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.FRENZY));
        potion.setDeltaMovement(0.0, -0.5, 0.0);
        level.addFreshEntity(potion);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(zombie.hasEffect(ModEffects.FRENZY) && another.hasEffect(ModEffects.FRENZY), Component.literal("The splash should reach both zombies"));
                    helper.assertTrue(zombie.getTarget() == another && another.getTarget() == zombie, Component.literal("The zombies should turn on each other"));
                    helper.assertTrue(NightOfTerror.isTerrified(cow), Component.literal("The cow should run"));
                    helper.assertTrue(zombie.getTarget() != cow && another.getTarget() != cow, Component.literal("Frenzy turns monsters on monsters, not on the cow"));
                })
                .thenExecute(() -> {
                    zombie.discard();
                    another.discard();
                    cow.discard();
                })
                .thenSucceed();
    }

    static void whipsAndBeltsAreMadeFromHide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var recipes = level.getServer().getRecipeManager();
        ItemStack hide = new ItemStack(ModItems.WEREWOLF_HIDE.get());
        ItemStack leather = new ItemStack(Items.LEATHER);
        for (String name : new String[] {"whip", "werewolf_belt"}) {
            var recipe = recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id(name))).orElseThrow().value();
            var ingredients = ((ShapedRecipe) recipe).getIngredients().stream().flatMap(java.util.Optional::stream).toList();
            helper.assertTrue(ingredients.stream().anyMatch((Ingredient ingredient) -> ingredient.test(hide)), Component.literal(name + " should take Werewolf Hide"));
            helper.assertTrue(ingredients.stream().noneMatch((Ingredient ingredient) -> ingredient.test(leather)), Component.literal(name + " shouldn't take plain leather"));
        }
        for (String unlock : new String[] {"recipes/combat/whip", "recipes/combat/werewolf_belt"}) {
            helper.assertTrue(level.getServer().getAdvancements().get(CrimsonCovenant.id(unlock)) != null, Component.literal("Missing recipe unlock " + unlock));
        }
        helper.succeed();
    }

    private static void assertWerewolf(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(WerewolfBelt.isTurned(player), Component.literal("The wearer should have turned"));
        helper.assertTrue(player.getEffect(MobEffects.STRENGTH).getAmplifier() == 2, Component.literal("A werewolf should have Strength III"));
        helper.assertTrue(player.getEffect(MobEffects.SPEED).getAmplifier() == 1, Component.literal("A werewolf should have Speed II"));
        helper.assertTrue(player.hasEffect(MobEffects.JUMP_BOOST), Component.literal("A werewolf should jump high"));
        assertClose(helper, player.getAttributeValue(Attributes.ATTACK_KNOCKBACK), 2.0, "A werewolf's blows should carry Knockback II");
        assertClose(helper, player.getMaxHealth(), 40.0, "A werewolf should have ten extra hearts");
        assertClose(helper, player.getEyeHeight(), 1.62 * WerewolfBelt.EYE_HEIGHT_SCALE, "A werewolf's eyes should be higher");
        assertClose(helper, player.getBbHeight(), 1.8, "A werewolf should still fit through a door");
    }

    private static void assertHuman(GameTestHelper helper, ServerPlayer player, String message) {
        helper.assertTrue(!WerewolfBelt.isTurned(player), Component.literal(message));
        helper.assertTrue(!player.hasEffect(MobEffects.STRENGTH) && !player.hasEffect(MobEffects.SPEED) && !player.hasEffect(MobEffects.JUMP_BOOST),
                Component.literal(message + ", and take the beast's strength with it"));
        assertClose(helper, player.getAttributeValue(Attributes.ATTACK_KNOCKBACK), 0.0, message + ", and take the beast's knockback with it");
        assertClose(helper, player.getMaxHealth(), 20.0, message + ", and its extra hearts");
        assertClose(helper, player.getEyeHeight(), 1.62, message + ", and bring their eyes back down");
    }

    static void beltTurnsItsWearerUnderABloodMoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack belt = new ItemStack(ModItems.WEREWOLF_BELT.get());
        helper.assertTrue(belt.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.LEGS, Component.literal("The belt goes in the leggings slot"));
        player.setItemSlot(EquipmentSlot.LEGS, belt);
        WerewolfBelt.tickForm(player);
        assertHuman(helper, player, "Nothing should happen by day");
        player.setHealth(10.0F);

        BloodMoon.start(level);
        try {
            WerewolfBelt.tickForm(player);
            assertWerewolf(helper, player);
            assertClose(helper, player.getHealth(), 20.0, "Half hurt as a person should be half hurt as a werewolf");
            WerewolfBelt.tickForm(player);
            assertWerewolf(helper, player);
            assertClose(helper, player.getHealth(), 20.0, "Staying turned shouldn't change their health");

            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            WerewolfBelt.tickForm(player);
            assertHuman(helper, player, "Taking the belt off should end it");
            assertClose(helper, player.getHealth(), 10.0, "Slipping the belt off and on shouldn't heal anyone");
            WerewolfBelt.tickForm(player);
            assertHuman(helper, player, "No belt, no turning, moon or no moon");

            player.setItemSlot(EquipmentSlot.LEGS, belt);
            WerewolfBelt.tickForm(player);
            assertWerewolf(helper, player);
        } finally {
            BloodMoon.stop(level);
        }
        WerewolfBelt.tickForm(player);
        assertHuman(helper, player, "The moon setting should end it");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    static void beltAnswersToAFullMoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerClockManager clocks = level.getServer().clockManager();
        Holder<WorldClock> overworldClock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
        long originalTime = level.getOverworldClockTime();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.WEREWOLF_BELT.get()));
        try {
            // Full moons come every 8 days
            clocks.setTotalTicks(overworldClock, 24000L * 8 + 18000L);
            helper.assertTrue(BloodMoon.moonPhase(level) == MoonPhase.FULL_MOON, Component.literal("Day 8 should have a full moon, not " + BloodMoon.moonPhase(level)));
            WerewolfBelt.tickForm(player);
            assertWerewolf(helper, player);
            clocks.setTotalTicks(overworldClock, 24000L * 8 + 23500L);
            WerewolfBelt.tickForm(player);
            assertHuman(helper, player, "Dawn should end it");
            clocks.setTotalTicks(overworldClock, 24000L * 9 + 18000L);
            WerewolfBelt.tickForm(player);
            assertHuman(helper, player, "The night after the full moon is no good");
        } finally {
            clocks.setTotalTicks(overworldClock, originalTime);
        }
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    static void vampiresDontAnswerTheMoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.WEREWOLF_BELT.get()));
        player.setData(ModAttachments.IS_VAMPIRE, true);
        BloodMoon.start(level);
        try {
            WerewolfBelt.tickForm(player);
            assertHuman(helper, player, "A vampire shouldn't turn");
            player.setData(ModAttachments.IS_VAMPIRE, false);
            WerewolfBelt.tickForm(player);
            assertWerewolf(helper, player);
            player.setData(ModAttachments.IS_VAMPIRE, true);
            WerewolfBelt.tickForm(player);
            assertHuman(helper, player, "A werewolf who turns vampire should stop being one");
        } finally {
            BloodMoon.stop(level);
        }
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    static void werewolfBlowsLandWithKnockback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.WEREWOLF_BELT.get()));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        Husk plain = spawnSturdyHusk(helper, new BlockPos(4, 1, 2));
        Husk struck = spawnSturdyHusk(helper, new BlockPos(4, 1, 6));
        player.attack(plain);
        double plainPush = plain.getDeltaMovement().horizontalDistance();
        BloodMoon.start(level);
        try {
            WerewolfBelt.tickForm(player);
            player.attack(struck);
        } finally {
            BloodMoon.stop(level);
        }
        double push = struck.getDeltaMovement().horizontalDistance();
        helper.assertTrue(push > plainPush + 0.5, Component.literal("A werewolf's blow should throw harder: " + push + " against " + plainPush));
        plain.discard();
        struck.discard();
        WerewolfBelt.tickForm(player);
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /// helper.makeMockServerPlayerInLevel, but in survival: mobs won't take a creative player for a target at all
    private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper, Vec3 pos) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survival-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getAbilities().invulnerable = false;
        Vec3 at = helper.absoluteVec(pos);
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    static void skeletonsFearWerewolfPlayers(GameTestHelper helper) {
        // Out of the sun, which they'd run from anyway
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        ServerPlayer player = makeSurvivalPlayer(helper, new Vec3(0.5, 1.0, 4.5));
        Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(2, 1, 4));
        skeleton.setTarget(player);
        helper.assertTrue(skeleton.getTarget() == player, Component.literal("Nothing should frighten a skeleton off a plain player"));
        skeleton.setTarget(null);
        player.setData(ModAttachments.WEREWOLF_FORM, true);
        skeleton.setTarget(player);
        helper.assertTrue(skeleton.getTarget() != player, Component.literal("A skeleton shouldn't take on a werewolf"));
        double start = skeleton.distanceTo(player);
        helper.startSequence()
                // It may bolt for either end of the arena: this far off is reachable whichever it picks
                .thenWaitUntil(() -> helper.assertTrue(skeleton.distanceTo(player) > start + 1.5, Component.literal("A skeleton should run from a werewolf")))
                .thenExecute(() -> {
                    skeleton.discard();
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    static void werewolvesKnowTheirOwn(GameTestHelper helper) {
        ServerPlayer player = makeSurvivalPlayer(helper, new Vec3(2.5, 1.0, 4.5));
        helper.assertTrue(Werewolf.isPrey(player), Component.literal("A plain player is prey"));
        player.setData(ModAttachments.WEREWOLF_FORM, true);
        helper.assertTrue(!Werewolf.isPrey(player), Component.literal("A werewolf player isn't"));
        Werewolf werewolf = helper.spawn(ModEntities.WEREWOLF.get(), new BlockPos(6, 1, 4));
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(werewolf.getTarget() == null, Component.literal("A werewolf should leave a fellow werewolf be"));
                    player.setData(ModAttachments.WEREWOLF_FORM, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(werewolf.getTarget() == player, Component.literal("Turned back, they're prey again")))
                .thenExecute(() -> {
                    werewolf.discard();
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    static void moonbarsFillWithTheHunt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack belt = new ItemStack(ModItems.WEREWOLF_BELT.get());
        player.setItemSlot(EquipmentSlot.LEGS, belt);
        MoonPowers.set(player, 40);
        BloodMoon.start(level);
        try {
            WerewolfBelt.tickForm(player);
            helper.assertValueEqual(MoonPowers.get(player), 0, "the moonbars on turning");
            Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(2, 1, 4));
            cow.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
            helper.assertValueEqual(MoonPowers.get(player), 5, "the moonbars after killing a cow");
            MoonPowers.set(player, 95);
            Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
            zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
            helper.assertValueEqual(MoonPowers.get(player), MoonPowers.MAX_MOON, "the moonbars at their fullest");

            player.getFoodData().setFoodLevel(5);
            WerewolfBelt.tickForm(player);
            helper.assertTrue(player.getFoodData().getFoodLevel() >= 18, Component.literal("A werewolf shouldn't hunger"));
            helper.assertTrue(player.getFoodData().needsFood(), Component.literal("...but should still be able to eat"));

            MoonPowers.set(player, 20);
            for (ItemStack food : new ItemStack[] {new ItemStack(Items.BEEF), new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.COOKED_BEEF)}) {
                NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, food, 0, ItemStack.EMPTY));
            }
            helper.assertValueEqual(MoonPowers.get(player), 30, "the moonbars after raw beef, rotten flesh and a cooked steak");

            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            WerewolfBelt.tickForm(player);
            player.setItemSlot(EquipmentSlot.LEGS, belt);
            WerewolfBelt.tickForm(player);
            helper.assertValueEqual(MoonPowers.get(player), 0, "the moonbars on turning again");
        } finally {
            BloodMoon.stop(level);
        }
        WerewolfBelt.tickForm(player);
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    static void moonPowersCostMoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.WEREWOLF_BELT.get()));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 4));
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(6, 1, 4));
        helper.assertTrue(!MoonPowers.tryCast(player, MoonPowers.Power.CALL_OF_THE_PACK), Component.literal("Only a werewolf has the powers"));
        List<Wolf> pack;
        BloodMoon.start(level);
        try {
            WerewolfBelt.tickForm(player);
            helper.assertTrue(!MoonPowers.tryCast(player, MoonPowers.Power.CALL_OF_THE_PACK), Component.literal("No moon, no pack"));

            MoonPowers.set(player, 60);
            helper.assertTrue(MoonPowers.tryCast(player, MoonPowers.Power.CALL_OF_THE_PACK), Component.literal("The pack should come"));
            helper.assertValueEqual(MoonPowers.get(player), 50, "the moon after Call of the Pack");
            pack = level.getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(8.0), MoonPowers::isPackWolf);
            helper.assertValueEqual(pack.size(), MoonPowers.PACK_SIZE, "the wolves that came");
            helper.assertTrue(pack.stream().allMatch(wolf -> wolf.getOwner() == player), Component.literal("The pack should be the werewolf's own"));

            helper.assertTrue(MoonPowers.tryCast(player, MoonPowers.Power.MADDENING_HOWL), Component.literal("The howl should sound"));
            helper.assertValueEqual(MoonPowers.get(player), 30, "the moon after Maddening Howl");
            helper.assertTrue(player.hasEffect(ModEffects.FRENZY) && zombie.hasEffect(ModEffects.FRENZY) && cow.hasEffect(ModEffects.FRENZY),
                    Component.literal("The howl should frenzy the werewolf and everything around them"));
            helper.assertTrue(pack.stream().noneMatch(wolf -> wolf.hasEffect(ModEffects.FRENZY)), Component.literal("...but not their pack"));

            helper.assertTrue(MoonPowers.tryCast(player, MoonPowers.Power.MOONFALL), Component.literal("The werewolf should leap"));
            helper.assertValueEqual(MoonPowers.get(player), 0, "the moon after Moonfall");
            helper.assertTrue(MoonPowers.isLeaping(player) && player.getDeltaMovement().y > 0.5, Component.literal("A Moonfall starts with a leap"));
            float before = zombie.getHealth();
            MoonPowers.land(player);
            helper.assertTrue(!MoonPowers.isLeaping(player), Component.literal("It should end when it lands"));
            helper.assertTrue(zombie.getHealth() < before - 4.0F, Component.literal("Landing should hurt what's near"));
            helper.assertTrue(zombie.getData(ModAttachments.SPEAR_DRIVE).isPresent(), Component.literal("...and fling it away"));
            helper.assertTrue(pack.stream().allMatch(wolf -> wolf.getHealth() == wolf.getMaxHealth()), Component.literal("...but not the pack"));

            helper.assertTrue(!MoonPowers.tryCast(player, MoonPowers.Power.ECLIPSE_HIDE), Component.literal("Eclipse Hide costs 50"));
            MoonPowers.set(player, 50);
            helper.assertTrue(MoonPowers.tryCast(player, MoonPowers.Power.ECLIPSE_HIDE), Component.literal("Eclipse Hide should take hold"));
            helper.assertTrue(player.hasEffect(ModEffects.ECLIPSE_HIDE), Component.literal("It should last on the werewolf"));
            MoonPowers.tickPlayer(player);
            helper.assertTrue(player.getData(ModAttachments.ECLIPSE_HIDE_VISIBLE), Component.literal("Those nearby should see its shell"));
            player.removeEffect(ModEffects.ECLIPSE_HIDE);
            MoonPowers.tickPlayer(player);
            helper.assertTrue(!player.getData(ModAttachments.ECLIPSE_HIDE_VISIBLE), Component.literal("The shell should go with it"));

            MoonPowers.set(player, 50);
            helper.assertTrue(MoonPowers.eclipseHideCooldown(player) > 0.9F, Component.literal("Eclipse Hide should have a long way to come back"));
            helper.assertTrue(!MoonPowers.tryCast(player, MoonPowers.Power.ECLIPSE_HIDE), Component.literal("Eclipse Hide should be slow to come back"));
            helper.assertValueEqual(MoonPowers.get(player), 50, "the moon, not spent on a power that wouldn't come");
            player.setData(ModAttachments.ECLIPSE_HIDE_READY_AT, 0L);
            helper.assertTrue(MoonPowers.tryCast(player, MoonPowers.Power.ECLIPSE_HIDE), Component.literal("Rested, it should come again"));
        } finally {
            BloodMoon.stop(level);
        }
        WerewolfBelt.tickForm(player);
        helper.assertTrue(!player.hasEffect(ModEffects.ECLIPSE_HIDE), Component.literal("Eclipse Hide goes with the beast"));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pack.stream().allMatch(Wolf::isRemoved), Component.literal("The pack should go when its werewolf turns back")))
                .thenExecute(() -> {
                    zombie.discard();
                    cow.discard();
                    level.getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    static void eclipseHideSparesAllButSilver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk husk = spawnSturdyHusk(helper, new BlockPos(4, 1, 4));
        husk.addEffect(new MobEffectInstance(ModEffects.ECLIPSE_HIDE, 100));
        husk.hurtServer(level, level.damageSources().generic(), 50.0F);
        float taken = husk.getMaxHealth() - husk.getHealth();
        helper.assertTrue(taken > 0.0F && taken <= 5.0F, Component.literal("Eclipse Hide should take 90% off a blow, but it took " + taken));
        husk.damageCooldownTime = 0;
        husk.hurtServer(level, level.damageSources().source(ModDamageTypes.SILVER_TRAP), 30.0F);
        float silver = husk.getMaxHealth() - husk.getHealth() - taken;
        helper.assertTrue(silver > 20.0F, Component.literal("Silver should get through in full, but it took " + silver));
        husk.discard();
        helper.succeed();
    }

    static void werewolvesShrugOffTheVampireCurse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(2, 1, 4));
        ItemStack belt = new ItemStack(ModItems.WEREWOLF_BELT.get());
        helper.assertValueEqual(belt.getMaxDamage(), WerewolfBelt.DURABILITY, "the belt's durability");
        ServerPlayer werewolf = helper.makeMockServerPlayerInLevel();
        werewolf.setItemSlot(EquipmentSlot.LEGS, belt.copy());
        ServerPlayer unlucky = helper.makeMockServerPlayerInLevel();
        unlucky.setItemSlot(EquipmentSlot.LEGS, belt.copy());
        ServerPlayer worn = helper.makeMockServerPlayerInLevel();
        ItemStack almostGone = belt.copy();
        almostGone.setDamageValue(WerewolfBelt.DURABILITY - 1);
        worn.setItemSlot(EquipmentSlot.LEGS, almostGone);
        BloodMoon.start(level);
        try {
            WerewolfBelt.tickForm(werewolf);
            WerewolfBelt.tickForm(unlucky);
            WerewolfBelt.tickForm(worn);
            new ItemStack(ModItems.BLOOD_BOTTLE.get()).finishUsingItem(level, werewolf);
            helper.assertTrue(!Vampirism.isInfected(werewolf), Component.literal("A werewolf should drink blood without catching the curse"));
            werewolf.die(level.damageSources().mobAttack(vampire));
            unlucky.die(level.damageSources().generic());
            worn.die(level.damageSources().mobAttack(vampire));
        } finally {
            BloodMoon.stop(level);
        }
        helper.assertTrue(!Vampirism.isInfected(werewolf), Component.literal("A werewolf killed by a vampire shouldn't catch the curse"));
        helper.assertTrue(!WerewolfBelt.isTurned(werewolf), Component.literal("Dying should end the form"));
        helper.assertTrue(werewolf.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), Component.literal("The belt shouldn't fall with them"));
        List<ItemStack> kept = werewolf.getData(ModAttachments.KEPT_SKILLS);
        helper.assertValueEqual(kept.size(), 1, "what a vampire's kill leaves them");
        helper.assertTrue(kept.getFirst().is(ModItems.WEREWOLF_BELT.get()), Component.literal("It should be the belt"));
        helper.assertValueEqual(kept.getFirst().getDamageValue(), WerewolfBelt.VAMPIRE_KILL_DAMAGE, "the wear a vampire's kill does the belt");
        helper.assertTrue(unlucky.getData(ModAttachments.KEPT_SKILLS).isEmpty(), Component.literal("Any other death takes the belt as usual"));
        helper.assertTrue(worn.getData(ModAttachments.KEPT_SKILLS).isEmpty(), Component.literal("A belt worn through should break"));
        vampire.discard();
        level.getServer().getPlayerList().remove(werewolf);
        level.getServer().getPlayerList().remove(unlucky);
        level.getServer().getPlayerList().remove(worn);
        helper.succeed();
    }

    static void werewolfBlowsSlamFoesIntoWalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.WEREWOLF_BELT.get()));
        for (int z = 0; z <= 2; z++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
            }
        }
        Husk walled = spawnSturdyHusk(helper, new BlockPos(5, 1, 1));
        Husk free = spawnSturdyHusk(helper, new BlockPos(2, 1, 7));
        BloodMoon.start(level);
        try {
            WerewolfBelt.tickForm(player);
            Vec3 byTheWall = helper.absoluteVec(new Vec3(3.5, 1.0, 1.5));
            player.teleportTo(byTheWall.x, byTheWall.y, byTheWall.z);
            player.attack(walled);
            Vec3 inTheOpen = helper.absoluteVec(new Vec3(0.5, 1.0, 7.5));
            player.teleportTo(inTheOpen.x, inTheOpen.y, inTheOpen.z);
            player.attack(free);
        } finally {
            BloodMoon.stop(level);
        }
        helper.startSequence()
                .thenExecuteAfter(8, () -> {
                    helper.assertTrue(BloodSpear.isStaggered(walled), Component.literal("Driven into the wall, it should be slammed against it"));
                    helper.assertTrue(!BloodSpear.isStaggered(free), Component.literal("Driven across open ground, it shouldn't"));
                    helper.assertTrue(walled.getHealth() < free.getHealth() - 3.0F,
                            Component.literal("The wall should hurt: " + walled.getHealth() + " against " + free.getHealth()));
                })
                .thenExecute(() -> {
                    walled.discard();
                    free.discard();
                    WerewolfBelt.tickForm(player);
                    level.getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    static void huntersHuntWerewolfPlayers(GameTestHelper helper) {
        ServerPlayer player = makeSurvivalPlayer(helper, new Vec3(2.5, 1.0, 4.5));
        player.setData(ModAttachments.HUNTER_RANK, HunterRanks.MAX_RANK);
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(6, 1, 4));
        hunter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(hunter.getTarget() == null, Component.literal("A hunter should leave a fellow hunter be"));
                    player.setData(ModAttachments.WEREWOLF_FORM, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(hunter.getTarget() == player, Component.literal("A hunter should hunt a werewolf, even one of their own")))
                .thenExecute(() -> {
                    WerewolfBelt.turnBack(player);
                    helper.assertTrue(hunter.getTarget() == null, Component.literal("Turned back, they're a hunter again"));
                    hunter.discard();
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }
}
