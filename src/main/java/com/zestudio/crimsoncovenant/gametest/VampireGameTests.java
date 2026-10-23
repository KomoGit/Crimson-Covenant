package com.zestudio.crimsoncovenant.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.MasterHunter;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.VampireBat;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/// GameTests for vampires and player vampirism. Registered in ModGameTests.
final class VampireGameTests {
    private VampireGameTests() {}

    // Test worlds are in daylight, so every test vampire gets a helmet to stop it burning
    private static Vampire spawnVampire(GameTestHelper helper, BlockPos pos) {
        Vampire vampire = helper.spawn(ModEntities.VAMPIRE.get(), pos);
        vampire.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        vampire.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return vampire;
    }

    static void healsByKilling(GameTestHelper helper) {
        Vampire vampire = spawnVampire(helper, new BlockPos(2, 1, 4));
        float startHealth = vampire.getMaxHealth() * 0.3F;
        vampire.setHealth(startHealth);
        helper.assertTrue(vampire.isThirsty(), Component.literal("Vampire at 30% health should be thirsty"));
        Chicken chicken = helper.spawnWithNoFreeWill(EntityTypes.CHICKEN, new BlockPos(4, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(!chicken.isAlive(), Component.literal("Thirsty vampire should kill the chicken"));
            helper.assertTrue(vampire.getHealth() >= startHealth + 3.0F, Component.literal("Vampire should heal after the kill"));
        });
    }

