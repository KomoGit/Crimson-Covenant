package com.zestudio.crimsoncovenant.gametest;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.DecorBlock;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.MasterHunter;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.SilverArrow;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModVillagers;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.world.FactionVillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.phys.AABB;

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
}