    static void hasResistance(GameTestHelper helper) {
        Vampire vampire = spawnVampire(helper, new BlockPos(4, 1, 4));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(vampire.hasEffect(MobEffects.RESISTANCE), Component.literal("Vampires should have permanent Resistance"));
            // Resistance II cuts damage by 40%: a 10 damage hit (that ignores armor) should only take 6 health
            vampire.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 10.0F);
            helper.assertTrue(Math.abs(vampire.getMaxHealth() - vampire.getHealth() - 6.0F) < 0.01F,
                    Component.literal("Expected 6 damage after Resistance, took " + (vampire.getMaxHealth() - vampire.getHealth())));
            helper.succeed();
        });
    }

    static void ignoresAnimalsWhenHealthy(GameTestHelper helper) {
        Vampire vampire = spawnVampire(helper, new BlockPos(2, 1, 4));
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 4));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(vampire.getTarget() != cow && cow.getHealth() == cow.getMaxHealth(), Component.literal("A healthy vampire should leave animals alone"));
            helper.succeed();
        });
    }

    static void attacksVillager(GameTestHelper helper) {
        Vampire vampire = spawnVampire(helper, new BlockPos(2, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 4));
        helper.succeedWhen(() -> helper.assertTrue(vampire.getTarget() == villager, Component.literal("Vampire should target the villager")));
    }

    static void breaksDoors(GameTestHelper helper) {
        // A stone wall across the arena with an oak door in the middle, villager on the far side
        buildWallWithDoor(helper);
        BlockPos doorPos = new BlockPos(4, 1, 4);
        setDoor(helper, doorPos, true);

        Vampire vampire = spawnVampire(helper, new BlockPos(1, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(7, 1, 4));
        // Like a villager running inside: the vampire spots it through the open door, then the door shuts.
        // The vampire can't see its prey any more, but should keep at the door until it breaks.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vampire.getTarget() == villager, Component.literal("Vampire should spot the villager")))
                .thenExecute(() -> {
                    setDoor(helper, doorPos, false);
                    // Stay on this villager: prey from neighbouring tests mustn't pull it away from the door
                    vampire.targetSelector.removeAllGoals(goal -> true);
                    vampire.setTarget(villager);
                })
                .thenWaitUntil(() -> helper.assertBlockNotPresent(Blocks.OAK_DOOR, doorPos))
                .thenSucceed();
    }

    static void finishesBreakingDoorWithoutTarget(GameTestHelper helper) {
        buildWallWithDoor(helper);
        BlockPos doorPos = new BlockPos(4, 1, 4);
        setDoor(helper, doorPos, false);

        // Like a vampire roaming a village at night: no prey in sight, just walking into a closed door
        Vampire vampire = spawnVampire(helper, new BlockPos(2, 1, 4));
        BlockPos farSide = helper.absolutePos(new BlockPos(7, 1, 4));
        vampire.getNavigation().moveTo(farSide.getX() + 0.5, farSide.getY(), farSide.getZ() + 0.5, 1.0);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(Blocks.OAK_DOOR, doorPos));
    }

    private static void buildWallWithDoor(GameTestHelper helper) {
        for (int z = 0; z < 9; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
    }

    private static void setDoor(GameTestHelper helper, BlockPos doorPos, boolean open) {
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, open);
        helper.setBlock(doorPos, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(doorPos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static void infectionTurnsPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vampirism.infect(player, 10);
        for (int i = 0; i < 20; i++) {
            Vampirism.tickInfection(player);
        }
        helper.assertTrue(Vampirism.isVampire(player), Component.literal("Player should be a vampire once the infection runs out"));
        helper.assertTrue(!player.hasEffect(ModEffects.SANGUINE_VAMPIRIS), Component.literal("The infection effect should be gone after turning"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void infectionSurvivesMilk(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vampirism.infect(player, Vampirism.INFECTION_DURATION);
        Vampirism.tickInfection(player);
        helper.assertTrue(player.hasEffect(ModEffects.SANGUINE_VAMPIRIS), Component.literal("Infected player should show the effect"));
        // Drinking milk clears every effect this way
        player.removeAllEffects();
        Vampirism.tickInfection(player);
        helper.assertTrue(player.hasEffect(ModEffects.SANGUINE_VAMPIRIS), Component.literal("Milk should not cure the infection"));
        helper.assertTrue(Vampirism.isInfected(player), Component.literal("Player should still be infected"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void killInfectsPlayer(GameTestHelper helper) {
        Vampire vampire = spawnVampire(helper, new BlockPos(2, 1, 4));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.die(helper.getLevel().damageSources().mobAttack(vampire));
        helper.assertTrue(Vampirism.isInfected(player), Component.literal("A player killed by a vampire should be infected"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void dyingInfectedTurnsPlayer(GameTestHelper helper) {
        // Any death counts, not only one at a vampire's hands
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vampirism.infect(player, Vampirism.INFECTION_DURATION);
        player.die(helper.getLevel().damageSources().generic());
        helper.assertTrue(Vampirism.isVampire(player), Component.literal("An infected player who dies should rise as a vampire"));
        helper.assertTrue(!Vampirism.isInfected(player), Component.literal("The infection countdown should be over once turned"));
        removePlayer(helper, player);

        // Killed by a vampire while already infected: turned, not infected all over again
        Vampire vampire = spawnVampire(helper, new BlockPos(2, 1, 4));
        ServerPlayer bitten = helper.makeMockServerPlayerInLevel();
        Vampirism.infect(bitten, Vampirism.INFECTION_DURATION);
        bitten.die(helper.getLevel().damageSources().mobAttack(vampire));
        helper.assertTrue(Vampirism.isVampire(bitten), Component.literal("An infected player killed by a vampire should rise as a vampire"));
        removePlayer(helper, bitten);
        helper.succeed();
    }

    private static MasterVampire spawnMasterVampire(GameTestHelper helper, BlockPos pos) {
        MasterVampire master = helper.spawn(ModEntities.MASTER_VAMPIRE.get(), pos);
        master.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return master;
    }

    private static void buildGlassWall(GameTestHelper helper) {
        for (int z = 0; z < 9; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.GLASS);
            }
        }
    }

    static void masterDrainsLifeNearby(GameTestHelper helper) {
        // The villager is behind glass: out of reach, but close enough for the aura
        buildGlassWall(helper);
        MasterVampire master = spawnMasterVampire(helper, new BlockPos(2, 1, 4));
        float startHealth = 40.0F;
        master.setHealth(startHealth);
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("Villager near the master should lose health"));
            helper.assertTrue(master.getHealth() > startHealth, Component.literal("Master should heal from draining"));
        });
    }

    static void masterSummonsMinionsWhenLow(GameTestHelper helper) {
        buildGlassWall(helper);
        MasterVampire master = spawnMasterVampire(helper, new BlockPos(1, 1, 4));
        master.setHealth(master.getMaxHealth() * 0.25F);
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(7, 1, 4));
        // Glass blocks a mob's line of sight, so give the master its target directly
        master.setTarget(villager);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<Mob> minions = master.getMinions().stream()
                            .map(uuid -> helper.getLevel().getEntity(uuid))
                            .filter(Mob.class::isInstance).map(Mob.class::cast).toList();
                    helper.assertTrue(minions.stream().anyMatch(m -> m.getType() == EntityTypes.SPIDER), Component.literal("Master should summon spiders"));
                    helper.assertTrue(minions.stream().noneMatch(m -> m.getType() == EntityTypes.WOLF), Component.literal("Master should no longer summon wolves"));
                    helper.assertTrue(minions.stream().anyMatch(m -> m.getType() == ModEntities.VAMPIRE_BAT.get()), Component.literal("Master should summon vampire bats"));
                    helper.assertTrue(minions.stream().allMatch(m -> m.getTarget() == villager), Component.literal("Minions should attack the master's target"));
                })
                .thenExecute(() -> master.kill(helper.getLevel()))
                .thenWaitUntil(() -> {
                    helper.assertEntityNotPresent(EntityTypes.SPIDER);
                    helper.assertEntityNotPresent(EntityTypes.WOLF);
                    helper.assertEntityNotPresent(ModEntities.VAMPIRE_BAT.get());
                })
                .thenSucceed();
    }

    static void masterSparesItsMinions(GameTestHelper helper) {
        buildGlassWall(helper);
        // Badly hurt, so it's both summoning and thirsty enough to hunt animals (wolves count as animals)
        MasterVampire master = spawnMasterVampire(helper, new BlockPos(1, 1, 4));
        master.setHealth(master.getMaxHealth() * 0.25F);
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(7, 1, 4));
        master.setTarget(villager);
        List<UUID> summoned = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!master.getMinions().isEmpty(), Component.literal("Master should summon its minions")))
                .thenExecute(() -> summoned.addAll(master.getMinions()))
                .thenExecuteFor(100, () -> {
                    LivingEntity target = master.getTarget();
                    helper.assertTrue(target == null || !Vampire.isFamiliar(target),
                            Component.literal("Master should never target its own minions, but targeted " + target));
                    for (UUID uuid : summoned) {
                        Entity minion = helper.getLevel().getEntity(uuid);
                        helper.assertTrue(minion != null && minion.isAlive(), Component.literal("Master's minions should survive, lost " + uuid));
                    }
                })
                .thenSucceed();
    }

    static void dropsTokenOfBloodWhenKilledByPlayer(GameTestHelper helper) {
        Vampire vampire = spawnVampire(helper, new BlockPos(4, 1, 4));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        vampire.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        removePlayer(helper, player);
        helper.assertItemEntityPresent(ModItems.TOKEN_OF_BLOOD.get());
        helper.succeed();
    }

    static void holyWaterBurnsOnlyUndead(GameTestHelper helper) {
        // Husks don't burn in daylight, so any fire comes from the Holy Water
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(3, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(5, 1, 4));
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(4, 1, 6));
        BlockPos above = helper.absolutePos(new BlockPos(4, 3, 4));
        ThrownSplashPotion potion = new ThrownSplashPotion(helper.getLevel(), above.getX() + 0.5, above.getY(), above.getZ() + 0.5,
                PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.HOLY_WATER));
        potion.setDeltaMovement(0.0, -0.5, 0.0);
        helper.getLevel().addFreshEntity(potion);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(potion.isRemoved(), Component.literal("Potion should have shattered")))
                .thenExecute(() -> {
                    helper.assertTrue(husk.hasEffect(ModEffects.HOLY_FLAME), Component.literal("Holy Water should set undead alight with Holy Flame"));
                    helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), Component.literal("Holy Water should hurt undead"));
                    helper.assertTrue(!villager.hasEffect(ModEffects.HOLY_FLAME) && villager.getHealth() == villager.getMaxHealth(), Component.literal("Holy Water should not hurt villagers"));
                    helper.assertTrue(!cow.hasEffect(ModEffects.HOLY_FLAME) && cow.getHealth() == cow.getMaxHealth(), Component.literal("Holy Water should not hurt animals"));
                })
                .thenSucceed();
    }

    static void holyFlameSurvivesWater(GameTestHelper helper) {
        // Standing in water would put out normal fire straight away
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.WATER);
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(4, 1, 4));
        husk.addEffect(new MobEffectInstance(ModEffects.HOLY_FLAME, 100));
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(husk.isInWater(), Component.literal("Husk should be standing in water"));
            helper.assertTrue(husk.hasEffect(ModEffects.HOLY_FLAME), Component.literal("Water should not put out Holy Flame"));
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), Component.literal("Holy Flame should keep burning in water"));
            helper.succeed();
        });
    }

    static void vampireBatAttacksPrey(GameTestHelper helper) {
        VampireBat bat = helper.spawn(ModEntities.VAMPIRE_BAT.get(), new BlockPos(2, 2, 4));
        bat.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 4));
        helper.succeedWhen(() -> helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("Vampire bat should bite the villager")));
    }

    static void holyWaterSlowsAndStripsResistance(GameTestHelper helper) {
        Vampire vampire = spawnVampire(helper, new BlockPos(4, 1, 4));
        double normalSpeed = vampire.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vampire.hasEffect(MobEffects.RESISTANCE), Component.literal("Vampire should start with Resistance")))
                .thenExecute(() -> ModEffects.HOLY_WATER.get().applyInstantaneousEffect(helper.getLevel(), null, null, vampire, 0, 1.0))
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(vampire.hasEffect(ModEffects.HOLY_FLAME), Component.literal("Holy Water should leave Holy Flame"));
                    helper.assertTrue(!vampire.hasEffect(MobEffects.RESISTANCE), Component.literal("Holy Water should strip Resistance, and keep it off while burning"));
                    helper.assertTrue(vampire.getAttributeValue(Attributes.MOVEMENT_SPEED) < normalSpeed * 0.8,
                            Component.literal("Holy Flame should slow the vampire"));
                })
                .thenSucceed();
    }

    static void lingeringHolyWaterBurnsUndead(GameTestHelper helper) {
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(4, 1, 4));
        BlockPos above = helper.absolutePos(new BlockPos(4, 3, 4));
        ThrownLingeringPotion potion = new ThrownLingeringPotion(helper.getLevel(), above.getX() + 0.5, above.getY(), above.getZ() + 0.5,
                PotionContents.createItemStack(Items.LINGERING_POTION, ModPotions.HOLY_WATER));
        potion.setDeltaMovement(0.0, -0.5, 0.0);
        helper.getLevel().addFreshEntity(potion);
        helper.succeedWhen(() -> {
            helper.assertTrue(!helper.getEntities(EntityTypes.AREA_EFFECT_CLOUD).isEmpty(), Component.literal("Lingering Holy Water should leave a cloud"));
            helper.assertTrue(husk.hasEffect(ModEffects.HOLY_FLAME), Component.literal("The cloud should set undead alight"));
        });
    }

    static void vampireBecomesMasterByKillingMasterHunter(GameTestHelper helper) {
        MasterHunter hunter = helper.spawnWithNoFreeWill(ModEntities.MASTER_HUNTER.get(), new BlockPos(6, 1, 4));
        hunter.setHealth(1.0F);
        Vampire vampire = spawnVampire(helper, new BlockPos(2, 1, 4));
        vampire.targetSelector.removeAllGoals(goal -> true);
        vampire.setTarget(hunter);
        helper.succeedWhen(() -> {
            helper.assertTrue(!hunter.isAlive(), Component.literal("Vampire should slay the Master Hunter"));
            helper.assertTrue(helper.getEntities(ModEntities.MASTER_VAMPIRE.get()).size() == 1, Component.literal("The vampire should rise as a Master Vampire"));
            helper.assertTrue(helper.getEntities(ModEntities.VAMPIRE.get()).isEmpty(), Component.literal("The regular vampire should be gone"));
        });
    }

    static void masterVampireBloodArmor(GameTestHelper helper) {
        MasterVampire healthy = spawnMasterVampire(helper, new BlockPos(2, 1, 2));
        MasterVampire wounded = spawnMasterVampire(helper, new BlockPos(6, 1, 6));
        wounded.setHealth(20.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!healthy.isBloodArmored() && wounded.isBloodArmored(), Component.literal("Only the wounded Master should have blood armor"));
            healthy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 10.0F);
            wounded.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 10.0F);
            float healthyLoss = healthy.getMaxHealth() - healthy.getHealth();
            float woundedLoss = 20.0F - wounded.getHealth();
            // Resistance II takes 40% off; blood armor takes another 30% off what's left
            helper.assertTrue(Math.abs(healthyLoss - 6.0F) < 0.01F, Component.literal("Healthy Master should take 6, took " + healthyLoss));
            helper.assertTrue(Math.abs(woundedLoss - 4.2F) < 0.01F, Component.literal("Blood armor should cut that to 4.2, took " + woundedLoss));
            helper.succeed();
        });
    }

    static void masterVampireBatSwarm(GameTestHelper helper) {
        // The villager is out of reach behind glass and too far for the life drain; only the swarm can get it
        buildGlassWall(helper);
        MasterVampire master = spawnMasterVampire(helper, new BlockPos(0, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(8, 1, 4));
        master.beginBatSwarm(villager);
        helper.startSequence()
                .thenExecuteAfter(10, () -> helper.assertTrue(helper.getLevel().getEntities(ModEntities.SWARM_BAT.get(), bat -> bat.getOwner() == master).isEmpty(),
                        Component.literal("The swarm should only be released after the wind-up")))
                .thenWaitUntil(() -> helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("The swarm should fly through the wall and hit the villager")))
                // The chill sets in from the effect's first tick
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(villager.hasEffect(ModEffects.BLOOD_LOSS), Component.literal("Swarm bites should cause Blood Loss"));
                    helper.assertTrue(villager.getTicksFrozen() > 0, Component.literal("Blood Loss should leave its victim cold"));
                })
                // Other tests' Master Vampires may be launching swarms too, so only count this one's bats
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getEntities(ModEntities.SWARM_BAT.get(), bat -> bat.getOwner() == master).isEmpty(),
                        Component.literal("Swarm bats should vanish after 5 seconds")))
                .thenSucceed();
    }

    static void headlessHorsemanRidesWithGoldenSpear(GameTestHelper helper) {
        HeadlessHorseman horseman = ModEntities.HEADLESS_HORSEMAN.get().spawn(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), EntitySpawnReason.SPAWN_ITEM_USE);
        helper.assertTrue(horseman != null, Component.literal("Horseman should spawn"));
        helper.assertTrue(!horseman.isBaby(), Component.literal("Horseman should never be a baby"));
        helper.assertTrue(horseman.getVehicle() instanceof ZombieHorse, Component.literal("Horseman should ride a zombie horse"));
        helper.assertTrue(horseman.getVehicle() instanceof ZombieHorse steed && steed.getItemBySlot(EquipmentSlot.BODY).is(Items.GOLDEN_HORSE_ARMOR),
                Component.literal("The horse should wear golden barding"));
        helper.assertTrue(horseman.getItemBySlot(EquipmentSlot.HEAD).is(Items.JACK_O_LANTERN), Component.literal("Horseman's head should be a jack o'lantern"));
        ItemStack spear = horseman.getMainHandItem();
        helper.assertTrue(spear.is(Items.GOLDEN_SPEAR) && spear.isEnchanted(), Component.literal("Horseman should carry an enchanted golden spear"));
        // Don't let it ride off into neighbouring tests
        horseman.getVehicle().discard();
        horseman.discard();
        helper.succeed();
    }

    static void headlessHorsemanHuntsHunters(GameTestHelper helper) {
        HeadlessHorseman horseman = helper.spawn(ModEntities.HEADLESS_HORSEMAN.get(), new BlockPos(2, 1, 4));
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(6, 1, 4));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(horseman.getTarget() == hunter, Component.literal("Horseman should hunt hunters")))
                .thenExecute(horseman::discard)
                .thenSucceed();
    }

    static void ghostOnlyHurtByHolyWater(GameTestHelper helper) {
        Ghost ghost = helper.spawnWithNoFreeWill(ModEntities.GHOST.get(), new BlockPos(4, 2, 4));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        for (DamageSource source : List.of(level.damageSources().mobAttack(zombie), level.damageSources().magic(), level.damageSources().onFire(),
                level.damageSources().generic(), level.damageSources().fall())) {
            helper.assertTrue(!ghost.hurtServer(level, source, 10.0F), Component.literal("Ghost should shrug off " + source.getMsgId()));
        }
        helper.assertTrue(ghost.getHealth() == ghost.getMaxHealth(), Component.literal("Normal damage should not hurt a ghost"));
        ModEffects.HOLY_WATER.get().applyInstantaneousEffect(level, null, null, ghost, 0, 1.0);
        helper.assertTrue(!ghost.isAlive(), Component.literal("Holy Water should banish a ghost outright"));
        // Ghosts leave ectoplasm behind
        helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.ECTOPLASM.get()));
    }

    static void ghostPassesThroughWalls(GameTestHelper helper) {
        for (int z = 0; z < 9; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
        Ghost ghost = helper.spawn(ModEntities.GHOST.get(), new BlockPos(1, 2, 4));
        // No targets, so it can't swoop off through the walls into neighbouring tests
        ghost.targetSelector.removeAllGoals(goal -> true);
        BlockPos farSide = helper.absolutePos(new BlockPos(7, 2, 4));
        ghost.getMoveControl().setWantedPosition(farSide.getX() + 0.5, farSide.getY(), farSide.getZ() + 0.5, 0.6);
        helper.succeedWhen(() -> helper.assertTrue(helper.relativeVec(ghost.position()).x > 5.5, Component.literal("Ghost should drift through the stone wall")));
    }

    static void ghostGivesNausea(GameTestHelper helper) {
        Ghost ghost = helper.spawnWithNoFreeWill(ModEntities.GHOST.get(), new BlockPos(4, 1, 4));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(ghost.getX(), ghost.getY(), ghost.getZ());
        helper.succeedWhen(() -> {
            helper.assertTrue(player.hasEffect(MobEffects.NAUSEA), Component.literal("Passing through a ghost should cause Nausea"));
            removePlayer(helper, player);
        });
    }

    static void spawnsInOverworldBiomes(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        helper.assertTrue(spawnsIn(biomes.getOrThrow(Biomes.PLAINS).value()), Component.literal("Vampires should spawn in plains"));
        helper.assertTrue(spawnsIn(biomes.getOrThrow(Biomes.DRIPSTONE_CAVES).value()), Component.literal("Vampires should spawn in caves"));
        helper.assertTrue(!spawnsIn(biomes.getOrThrow(Biomes.MUSHROOM_FIELDS).value()), Component.literal("Vampires should not spawn on mushroom islands"));
        helper.assertTrue(!spawnsIn(biomes.getOrThrow(Biomes.NETHER_WASTES).value()), Component.literal("Vampires should not spawn in the Nether"));
        helper.assertTrue(spawnsIn(biomes.getOrThrow(Biomes.FOREST).value(), ModEntities.HEADLESS_HORSEMAN.get())
                && spawnsIn(biomes.getOrThrow(Biomes.DARK_FOREST).value(), ModEntities.HEADLESS_HORSEMAN.get()),
                Component.literal("The Headless Horseman should spawn in forests"));
        helper.assertTrue(!spawnsIn(biomes.getOrThrow(Biomes.PLAINS).value(), ModEntities.HEADLESS_HORSEMAN.get()),
                Component.literal("The Headless Horseman should only spawn in forests"));
        helper.succeed();
    }

    private static boolean spawnsIn(Biome biome) {
        return spawnsIn(biome, ModEntities.VAMPIRE.get());
    }

    private static boolean spawnsIn(Biome biome, EntityType<?> type) {
        // In 26.x a biome's mob spawns are an environment attribute rather than a plain list
        MobSpawnSettings spawns = biome.getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
        return spawns.getMobsToSpawn(MobCategory.MONSTER).unwrap().stream()
                .anyMatch(spawner -> spawner.value().type() == type);
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
