package com.zestudio.crimsoncovenant.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.skill.UpgradeEffects;
import com.zestudio.crimsoncovenant.effect.BloodFlightEffect;
import com.zestudio.crimsoncovenant.block.AncientTranslatorBlock;
import com.zestudio.crimsoncovenant.block.GarlicBraidBlock;
import com.zestudio.crimsoncovenant.block.BloodAltarBlock;
import com.zestudio.crimsoncovenant.effect.BlackBloodEffect;
import com.zestudio.crimsoncovenant.effect.GarlicEffect;
import com.zestudio.crimsoncovenant.entity.BloodBat;
import com.zestudio.crimsoncovenant.entity.BloodOrb;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.CrimsonLightning;
import com.zestudio.crimsoncovenant.entity.HemorrhageZone;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.SwarmBat;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.item.BloodBottleItem;
import com.zestudio.crimsoncovenant.item.BloodSpearItem;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.PossessionState;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.registry.ModVillagers;
import com.zestudio.crimsoncovenant.skill.AncientTomeItem;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.skill.BloodSkills;
import com.zestudio.crimsoncovenant.skill.CreatureAbilities;
import com.zestudio.crimsoncovenant.skill.DeathMarch;
import com.zestudio.crimsoncovenant.skill.Enthrall;
import com.zestudio.crimsoncovenant.skill.NightOfTerror;
import com.zestudio.crimsoncovenant.skill.Possession;
import com.zestudio.crimsoncovenant.skill.SkillKeeper;
import com.zestudio.crimsoncovenant.skill.SkillSlots;
import com.zestudio.crimsoncovenant.skill.SkillUpgrade;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.VampireAge;
import com.zestudio.crimsoncovenant.vampirism.GarlicWard;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.BloodMoon;
import com.zestudio.crimsoncovenant.effect.CrimsonShroudEffect;
import com.zestudio.crimsoncovenant.block.AncientTranslatorBlockEntity;
import com.zestudio.crimsoncovenant.block.IronMaidenBlock;
import com.zestudio.crimsoncovenant.block.IronMaidenBlockEntity;
import com.zestudio.crimsoncovenant.entity.WhipLash;
import com.zestudio.crimsoncovenant.effect.FlightLift;
import com.zestudio.crimsoncovenant.item.WhipItem;
import com.zestudio.crimsoncovenant.item.Silvering;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/// Tests for the vampire's blood bar, the blood skills, the Ancient Translator, and where tomes come from
final class SkillGameTests {
    private SkillGameTests() {}

    /// Mock players are always in creative; take away its creative powers so flight and item use behave as in survival
    static ServerPlayer makePlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.getAbilities().instabuild = false;
        player.getAbilities().invulnerable = false;
        return player;
    }

    static ServerPlayer makeVampire(GameTestHelper helper, int blood) {
        ServerPlayer player = makePlayer(helper);
        player.setData(ModAttachments.IS_VAMPIRE, true);
        BloodPool.set(player, blood);
        return player;
    }

    static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /// Wears the skill in the first slot and presses its key
    static boolean castSkill(ServerPlayer player, BloodSkill skill) {
        ItemStack stack = new ItemStack(skill.skillItem());
        SkillSlots.set(player, 0, stack);
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(stack));
        return BloodSkills.tryCast(player, 0);
    }

    // ---- Blood ----

    static void killsFillTheBloodBar(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        player.setHealth(10.0F);
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(4, 1, 4));
        cow.die(helper.getLevel().damageSources().playerAttack(player));
        helper.assertValueEqual(BloodPool.get(player), BloodPool.bloodFrom(cow), "blood after killing a cow");
        helper.assertValueEqual(player.getHealth(), 10.0F + BloodPool.KILL_HEAL, "health after killing a cow (half a heart back)");

        // The undead have no blood to give
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(3, 1, 4));
        zombie.die(helper.getLevel().damageSources().playerAttack(player));
        helper.assertValueEqual(BloodPool.get(player), BloodPool.bloodFrom(cow), "blood after also killing a zombie");

        // Humans don't get anything
        ServerPlayer human = makePlayer(helper);
        Chicken chicken = helper.spawnWithNoFreeWill(EntityTypes.CHICKEN, new BlockPos(5, 1, 4));
        chicken.die(helper.getLevel().damageSources().playerAttack(human));
        helper.assertValueEqual(BloodPool.get(human), 0, "a human's blood after a kill");
        removePlayer(helper, player);
        removePlayer(helper, human);
        helper.succeed();
    }

    static void bloodRefillsSlowly(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        for (int tick = 1; tick <= 400; tick++) {
            player.tickCount = tick;
            BloodPool.tick(player);
        }
        helper.assertValueEqual(BloodPool.get(player), 10, "blood regained in 20 seconds");
        for (int tick = 401; tick <= 4000; tick++) {
            player.tickCount = tick;
            BloodPool.tick(player);
        }
        helper.assertValueEqual(BloodPool.get(player), 30, "blood regained on its own stops at 3 drops");
        BloodPool.set(player, 150);
        helper.assertValueEqual(BloodPool.get(player), 100, "blood can't go over the maximum");
        removePlayer(helper, player);
        helper.succeed();
    }

    static void olderVampiresHoldMoreBlood(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        int[][] expected = {{0, 100, 30}, {10, 110, 36}, {20, 150, 60}, {30, 200, 90}};
        for (int[] age : expected) {
            VampireAge.setDays(player, age[0]);
            BloodPool.set(player, 0);
            for (int tick = 1; tick <= 8000; tick++) {
                player.tickCount = tick;
                BloodPool.tick(player);
            }
            helper.assertValueEqual(BloodPool.get(player), age[2], "blood regained on its own after " + age[0] + " days");
            BloodPool.set(player, 1000);
            helper.assertValueEqual(BloodPool.get(player), age[1], "most blood held after " + age[0] + " days");
        }
        removePlayer(helper, player);
        helper.succeed();
    }

    // ---- Casting ----

    static void skillsCostBloodAndNeedAVampire(GameTestHelper helper) {
        ServerPlayer human = makePlayer(helper);
        BloodPool.set(human, 100);
        helper.assertTrue(!castSkill(human, BloodSkill.FLIGHT), Component.literal("Humans can't use blood skills"));
        helper.assertTrue(!human.hasEffect(ModEffects.BLOOD_FLIGHT), Component.literal("A human shouldn't get to fly"));

        ServerPlayer vampire = makeVampire(helper, 30);
        helper.assertTrue(!castSkill(vampire, BloodSkill.FLIGHT), Component.literal("Flight needs 40 blood"));
        helper.assertValueEqual(BloodPool.get(vampire), 30, "blood after a failed cast");

        BloodPool.set(vampire, 50);
        helper.assertTrue(castSkill(vampire, BloodSkill.FLIGHT), Component.literal("A vampire with 50 blood can fly"));
        helper.assertValueEqual(BloodPool.get(vampire), 10, "blood after casting Flight");
        helper.assertTrue(vampire.hasEffect(ModEffects.BLOOD_FLIGHT), Component.literal("Flight should be active"));
        helper.assertTrue(vampire.getAbilities().flying && vampire.mayFly(), Component.literal("The vampire should be flying"));
        removePlayer(helper, human);
        removePlayer(helper, vampire);
        helper.succeed();
    }

    static void dashLaunchesThePlayer(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 20);
        helper.assertTrue(castSkill(player, BloodSkill.DASH), Component.literal("Dash should cast"));
        helper.assertValueEqual(BloodPool.get(player), 0, "blood after dashing");
        int ticks = 0;
        while (player.getData(ModAttachments.DASH).ticksLeft() > 0 && ticks < 20) {
            com.zestudio.crimsoncovenant.skill.BloodSkills.tickDash(player);
            if (ticks == 0) {
                helper.assertTrue(player.getDeltaMovement().length() > 1.9, Component.literal("A dash should move at 2 blocks a tick"));
            }
            ticks++;
        }
        helper.assertValueEqual(ticks, 5, "dash ticks (10 blocks at 2 a tick)");
        removePlayer(helper, player);
        helper.succeed();
    }

    static void armorOfAncientMatchesDiamond(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 60);
        double before = player.getAttributeValue(Attributes.ARMOR);
        helper.assertTrue(castSkill(player, BloodSkill.ARMOR_OF_ANCIENT), Component.literal("Armor of Ancient should cast"));
        helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR) - before, 20.0, "armor gained (a full diamond set gives 20)");
        helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 8.0, "armor toughness (diamond gives 8)");
        helper.assertTrue(!castSkill(player, BloodSkill.ARMOR_OF_ANCIENT), Component.literal("Can't recast while it's active"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void daywalkerLastsADay(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        helper.assertTrue(castSkill(player, BloodSkill.DAYWALKER), Component.literal("Daywalker should cast"));
        helper.assertValueEqual(BloodPool.get(player), 0, "blood after Daywalker");
        helper.assertValueEqual(player.getEffect(ModEffects.DAYWALKER).getDuration(), 24000, "Daywalker duration");
        removePlayer(helper, player);
        helper.succeed();
    }

    static void holyWaterDispelsSkills(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        castSkill(player, BloodSkill.DAYWALKER);
        BloodPool.set(player, 100);
        castSkill(player, BloodSkill.ARMOR_OF_ANCIENT);
        BloodPool.set(player, 100);
        castSkill(player, BloodSkill.FLIGHT);
        ModEffects.HOLY_WATER.get().applyInstantaneousEffect(helper.getLevel(), null, null, player, 0, 1.0);
        helper.assertTrue(!player.hasEffect(ModEffects.DAYWALKER) && !player.hasEffect(ModEffects.ANCIENT_ARMOR) && !player.hasEffect(ModEffects.BLOOD_FLIGHT),
                Component.literal("Holy Water should wash every active skill away"));
        helper.assertTrue(!player.mayFly(), Component.literal("No more flight after Holy Water"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void bloodSpearRefundsOnKill(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 10);
        helper.assertTrue(castSkill(player, BloodSkill.BLOOD_SPEAR), Component.literal("Blood Spear should cast"));
        helper.assertValueEqual(BloodPool.get(player), 0, "blood after conjuring the spear");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // A spear aimed straight at a chicken a block away. It's ticked by hand so the mock player can be
        // removed right away: while any player exists, monsters far from it (in other tests) despawn.
        Chicken chicken = helper.spawnWithNoFreeWill(EntityTypes.CHICKEN, new BlockPos(5, 1, 4));
        BloodSpear spear = new BloodSpear(helper.getLevel(), player);
        Vec3 from = helper.absoluteVec(new Vec3(3.5, 1.3, 4.5));
        spear.setPos(from);
        spear.shoot(chicken.getX() - from.x, chicken.getY(0.5) - from.y, chicken.getZ() - from.z, 2.0F, 0.0F);
        helper.getLevel().addFreshEntity(spear);
        for (int i = 0; i < 5 && spear.isAlive(); i++) {
            spear.tick();
        }
        int blood = BloodPool.get(player);
        removePlayer(helper, player);
        helper.assertTrue(!chicken.isAlive(), Component.literal("The spear should kill the chicken"));
        helper.assertValueEqual(blood, BloodSpear.BLOOD_REFUND + BloodPool.bloodFrom(chicken), "blood back from the spear plus the kill");
        helper.succeed();
    }

    static void bloodSpearIsThrownLikeATrident(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 10);
        // Inside the test, where the thrown spear can be found (mock players start at the world spawn)
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertTrue(castSkill(player, BloodSkill.BLOOD_SPEAR), Component.literal("Blood Spear should cast"));
        ItemStack spear = player.getMainHandItem();
        helper.assertTrue(spear.is(ModItems.BLOOD_SPEAR.get()), Component.literal("Casting should put the spear in hand"));
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.STICK)), Component.literal("Whatever was in hand moves into the inventory"));
        helper.assertTrue(spear.getUseAnimation() == ItemUseAnimation.TRIDENT, Component.literal("It should be drawn back like a trident"));

        int duration = spear.getUseDuration(player);
        AABB around = player.getBoundingBox().inflate(4.0);
        // Let go too soon and nothing is thrown
        spear.releaseUsing(level, player, duration - (BloodSpearItem.MIN_DRAW - 5));
        helper.assertTrue(level.getEntitiesOfClass(BloodSpear.class, around).isEmpty() && !spear.isEmpty(), Component.literal("A half-drawn spear isn't thrown"));
        // Drawn back long enough, it flies
        spear.releaseUsing(level, player, duration - (BloodSpearItem.MIN_DRAW + 5));
        List<BloodSpear> thrown = level.getEntitiesOfClass(BloodSpear.class, around);
        helper.assertTrue(thrown.size() == 1 && spear.isEmpty(), Component.literal("A fully drawn spear should be thrown and leave the hand ("
                + thrown.size() + " thrown, " + spear.getCount() + " left in hand)"));
        // Thrown from wherever the mock player stands, outside the test: get rid of it
        thrown.forEach(BloodSpear::discard);
        removePlayer(helper, player);
        helper.succeed();
    }

    static void bloodSpearDrivesAndSlams(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 0);
        // A stone wall a block and a half behind one zombie, which is speared east (kept well clear of the other
        // zombie's diagonal ride)
        for (int z = 6; z <= 8; z++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        Zombie slammed = spawnSturdyZombie(helper, new BlockPos(1, 1, 7));
        // The other is speared corner to corner, the longest clear run in this 9x9 test area, so its whole ride
        // (about 6.5 blocks) stays inside it
        Zombie driven = spawnSturdyZombie(helper, new BlockPos(1, 1, 1));
        Vec3 drivenStart = driven.position();
        throwSpearAt(level, player, slammed, new Vec3(1.0, 0.0, 0.0));
        throwSpearAt(level, player, driven, new Vec3(1.0, 0.0, 1.0).normalize());
        removePlayer(helper, player);
        helper.runAfterDelay(BloodSpear.DRIVE_TICKS + 4, () -> {
            float slamDamage = slammed.getMaxHealth() - slammed.getHealth();
            float driveDamage = driven.getMaxHealth() - driven.getHealth();
            helper.assertTrue(driveDamage > 0.0F, Component.literal("The spear should hit the zombie in the open"));
            helper.assertTrue(slamDamage > driveDamage + 4.0F, Component.literal("Slammed into the wall should hurt more: " + slamDamage + " vs " + driveDamage
                    + " (open-ground zombie from " + helper.relativeVec(drivenStart) + " to " + helper.relativeVec(driven.position()) + ", staggered " + driven.hasEffect(MobEffects.SLOWNESS) + ")"));
            helper.assertTrue(slammed.hasEffect(MobEffects.SLOWNESS), Component.literal("A slammed zombie should be staggered"));
            double carried = driven.position().subtract(drivenStart).horizontalDistance();
            helper.assertTrue(carried > 3.0, Component.literal("The spear should carry the zombie back, but it moved " + carried));
            slammed.discard();
            driven.discard();
            helper.succeed();
        });
    }

    static void bloodSpearPiercesThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 0);
        // Two zombies in a row, three blocks apart: the spear runs through the first and on into the second
        Zombie first = spawnSturdyZombie(helper, new BlockPos(1, 1, 4));
        Zombie second = spawnSturdyZombie(helper, new BlockPos(4, 1, 4));
        first.removeFreeWill();
        second.removeFreeWill();
        throwSpearAt(level, player, first, new Vec3(1.0, 0.0, 0.0));
        removePlayer(helper, player);
        helper.assertTrue(first.getHealth() < first.getMaxHealth(), Component.literal("The spear should strike the first zombie"));
        helper.assertTrue(second.getHealth() < second.getMaxHealth(), Component.literal("The spear should run through it into the second"));
        first.discard();
        second.discard();
        helper.succeed();
    }

    static void whipsReachFarAndSlam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack whip = new ItemStack(ModItems.WHIP.get());
        helper.assertTrue(Silvering.isSilvered(whip), Component.literal("A whip is silvered by its making"));
        double reach = whip.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ENTITY_INTERACTION_RANGE, 3.0, EquipmentSlot.MAINHAND);
        helper.assertValueEqual(reach, 6.0, "a whip's reach");

        for (int z = 3; z <= 5; z++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        ServerPlayer player = makePlayer(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(1.5, 1.0, 4.5));
        player.teleportTo(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, whip);
        Zombie zombie = spawnSturdyZombie(helper, new BlockPos(3, 1, 4));
        float blow = 2.0F;
        zombie.hurtServer(level, level.damageSources().playerAttack(player), blow);
        // The lash cracks out with the swing, but not twice at once
        WhipLash lash = WhipLash.crack(player);
        helper.assertTrue(lash != null && WhipLash.crack(player) == null, Component.literal("A swing should crack one lash out"));
        float[] slammed = new float[1];
        helper.startSequence()
                .thenExecuteAfter(WhipItem.DRIVE_TICKS + 4, () -> {
                    helper.assertTrue(zombie.getMaxHealth() - zombie.getHealth() > blow + 1.0F,
                            Component.literal("Cracked back into the wall, the zombie should be slammed against it"));
                    helper.assertTrue(BloodSpear.isStaggered(zombie), Component.literal("The slammed zombie should be staggered"));
                    slammed[0] = zombie.getHealth();
                })
                // Once, not over and over: the slam is the wielder's blow too, and mustn't drive it back into the wall
                .thenExecuteAfter(20, () -> helper.assertValueEqual(zombie.getHealth(), slammed[0], "the zombie's health a second after its one slam"))
                .thenExecute(() -> {
                    removePlayer(helper, player);
                    zombie.discard();
                })
                .thenSucceed();
    }

    static void whipLashLandsTheBlow(GameTestHelper helper) {
        ServerPlayer player = makePlayer(helper);
        // Facing east, down the test, at a zombie four blocks off: beyond an arm's reach, within the whip's
        Vec3 stand = helper.absoluteVec(new Vec3(1.5, 1.0, 4.5));
        player.snapTo(stand.x, stand.y, stand.z, -90.0F, 0.0F);
        player.teleportTo(stand.x, stand.y, stand.z);
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHIP.get()));
        Zombie zombie = spawnSturdyZombie(helper, new BlockPos(4, 1, 4));
        zombie.removeFreeWill();
        float health = zombie.getHealth();
        // And one beyond it, still within the whip's reach: the lash cuts through to it
        Zombie beyond = spawnSturdyZombie(helper, new BlockPos(6, 1, 4));
        beyond.removeFreeWill();
        WhipLash lash = WhipLash.crack(player);
        helper.assertTrue(lash != null, Component.literal("The whip should crack"));
        helper.assertTrue(WhipLash.isLashing(player) && WhipLash.crack(player) == null, Component.literal("No second lash while the first is out"));
        helper.startSequence()
                .thenExecuteAfter(WhipLash.CRACK_TICK + 2, () -> {
                    helper.assertTrue(zombie.getHealth() < health, Component.literal("The crack should land on the zombie the lash reaches"));
                    helper.assertTrue(beyond.getHealth() < beyond.getMaxHealth(), Component.literal("The lash should cut through to the zombie beyond"));
                })
                .thenWaitUntil(() -> helper.assertTrue(!WhipLash.isLashing(player), Component.literal("The lash should be drawn back in")))
                .thenExecute(() -> {
                    removePlayer(helper, player);
                    zombie.discard();
                    beyond.discard();
                })
                .thenSucceed();
    }

    static void whipReachIsKnownBeforeTheCrack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makePlayer(helper);
        // Facing east, down the test, at a zombie eight blocks off: past the whip's reach, but in its sights
        Vec3 stand = helper.absoluteVec(new Vec3(0.5, 1.0, 4.5));
        player.snapTo(stand.x, stand.y, stand.z, -90.0F, 0.0F);
        player.teleportTo(stand.x, stand.y, stand.z);
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setXRot(0.0F);
        Zombie zombie = spawnSturdyZombie(helper, new BlockPos(8, 1, 4));
        zombie.removeFreeWill();
        helper.assertTrue(WhipLash.reached(level, player).isEmpty(), Component.literal("Eight blocks off is past the whip's reach"));
        helper.assertTrue(WhipLash.along(level, player, 32.0).contains(zombie), Component.literal("The zombie should still be in the whip's sights"));
        Vec3 near = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        zombie.teleportTo(near.x, near.y, near.z);
        helper.assertTrue(WhipLash.reached(level, player).contains(zombie), Component.literal("Four blocks off is within the whip's reach"));
        removePlayer(helper, player);
        zombie.discard();
        helper.succeed();
    }

    static void flightCarriesOffWhatItFliesThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        Zombie zombie = spawnSturdyZombie(helper, new BlockPos(4, 1, 4));
        zombie.removeFreeWill();
        Zombie second = spawnSturdyZombie(helper, new BlockPos(1, 1, 7));
        second.removeFreeWill();
        IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(1, 1, 1));
        // Without wings, passing through is only passing through
        flyPast(helper, player, new Vec3(4.5, 1.5, 1.5), new Vec3(4.5, 1.5, 7.5));
        helper.assertTrue(!FlightLift.snatch(player) && !FlightLift.isLifted(zombie), Component.literal("Without Flight, nothing should be snatched up"));
        player.addEffect(new MobEffectInstance(ModEffects.BLOOD_FLIGHT, 400));
        flyPast(helper, player, new Vec3(1.5, 1.5, 1.5), new Vec3(1.5, 1.5, 1.6));
        helper.assertTrue(!FlightLift.snatch(player) && !FlightLift.isLifted(golem), Component.literal("An Iron Golem is too heavy to carry off"));
        // Flat out, straight through the zombie between one tick and the next
        flyPast(helper, player, new Vec3(4.5, 1.5, 1.5), new Vec3(4.5, 1.5, 7.5));
        helper.assertTrue(FlightLift.snatch(player) && FlightLift.isLifted(zombie), Component.literal("Flying through the zombie should snatch it up"));
        flyPast(helper, player, new Vec3(1.5, 1.5, 7.4), new Vec3(1.5, 1.5, 7.5));
        helper.assertTrue(!FlightLift.snatch(player) && !FlightLift.isLifted(second), Component.literal("A vampire can only carry one creature at a time"));
        // Up under the arena's ceiling, which is as high as the zombie can be carried
        flyPast(helper, player, new Vec3(4.5, 4.2, 4.5), new Vec3(4.5, 4.2, 4.5));
        double ground = helper.absoluteVec(new Vec3(0.0, 1.0, 0.0)).y;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(zombie.getY() > ground + 0.8, Component.literal("The zombie should be carried up by the vampire"));
                    Vec3 offset = zombie.position().subtract(player.position());
                    helper.assertTrue(offset.z > 0.5 && offset.horizontalDistance() < 2.0 && offset.y > -2.0,
                            Component.literal("The zombie should be held up just in front of the vampire, not " + offset));
                })
                // Held for as long as they like, until a right click lets it go
                .thenIdle(FlightLift.REGRAB_TICKS + 20)
                .thenExecute(() -> {
                    helper.assertTrue(FlightLift.isLifted(zombie), Component.literal("The zombie should still be held"));
                    helper.assertTrue(FlightLift.drop(player) && !FlightLift.isLifted(zombie), Component.literal("A right click should let the zombie go"));
                    helper.assertTrue(!FlightLift.drop(player), Component.literal("Nothing left to let go"));
                    flyPast(helper, player, helper.relativeVec(zombie.position()), helper.relativeVec(zombie.position()));
                    helper.assertTrue(!FlightLift.snatch(player), Component.literal("What's just been let go shouldn't be snatched straight back up"));
                    removePlayer(helper, player);
                    zombie.discard();
                    second.discard();
                    golem.discard();
                })
                .thenSucceed();
    }

    /// Puts a (never ticked) player where they'd be after flying from `from` to `to` in one tick, both relative
    private static void flyPast(GameTestHelper helper, ServerPlayer player, Vec3 from, Vec3 to) {
        Vec3 was = helper.absoluteVec(from);
        Vec3 now = helper.absoluteVec(to);
        player.teleportTo(now.x, now.y, now.z);
        player.xOld = was.x;
        player.yOld = was.y;
        player.zOld = was.z;
    }

    static void whipPullsCreaturesIn(GameTestHelper helper) {
        ServerPlayer player = makePlayer(helper);
        // Facing east, at a zombie five blocks off
        Vec3 stand = helper.absoluteVec(new Vec3(1.5, 1.0, 4.5));
        player.snapTo(stand.x, stand.y, stand.z, -90.0F, 0.0F);
        player.teleportTo(stand.x, stand.y, stand.z);
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHIP.get()));
        Zombie zombie = spawnSturdyZombie(helper, new BlockPos(6, 1, 4));
        // Held still by its own will, so only the lash moves it
        zombie.removeFreeWill();
        float health = zombie.getHealth();
        double startDistance = zombie.distanceTo(player);
        helper.assertTrue(WhipLash.crack(player, WhipLash.Kind.PULL, null, WhipLash.LASH_TICKS) != null, Component.literal("The whip should crack"));
        helper.startSequence()
                .thenExecuteAfter(WhipLash.CRACK_TICK + 15, () -> {
                    double distance = zombie.distanceTo(player);
                    helper.assertTrue(distance < startDistance - 1.5, Component.literal("The lash should yank the zombie in: from " + startDistance + " to " + distance));
                    helper.assertTrue(distance > 1.0, Component.literal("The zombie should land short of the wielder, not on them: " + distance));
                    helper.assertValueEqual(zombie.getHealth(), health, "the pulled zombie's health (a pull isn't a blow)");
                })
                .thenExecute(() -> {
                    removePlayer(helper, player);
                    zombie.discard();
                })
                .thenSucceed();
    }

    /// A spear thrown from just in front of the zombie, so it hits on its first tick or two
    private static void throwSpearAt(ServerLevel level, ServerPlayer thrower, Zombie zombie, Vec3 direction) {
        throwSpearAt(level, thrower, zombie, direction, false);
    }

    private static void throwSpearAt(ServerLevel level, ServerPlayer thrower, Zombie zombie, Vec3 direction, boolean crimsonLightning) {
        BloodSpear spear = new BloodSpear(level, thrower);
        spear.setCrimsonLightning(crimsonLightning);
        spear.setPos(zombie.position().add(0.0, 0.9, 0.0).subtract(direction.scale(0.9)));
        spear.shoot(direction.x, direction.y, direction.z, 2.0F, 0.0F);
        level.addFreshEntity(spear);
        for (int i = 0; i < 5 && spear.isAlive(); i++) {
            spear.tick();
        }
        // It flies on through what it hits: not off into the neighbouring tests
        spear.discard();
    }

    /// A zombie that doesn't burn in the test's noon sun or shrug off knockback
    private static Zombie spawnSturdyZombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
        zombie.setPersistenceRequired();
        return zombie;
    }

    static void bloodBatsChainUntilTheirReachRunsOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Each one-hit zombie is nearest to the last; the far one would be a fourth link
        Zombie[] line = new Zombie[4];
        for (int i = 0; i < line.length; i++) {
            line[i] = weakZombie(helper, new BlockPos(1 + i * 2, 1, 1));
        }
        Zombie spared = weakZombie(helper, new BlockPos(1, 1, 7));
        Zombie first = line[0];
        first.discard();
        helper.assertTrue(BloodBat.chain(level, null, first, BloodBat.FIRST_RANGE) != null, Component.literal("A kill should set a Blood Bat loose"));
        helper.succeedWhen(() -> {
            for (int i = 1; i < line.length; i++) {
                helper.assertTrue(!line[i].isAlive(), Component.literal("Blood Bat link " + i + " should kill its zombie"));
            }
            helper.assertTrue(level.getEntitiesOfClass(BloodBat.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(32.0)).isEmpty(),
                    Component.literal("The chain should have run out"));
            helper.assertTrue(spared.isAlive(), Component.literal("Past 3 links, no more bats"));
        });
    }

    private static Zombie weakZombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setHealth(1.0F);
        return zombie;
    }

    static void bloodParadeDrainsHostiles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        helper.assertTrue(castSkill(player, BloodSkill.BLOOD_PARADE), Component.literal("Blood Parade should cast"));
        helper.assertValueEqual(BloodPool.get(player), 60, "blood left after Blood Parade");
        // ...and what its orb kills doesn't put any back
        BloodOrb paradeOrb = new BloodOrb(level, player, Vec3.ZERO, 0);
        Villager victim = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(7, 1, 1));
        victim.hurtServer(level, level.damageSources().source(ModDamageTypes.BLOOD_MAGIC, paradeOrb, player), 1000.0F);
        helper.assertValueEqual(BloodPool.get(player), 60, "blood after the Blood Parade's orb kills a villager");
        // That orb was cast from wherever the mock player stands, outside the test: get rid of it
        level.getEntitiesOfClass(BloodOrb.class, player.getBoundingBox().inflate(4.0)).forEach(BloodOrb::discard);
        removePlayer(helper, player);

        // The rest is watched over seconds, and the server never ticks a mock player (so its effects would never run
        // down): the orb here belongs to a villager instead. It hangs in the middle of the arena, with a zombie and
        // another villager in reach. Made with no blood, so its bolts are light and the zombie lives through several volleys.
        Villager caster = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 6));
        BloodOrb orb = new BloodOrb(level, caster, Vec3.ZERO, 0);
        orb.setPos(helper.absoluteVec(new Vec3(4.5, 1.5, 4.5)));
        level.addFreshEntity(orb);
        // Nearly full, so most of what it drinks becomes overheal
        caster.setHealth(caster.getMaxHealth() - 1.0F);
        float[] firstOverheal = new float[1];
        float[] zombieHealth = new float[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), Component.literal("The orb should lash the zombie"));
                    helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), Component.literal("The blood should heal the caster"));
                    helper.assertTrue(caster.getAbsorptionAmount() > 0.0F && caster.hasEffect(ModEffects.OVERHEAL),
                            Component.literal("Healing past full should become overheal"));
                })
                .thenExecute(() -> {
                    helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), Component.literal("Blood Parade only lashes hostiles"));
                    helper.assertTrue(caster.getEffect(ModEffects.OVERHEAL).getDuration() > BloodOrb.OVERHEAL_DURATION,
                            Component.literal("The overheal's countdown shouldn't start while the orb lasts"));
                    firstOverheal[0] = caster.getAbsorptionAmount();
                    zombieHealth[0] = zombie.getHealth();
                })
                .thenIdle(BloodOrb.BOLT_INTERVAL * 3)
                .thenExecute(() -> {
                    helper.assertTrue(zombie.getHealth() < zombieHealth[0], Component.literal("The orb keeps lashing the zombie"));
                    helper.assertValueEqual(caster.getAbsorptionAmount(), firstOverheal[0], "overheal after more hits on the same zombie (it only counts once)");
                })
                .thenWaitUntil(() -> helper.assertTrue(orb.isRemoved(), Component.literal("The orb lasts 8 seconds")))
                .thenExecute(() -> {
                    int left = caster.getEffect(ModEffects.OVERHEAL).getDuration();
                    helper.assertTrue(left > 0 && left <= BloodOrb.OVERHEAL_DURATION, Component.literal("Once the orb is gone, the overheal has 10 seconds left, not " + left));
                    firstOverheal[0] = caster.getAbsorptionAmount();
                })
                // Halfway through its fade
                .thenIdle(BloodOrb.OVERHEAL_DURATION / 2)
                .thenExecute(() -> {
                    float now = caster.getAbsorptionAmount();
                    helper.assertTrue(now > 0.0F && now < firstOverheal[0], Component.literal("The overheal should be fading, not gone or held: " + now + " of " + firstOverheal[0]));
                    // And at the end it's gone
                    caster.removeEffect(ModEffects.OVERHEAL);
                    helper.assertTrue(caster.getAbsorptionAmount() == 0.0F, Component.literal("The extra hearts should vanish with the overheal"));
                    zombie.discard();
                    villager.discard();
                    caster.discard();
                })
                .thenSucceed();
    }

    static void bloodParadeBouncesOffWalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 1; y <= 3; y++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
            }
        }
        Villager caster = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        BloodOrb orb = new BloodOrb(level, caster, new Vec3(1.0, 0.0, 0.0), 0);
        orb.setPos(helper.absoluteVec(new Vec3(5.0, 1.5, 4.5)));
        level.addFreshEntity(orb);
        helper.succeedWhen(() -> {
            helper.assertTrue(orb.getDeltaMovement().x < 0.0, Component.literal("The orb should bounce back off the wall"));
            orb.discard();
            caster.discard();
        });
    }

    // ---- Skill upgrades ----

    /// Wears an upgraded copy of the skill in the first slot and presses its key
    private static boolean castUpgraded(ServerPlayer player, BloodSkill skill) {
        ItemStack stack = new ItemStack(skill.skillItem());
        stack.set(ModDataComponents.SKILL_UPGRADED.get(), true);
        SkillSlots.set(player, 0, stack);
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(stack));
        return BloodSkills.tryCast(player, 0);
    }

    private static Zombie spawnHelmetedZombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    static void vampireSkillsHitHunters(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(4, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 4));
        helper.assertTrue(BloodOrb.isHostile(hunter, player), Component.literal("Vampire skills should strike hunters, even ones not yet after the vampire"));
        helper.assertTrue(!BloodOrb.isHostile(villager, player), Component.literal("Vampire skills should leave peaceful villagers alone"));
        hunter.discard();
        removePlayer(helper, player);
        helper.succeed();
    }

    static void crimsonShroudGlidesOnBloodCharges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 3.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        helper.assertTrue(castUpgraded(player, BloodSkill.FLIGHT), Component.literal("Upgraded Flight should cast"));
        MobEffectInstance shroud = player.getEffect(ModEffects.CRIMSON_SHROUD);
        helper.assertTrue(shroud != null && shroud.getDuration() == CrimsonShroudEffect.DURATION, Component.literal("The Crimson Shroud should last a minute"));
        helper.assertTrue(player.getAttributeValue(NeoForgeMod.GLIDING_FLIGHT) > 0.0, Component.literal("The shroud should glide like an elytra, with no elytra on"));
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), Component.literal("The shroud shouldn't take the chest slot"));
        helper.assertTrue(!player.hasEffect(ModEffects.BLOOD_FLIGHT), Component.literal("The shroud glides; it doesn't hover like plain Flight"));
        helper.assertTrue(NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 30.0, 1.0F)).isCanceled(), Component.literal("No fall should hurt while the shroud is on"));

        // Blood Charges: only while gliding, five blood each, driving the vampire on like a firework
        int before = BloodPool.get(player);
        helper.assertTrue(!BloodSkills.bloodCharge(player) && BloodPool.get(player) == before, Component.literal("No Blood Charge without gliding"));
        player.startFallFlying();
        // Not with a creature snatched up in their hands: a right click lets that go instead
        Zombie carried = spawnSturdyZombie(helper, new BlockPos(4, 1, 4));
        carried.removeFreeWill();
        flyPast(helper, player, new Vec3(4.5, 1.5, 4.5), new Vec3(4.5, 3.0, 4.5));
        helper.assertTrue(FlightLift.snatch(player), Component.literal("Gliding through the zombie should snatch it up"));
        helper.assertTrue(!BloodSkills.bloodCharge(player) && BloodPool.get(player) == before, Component.literal("No Blood Charge with hands full"));
        helper.assertTrue(FlightLift.drop(player), Component.literal("A right click should let the zombie go"));
        carried.discard();
        helper.assertTrue(BloodSkills.bloodCharge(player), Component.literal("A Blood Charge while gliding in the shroud"));
        helper.assertValueEqual(BloodPool.get(player), before - BloodSkills.BLOOD_CHARGE_COST, "blood after a Blood Charge");
        helper.assertTrue(level.getEntitiesOfClass(FireworkRocketEntity.class, player.getBoundingBox().inflate(3.0)).isEmpty(),
                Component.literal("A Blood Charge drives the vampire on itself, with no firework"));
        helper.assertValueEqual(player.getData(ModAttachments.BLOOD_CHARGE), BloodSkills.BLOOD_CHARGE_TICKS, "ticks of Blood Charge (and afterimages) to come");
        player.setDeltaMovement(Vec3.ZERO);
        BloodSkills.tickDash(player);
        helper.assertTrue(player.getDeltaMovement().dot(player.getLookAngle()) > 0.5, Component.literal("A Blood Charge should drive the vampire on along their aim"));
        player.removeData(ModAttachments.BLOOD_CHARGE);
        helper.assertTrue(!BloodSkills.bloodCharge(player), Component.literal("Not another straight away"));
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(new ItemStack(ModItems.BLOOD_CHARGE.get())));
        BloodPool.set(player, BloodSkills.BLOOD_CHARGE_COST - 1);
        helper.assertTrue(!BloodSkills.bloodCharge(player), Component.literal("No Blood Charge without the blood for it"));
        BloodPool.set(player, before);

        // Pressing the skill again takes the shroud off, free
        int blood = BloodPool.get(player);
        helper.assertTrue(BloodSkills.tryCast(player, 0), Component.literal("Pressing the skill again should take the shroud off"));
        helper.assertTrue(!player.hasEffect(ModEffects.CRIMSON_SHROUD), Component.literal("The shroud should be gone"));
        helper.assertValueEqual(BloodPool.get(player), blood, "blood after taking the shroud off");
        helper.assertTrue(!NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 30.0, 1.0F)).isCanceled(), Component.literal("Falls hurt again once it's off"));

        // Holy Water washes it off too
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(SkillSlots.get(player, 0)));
        helper.assertTrue(BloodSkills.tryCast(player, 0), Component.literal("The shroud should go back on"));
        BloodSkills.dispel(player);
        helper.assertTrue(!player.hasEffect(ModEffects.CRIMSON_SHROUD), Component.literal("Holy Water should wash the shroud off"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void afterimageStrikeSlashesThrough(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        // One zombie right in the dash's path, one off to the side that the dash only brushes past
        Zombie struckThrough = spawnHelmetedZombie(helper, new BlockPos(4, 1, 4));
        Zombie brushed = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(4.5, 1.0, 6.2));
        brushed.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        for (Zombie zombie : List.of(struckThrough, brushed)) {
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
            zombie.setHealth(200.0F);
        }
        Vec3 start = helper.absoluteVec(new Vec3(0.5, 1.0, 4.5));
        player.teleportTo(start.x, start.y, start.z);
        helper.assertTrue(castUpgraded(player, BloodSkill.DASH), Component.literal("Upgraded Dash should cast"));
        // Mock players don't move or tick on their own: walk it along the dash, 2 blocks a tick, and tick it by hand
        for (int tick = 1; tick <= 30; tick++) {
            int step = tick;
            helper.runAtTickTime(step, () -> {
                if (step <= 5) {
                    Vec3 at = start.add(2.0 * (step - 1), 0.0, 0.0);
                    player.teleportTo(at.x, at.y, at.z);
                }
                BloodSkills.tickDash(player);
            });
        }
        // One slash each so far: the zombie in the path is owed more, but they come 0.2 seconds apart
        helper.runAtTickTime(4, () -> helper.assertValueEqual(lost(struckThrough), lost(brushed), "health lost by both zombies before the next slash is due"));
        helper.runAtTickTime(31, () -> {
            float slash = lost(brushed);
            helper.assertTrue(slash > 0.0F, Component.literal("An afterimage should slash the zombie the dash brushes past"));
            helper.assertTrue(Math.abs(lost(struckThrough) - 3.0F * slash) < 0.01F,
                    Component.literal("Three afterimages should slash the zombie the dash runs through: lost " + lost(struckThrough) + ", one slash is " + slash));

            // A plain dash doesn't slash
            Zombie other = spawnHelmetedZombie(helper, new BlockPos(4, 1, 4));
            Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
            player.teleportTo(at.x, at.y, at.z);
            helper.assertTrue(castSkill(player, BloodSkill.DASH), Component.literal("Dash should cast"));
            BloodSkills.tickDash(player);
            helper.assertValueEqual(other.getHealth(), other.getMaxHealth(), "health of a zombie a plain dash passes through");
            List.of(struckThrough, brushed, other).forEach(Zombie::discard);
            removePlayer(helper, player);
            helper.succeed();
        });
    }

    private static float lost(LivingEntity entity) {
        return entity.getMaxHealth() - entity.getHealth();
    }

    static void thornedShellHurtsAttackers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        helper.assertTrue(castUpgraded(player, BloodSkill.ARMOR_OF_ANCIENT), Component.literal("Upgraded Armor of Ancient should cast"));
        helper.assertTrue(player.hasEffect(ModEffects.THORNED_SHELL) && player.hasEffect(ModEffects.ANCIENT_ARMOR), Component.literal("The shell comes with the armor"));
        removePlayer(helper, player);
        // Mock players can't be hurt, so the thorns are tried on a villager
        Zombie zombie = spawnHelmetedZombie(helper, new BlockPos(2, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        villager.addEffect(new MobEffectInstance(ModEffects.THORNED_SHELL, 200));
        villager.hurtServer(level, level.damageSources().mobAttack(zombie), 2.0F);
        helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), Component.literal("Striking a Thorned Shell should hurt the attacker"));
        zombie.discard();
        villager.discard();
        helper.succeed();
    }

    static void leechingSwarmBringsBlood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 0);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(5, 1, 4));
        SwarmBat.launchSwarm(level, player, player.getEyePosition().add(1.0, -0.8, 0.0), new Vec3(1.0, 0.0, 0.0), 6.0, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(BloodPool.get(player) >= SwarmBat.LEECHED_BLOOD, Component.literal("The swarm's bites should bring the vampire blood"));
            cow.discard();
            removePlayer(helper, player);
        });
    }

    static void panicSpreadsFromTheFleeing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie carrier = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
        carrier.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Zombie bystander = spawnHelmetedZombie(helper, new BlockPos(5, 1, 4));
        carrier.setData(ModAttachments.TERROR, Optional.of(new ModAttachments.Terror(carrier.position(), level.getGameTime() + 40, true)));
        helper.succeedWhen(() -> {
            helper.assertTrue(NightOfTerror.isTerrified(bystander), Component.literal("The terror should spread to the zombie next to the fleeing one"));
            helper.assertTrue(!bystander.getData(ModAttachments.TERROR).orElseThrow().contagious(), Component.literal("Caught panic doesn't spread again"));
            carrier.discard();
            bystander.discard();
        });
    }

    static void finalFeastHealsTheNextBlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 50);
        helper.assertTrue(castUpgraded(player, BloodSkill.DEFY_DEATH), Component.literal("Upgraded Defy Death should cast"));
        // A deadly blow, played out by hand (mock players can't be hurt)
        player.setHealth(0.0F);
        player.die(level.damageSources().generic());
        helper.assertValueEqual(player.getHealth(), 1.0F, "health after a defied death");
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        villager.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
        helper.assertTrue(player.getHealth() > 1.0F, Component.literal("The next blow should heal the vampire"));
        float healed = player.getHealth();
        villager.damageCooldownTime = 0;
        villager.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
        helper.assertValueEqual(player.getHealth(), healed, "health after a second blow (only the first feeds)");
        villager.discard();
        removePlayer(helper, player);
        helper.succeed();
    }

    static void returningStepLeavesAnAnchor(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        Vec3 anchor = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(anchor.x, anchor.y, anchor.z);
        helper.assertTrue(castUpgraded(player, BloodSkill.SHADOW_STEP), Component.literal("The first step places the anchor"));
        Vec3 away = anchor.add(20.0, 3.0, 0.0);
        player.teleportTo(away.x, away.y, away.z);
        helper.assertTrue(castUpgraded(player, BloodSkill.SHADOW_STEP), Component.literal("Returning Step should step to the anchor"));
        helper.assertTrue(player.position().distanceTo(anchor) < 0.01, Component.literal("The vampire should be on the anchor"));
        helper.assertTrue(player.getData(ModAttachments.SHADOW_ANCHOR).map(left -> left.position().distanceTo(away) < 0.01).orElse(false),
                Component.literal("The step should leave an anchor where the vampire stood"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void thirstingBladeFeedsOnKills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        helper.assertTrue(castUpgraded(player, BloodSkill.EPHEMERAL_SWORD), Component.literal("Upgraded Ephemeral Sword should cast"));
        ItemStack sword = player.getMainHandItem();
        long expiresAt = sword.get(ModDataComponents.EXPIRES_AT.get());
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        villager.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
        helper.assertValueEqual(sword.get(ModDataComponents.EXPIRES_AT.get()), expiresAt + UpgradeEffects.THIRSTING_BLADE_TICKS, "sword's expiry after a kill");
        removePlayer(helper, player);
        helper.succeed();
    }

    static void graveMarchRaisesTheFallen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        DeathMarch.march(villager, player);
        villager.kill(level);
        List<Zombie> risen = helper.getEntities(EntityTypes.ZOMBIE).stream().filter(zombie -> zombie.entityTags().contains(Enthrall.RISEN_TAG)).toList();
        helper.assertTrue(risen.size() == 1 && Enthrall.isThrallOf(risen.getFirst(), player), Component.literal("The fallen should rise to serve the vampire"));
        risen.forEach(Zombie::discard);
        removePlayer(helper, player);
        helper.succeed();
    }

    static void markedPreyTakesMoreDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        // Within sight of the zombies, as the gaze needs
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 1.5));
        player.teleportTo(at.x, at.y, at.z);
        Zombie marked = spawnHelmetedZombie(helper, new BlockPos(2, 1, 4));
        Zombie unmarked = spawnHelmetedZombie(helper, new BlockPos(6, 1, 4));
        helper.assertTrue(castUpgraded(player, BloodSkill.STALKERS_GAZE), Component.literal("Upgraded Stalker's Gaze should cast"));
        marked.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
        player.removeEffect(ModEffects.STALKERS_GAZE);
        unmarked.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
        float markedLost = marked.getMaxHealth() - marked.getHealth();
        float unmarkedLost = unmarked.getMaxHealth() - unmarked.getHealth();
        helper.assertTrue(markedLost > unmarkedLost, Component.literal("Marked prey should take more damage: " + markedLost + " against " + unmarkedLost));
        marked.discard();
        unmarked.discard();
        removePlayer(helper, player);
        helper.succeed();
    }


    static void enthralledCreaturesServe(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Possession.start(player, zombie, true);
        // Let the possession run out
        PossessionState state = player.getData(ModAttachments.POSSESSION_STATE);
        player.setData(ModAttachments.POSSESSION_STATE, new PossessionState(1, state.health(), state.attackDamage(), 0, 0));
        Possession.tickPossession(player);
        Zombie thrall = helper.getEntities(EntityTypes.ZOMBIE).getFirst();
        helper.assertTrue(Enthrall.isThrallOf(thrall, player), Component.literal("The creature should be enthralled once the possession runs its course"));
        thrall.setTarget(player);
        helper.assertTrue(thrall.getTarget() != player, Component.literal("A thrall shouldn't turn on its master"));

        // Something hurts the master: the thrall goes for it
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(6, 1, 4));
        player.setLastHurtByMob(husk);
        helper.succeedWhen(() -> {
            helper.assertTrue(thrall.getTarget() == husk, Component.literal("The thrall should attack whatever hurt its master"));
            thrall.discard();
            husk.discard();
            removePlayer(helper, player);
        });
    }

    static void plainPossessionLeavesNoThrall(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
        Possession.start(player, zombie);
        PossessionState state = player.getData(ModAttachments.POSSESSION_STATE);
        player.setData(ModAttachments.POSSESSION_STATE, new PossessionState(1, state.health(), state.attackDamage(), 0, 0));
        Possession.tickPossession(player);
        Zombie returned = helper.getEntities(EntityTypes.ZOMBIE).getFirst();
        helper.assertTrue(!Enthrall.isThrallOf(returned, player), Component.literal("Only the Enthrall upgrade leaves a thrall"));
        returned.discard();
        removePlayer(helper, player);
        helper.succeed();
    }


    static void bloodAltarUpgradesSkills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos table = new BlockPos(4, 1, 4);
        helper.setBlock(table, ModBlocks.BLOOD_ALTAR.get());
        BlockPos tablePos = helper.absolutePos(table);
        BlockState state = level.getBlockState(tablePos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(tablePos), Direction.UP, tablePos, false);
        ServerPlayer player = makeVampire(helper, 100);

        // Nothing to pay with
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BloodSkill.BLOOD_SPEAR.skillItem()));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(!SkillUpgrade.isUpgraded(player.getMainHandItem()), Component.literal("The table shouldn't upgrade for free"));

        // Every Token of Blood but no Token of Master, then one Token of Blood short, then the full price
        player.getInventory().add(new ItemStack(ModItems.TOKEN_OF_BLOOD.get(), BloodAltarBlock.PRICE));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(!SkillUpgrade.isUpgraded(player.getMainHandItem()), Component.literal("The table should want a Token of Master too"));
        player.getInventory().add(new ItemStack(ModItems.TOKEN_OF_MASTER.get(), BloodAltarBlock.MASTER_PRICE));
        removeOne(player, ModItems.TOKEN_OF_BLOOD.get());
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(!SkillUpgrade.isUpgraded(player.getMainHandItem()), Component.literal("The table should want its full price"));
        player.getInventory().add(new ItemStack(ModItems.TOKEN_OF_BLOOD.get(), 1));
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(SkillUpgrade.isUpgraded(player.getMainHandItem()), Component.literal("The table should upgrade the Blood Spear"));
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack left = player.getInventory().getItem(slot);
            helper.assertTrue(!left.is(ModItems.TOKEN_OF_BLOOD.get()) && !left.is(ModItems.TOKEN_OF_MASTER.get()), Component.literal("The tokens should be spent"));
        }

        // The upgraded spear costs 5 more blood, and the spear it conjures carries the lightning
        SkillSlots.set(player, 0, player.getMainHandItem().copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(BloodSkills.tryCast(player, 0), Component.literal("The upgraded Blood Spear should cast"));
        helper.assertValueEqual(BloodPool.get(player), 100 - BloodSkill.BLOOD_SPEAR.cost() - SkillUpgrade.CRIMSON_LIGHTNING.extraCost(), "blood after an upgraded Blood Spear");
        helper.assertTrue(player.getMainHandItem().is(ModItems.BLOOD_SPEAR.get()) && SkillUpgrade.isUpgraded(player.getMainHandItem()),
                Component.literal("The conjured spear should be upgraded too"));
        removePlayer(helper, player);
        helper.succeed();
    }

    private static void removeOne(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                player.getInventory().getItem(slot).shrink(1);
                return;
            }
        }
    }

    static void crimsonLightningStrikesSpearVictims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 0);
        Zombie struck = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        struck.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Zombie plain = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 1));
        plain.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        throwSpearAt(level, player, struck, new Vec3(0.0, 0.0, 1.0), true);
        throwSpearAt(level, player, plain, new Vec3(0.0, 0.0, 1.0), false);
        List<CrimsonLightning> bolts = level.getEntitiesOfClass(CrimsonLightning.class, struck.getBoundingBox().inflate(4.0));
        helper.assertTrue(!bolts.isEmpty(), Component.literal("Red lightning should strike the upgraded spear's victim"));
        helper.assertTrue(bolts.getFirst().shouldRenderAtSqrDistance(30.0 * 30.0), Component.literal("The bolt should be drawn for anyone nearby"));
        float struckLost = struck.getMaxHealth() - struck.getHealth();
        float plainLost = plain.getMaxHealth() - plain.getHealth();
        helper.assertTrue(struckLost > plainLost + 4.0F, Component.literal("The lightning should hurt on top of the spear: " + struckLost + " against " + plainLost));
        struck.discard();
        plain.discard();
        removePlayer(helper, player);
        helper.succeed();
    }

    static void spreadingBoilGrowsWithDeaths(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 0);
        Vec3 middle = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        HemorrhageZone spreading = HemorrhageZone.create(level, player, middle.x, middle.y, middle.z);
        spreading.setSpreading(true);
        level.addFreshEntity(spreading);
        helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(5, 1, 4)).kill(level);
        helper.assertValueEqual(spreading.getRadius(), HemorrhageZone.RADIUS + 1.0, "radius after a death in a spreading patch");
        spreading.discard();

        HemorrhageZone plain = HemorrhageZone.create(level, player, middle.x, middle.y, middle.z);
        level.addFreshEntity(plain);
        helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(5, 1, 4)).kill(level);
        helper.assertValueEqual(plain.getRadius(), HemorrhageZone.RADIUS, "radius after a death in a plain patch");
        plain.discard();
        removePlayer(helper, player);
        helper.succeed();
    }

    // ---- Keeping skills ----

    static void skillsStayThroughDeath(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        player.getInventory().add(new ItemStack(BloodSkill.DASH.skillItem()));
        player.getInventory().add(new ItemStack(Items.STICK));
        SkillKeeper.setAside(player);
        helper.assertTrue(!player.getInventory().contains(new ItemStack(BloodSkill.DASH.skillItem())), Component.literal("The skill shouldn't be dropped with the inventory"));
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.STICK)), Component.literal("Other items are left alone"));
        List<ItemStack> kept = player.getData(ModAttachments.KEPT_SKILLS);
        helper.assertValueEqual(kept.size(), 1, "skills set aside");
        SkillKeeper.giveBack(player);
        helper.assertTrue(player.getInventory().contains(new ItemStack(BloodSkill.DASH.skillItem())), Component.literal("The skill comes back on respawn"));
        removePlayer(helper, player);
        helper.succeed();
    }

    // ---- The Ancient Translator ----

    // An Iron Maiden with a villager shut in it, to feed a translator
    private static IronMaidenBlockEntity maidenWithCaptive(GameTestHelper helper, BlockPos pos) {
        BlockState lower = ModBlocks.IRON_MAIDEN.get().defaultBlockState();
        helper.setBlock(pos, lower);
        helper.setBlock(pos.above(), lower.setValue(IronMaidenBlock.HALF, DoubleBlockHalf.UPPER));
        IronMaidenBlockEntity maiden = (IronMaidenBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 7));
        maiden.setCaptive(IronMaidenBlockEntity.Captive.of(villager));
        villager.discard();
        return maiden;
    }

    static void translatorTurnsTomesIntoSkills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, ModBlocks.ANCIENT_TRANSLATOR.get());
        ServerPlayer player = makePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BloodSkill.DAYWALKER.tome()));
        // It won't read a tome whose skill its reader hasn't unlocked in their Codex
        helper.useBlock(pos, player);
        helper.assertBlockProperty(pos, AncientTranslatorBlock.TOME, 0);
        player.setData(ModAttachments.UNLOCKED_SKILLS, List.of(BloodSkill.DAYWALKER.getName()));
        helper.useBlock(pos, player);
        helper.assertTrue(player.getMainHandItem().isEmpty(), Component.literal("The translator should take the tome"));
        helper.assertBlockProperty(pos, AncientTranslatorBlock.TOME, BloodSkill.DAYWALKER.ordinal() + 1);
        removePlayer(helper, player);
        BlockPos at = helper.absolutePos(pos);
        AncientTranslatorBlockEntity translator = (AncientTranslatorBlockEntity) level.getBlockEntity(at);

        // With no captives about to feed it, it doesn't get anywhere
        for (int tick = 0; tick < 100; tick++) {
            AncientTranslatorBlockEntity.serverTick(level, at, level.getBlockState(at), translator);
        }
        helper.assertValueEqual(translator.progress(), 0, "progress of a translator with no Iron Maidens feeding it");

        // Two maidens read it twice as fast as one would, and each gives up some blood for it
        IronMaidenBlockEntity first = maidenWithCaptive(helper, new BlockPos(1, 1, 1));
        IronMaidenBlockEntity second = maidenWithCaptive(helper, new BlockPos(7, 1, 1));
        int needed = BloodSkill.DAYWALKER.tier().translationTicks();
        int ticks = 0;
        while (AncientTranslatorBlock.isTranslating(level.getBlockState(at)) && ticks < needed) {
            AncientTranslatorBlockEntity.serverTick(level, at, level.getBlockState(at), translator);
            ticks++;
        }
        helper.assertTrue(!AncientTranslatorBlock.isTranslating(level.getBlockState(at)), Component.literal("Fed by two Iron Maidens, the translation should finish"));
        helper.assertTrue(ticks <= needed / 2 + 25, Component.literal("Two Iron Maidens should halve the time, but it took " + ticks + " of " + needed + " ticks"));
        helper.assertItemEntityPresent(BloodSkill.DAYWALKER.skillItem(), pos.above(), 2.0);
        helper.assertTrue(first.getCaptive().blood() < IronMaidenBlockEntity.BLOOD_IN_A_CAPTIVE && second.getCaptive().blood() < IronMaidenBlockEntity.BLOOD_IN_A_CAPTIVE,
                Component.literal("Each Iron Maiden feeding the translator should give up some blood"));

        // No more than four feed one translator
        for (BlockPos more : new BlockPos[] {new BlockPos(1, 1, 7), new BlockPos(7, 1, 7), new BlockPos(1, 1, 4), new BlockPos(7, 1, 4)}) {
            maidenWithCaptive(helper, more);
        }
        helper.assertValueEqual(AncientTranslatorBlockEntity.feeders(level, at).size(), AncientTranslatorBlockEntity.MAX_MAIDENS, "Iron Maidens feeding one translator");
        for (IronMaidenBlockEntity maiden : IronMaidenBlock.captivesAround(level, at)) {
            maiden.setCaptive(null);
        }
        helper.succeed();
    }

    static void bloodSkillsComeInTiers(GameTestHelper helper) {
        for (BloodSkill skill : new BloodSkill[] {BloodSkill.BLOOD_PARADE, BloodSkill.POSSESS}) {
            helper.assertTrue(skill.tier() == BloodSkill.Tier.ANCIENT, Component.literal(skill + " should be an Ancient skill"));
        }
        for (BloodSkill skill : new BloodSkill[] {BloodSkill.BLOOD_SPEAR, BloodSkill.FLIGHT}) {
            helper.assertTrue(skill.tier() == BloodSkill.Tier.UNUSUAL, Component.literal(skill + " should be an Unusual skill"));
        }
        for (BloodSkill skill : new BloodSkill[] {BloodSkill.DASH, BloodSkill.DEATH_MARCH}) {
            helper.assertTrue(skill.tier() == BloodSkill.Tier.NEO, Component.literal(skill + " should be a Neo skill"));
        }
        helper.assertTrue(BloodSkill.Tier.ANCIENT.translationTicks() > BloodSkill.Tier.UNUSUAL.translationTicks()
                && BloodSkill.Tier.UNUSUAL.translationTicks() > BloodSkill.Tier.NEO.translationTicks(), Component.literal("Higher tiers should take longer to translate"));
        helper.succeed();
    }

    static void brokenTranslatorGivesTheTomeBack(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, ModBlocks.ANCIENT_TRANSLATOR.get().defaultBlockState().setValue(AncientTranslatorBlock.TOME, BloodSkill.BLOOD_PARADE.ordinal() + 1));
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.assertItemEntityPresent(BloodSkill.BLOOD_PARADE.tome(), pos, 2.0);
        helper.assertItemEntityPresent(ModItems.ANCIENT_TRANSLATOR.get(), pos, 2.0);
        helper.succeed();
    }

    static void translatorRecipeUsesTokensOfBlood(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String recipe : new String[] {"ancient_translator", "blood_altar", "silver_coin"}) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id(recipe))).isPresent(), Component.literal("Missing recipe " + recipe));
        }
        helper.succeed();
    }

    static void plagueDoctorTradesForCoinsAndEmeralds(GameTestHelper helper) {
        Villager hunter = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        hunter.setVillagerData(hunter.getVillagerData().withProfession(ModVillagers.PLAGUE_DOCTOR).withLevel(1));
        helper.assertTrue(!hunter.getOffers().isEmpty(), Component.literal("A novice Plague Doctor should have something to trade"));
        for (MerchantOffer offer : hunter.getOffers()) {
            boolean coins = offer.getCostA().is(ModItems.SILVER_COIN.get()) || offer.getCostA().is(Items.EMERALD) || offer.getResult().is(Items.EMERALD);
            helper.assertTrue(coins, Component.literal("The Plague Doctor deals in Silver Coins and emeralds, not " + offer.getCostA()));
        }
        hunter.discard();
        helper.succeed();
    }

    // ---- Where tomes come from ----

    static void ancientButlerSellsTomes(GameTestHelper helper) {
        Villager butler = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        butler.setVillagerData(butler.getVillagerData().withProfession(ModVillagers.ANCIENT_BUTLER).withLevel(1));
        MerchantOffer dash = butler.getOffers().stream().filter(offer -> offer.getResult().is(BloodSkill.DASH.tome())).findFirst().orElse(null);
        helper.assertTrue(dash != null, Component.literal("A novice Ancient Butler should sell the Dash tome"));
        helper.assertTrue(dash.getCostB().is(ModItems.TOKEN_OF_BLOOD.get()), Component.literal("Tomes should cost Tokens of Blood"));
        boolean sellsBlood = butler.getOffers().stream().anyMatch(offer -> offer.getResult().is(ModItems.BLOOD_BOTTLE.get()));
        helper.assertTrue(sellsBlood, Component.literal("A novice Ancient Butler should sell Blood Bottles"));
        butler.discard();
        helper.succeed();
    }

    static void tomesTurnUpInChests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable library = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.withDefaultNamespace("chests/stronghold_library")));
        // Opened by a vampire who's unlocked every skill (tomes don't turn up for anyone else: see guide.LockedTomes)
        ServerPlayer opener = makeVampire(helper, 0);
        opener.setData(ModAttachments.UNLOCKED_SKILLS, java.util.Arrays.stream(BloodSkill.values()).map(BloodSkill::getName).toList());
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .withParameter(LootContextParams.THIS_ENTITY, opener).create(LootContextParamSets.CHEST);
        int tomes = 0;
        for (int i = 0; i < 200; i++) {
            for (ItemStack stack : library.getRandomItems(params)) {
                if (stack.getItem() instanceof AncientTomeItem) {
                    tomes++;
                }
            }
        }
        // 44% a chest: about 90 in 200
        helper.assertTrue(tomes > 40 && tomes < 140, Component.literal("Expected about 90 tomes in 200 stronghold libraries, got " + tomes));
        removePlayer(helper, opener);
        helper.succeed();
    }

    // ---- Wearing skills ----

    static void skillsAreWornNotHeld(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BloodSkill.ARMOR_OF_ANCIENT.skillItem()));
        player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().isEmpty(), Component.literal("Using a skill should put it on"));
        helper.assertTrue(SkillSlots.get(player, 0).is(BloodSkill.ARMOR_OF_ANCIENT.skillItem()), Component.literal("The skill should be in the first slot"));

        // Can't wear two of the same
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BloodSkill.ARMOR_OF_ANCIENT.skillItem()));
        player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!player.getMainHandItem().isEmpty(), Component.literal("A second copy of a worn skill should stay in hand"));

        helper.assertTrue(BloodSkills.tryCast(player, 0), Component.literal("The worn skill's key should cast it"));
        helper.assertTrue(player.hasEffect(ModEffects.ANCIENT_ARMOR), Component.literal("Armor of Ancient should be up"));
        helper.assertTrue(!BloodSkills.tryCast(player, 1), Component.literal("An empty slot casts nothing"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void vampiresDontHunger(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        player.getFoodData().setFoodLevel(3);
        Vampirism.tickVampire(player);
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), 20, "a vampire's food level");
        removePlayer(helper, player);
        helper.succeed();
    }

    // ---- The newer skills ----

    static void defyDeathCostsBloodPerBlow(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 55);
        helper.assertTrue(castSkill(player, BloodSkill.DEFY_DEATH), Component.literal("Defy Death should cast"));
        helper.assertValueEqual(BloodPool.get(player), 25, "blood after casting Defy Death");
        // Mock players can't be hurt (they're always in creative), so each deadly blow is played out by hand:
        // health drops to nothing, then the player dies
        int blood = 25;
        for (int blow = 1; blow <= 3; blow++) {
            player.setHealth(0.0F);
            player.die(helper.getLevel().damageSources().generic());
            if (blood >= BloodSkills.DEFY_DEATH_COST_PER_HIT) {
                blood -= BloodSkills.DEFY_DEATH_COST_PER_HIT;
                helper.assertTrue(player.getHealth() > 0.0F, Component.literal("Defy Death should survive deadly blow " + blow));
            } else {
                // 5 blood left: not enough to defy this one
                helper.assertTrue(player.getHealth() <= 0.0F, Component.literal("Out of blood, death wins"));
            }
            helper.assertValueEqual(BloodPool.get(player), blood, "blood after deadly blow " + blow);
        }
        removePlayer(helper, player);
        helper.succeed();
    }

    static void shadowStepReturnsToTheAnchor(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        Vec3 anchor = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(anchor.x, anchor.y, anchor.z);
        helper.assertTrue(castSkill(player, BloodSkill.SHADOW_STEP), Component.literal("The first Shadow Step should place the anchor"));
        helper.assertValueEqual(BloodPool.get(player), 100, "blood after placing the anchor (free)");

        player.teleportTo(anchor.x + 30.0, anchor.y + 5.0, anchor.z);
        helper.assertTrue(castSkill(player, BloodSkill.SHADOW_STEP), Component.literal("Shadow Step should jump back to the anchor"));
        helper.assertTrue(player.position().distanceTo(anchor) < 0.01, Component.literal("The player should be back on the anchor"));
        helper.assertValueEqual(BloodPool.get(player), 100 - BloodSkill.SHADOW_STEP.cost(), "blood after stepping back");
        helper.assertTrue(player.getData(ModAttachments.SHADOW_ANCHOR).isEmpty(), Component.literal("The anchor is used up by the step"));

        // So the next cast places a new one, for free
        helper.assertTrue(castSkill(player, BloodSkill.SHADOW_STEP), Component.literal("Casting again should place a new anchor"));
        helper.assertValueEqual(BloodPool.get(player), 100 - BloodSkill.SHADOW_STEP.cost(), "blood after placing the new anchor");
        player.teleportTo(anchor.x + 150.0, anchor.y, anchor.z);
        helper.assertTrue(!castSkill(player, BloodSkill.SHADOW_STEP), Component.literal("An anchor 150 blocks away is out of reach"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void vampiresOnlyDrinkTheLiving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(4, 1, 4));
        vampire.setHealth(5.0F);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 4));
        vampire.killedEntity(level, zombie, level.damageSources().mobAttack(vampire));
        helper.assertValueEqual(vampire.getHealth(), 5.0F, "vampire health after killing a zombie");
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(6, 1, 4));
        vampire.killedEntity(level, cow, level.damageSources().mobAttack(vampire));
        helper.assertTrue(vampire.getHealth() > 5.0F, Component.literal("Killing a cow should heal the vampire"));
        zombie.discard();
        cow.discard();
        helper.succeed();
    }

    // These keep the mock player around for a while, so they run in their own batch (see ModGameTests.playerTest)

    static void batSwarmHitsWhatsAhead(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(6, 1, 4));
        cow.setPersistenceRequired();
        float health = cow.getHealth();
        SwarmBat.launchSwarm(helper.getLevel(), player, helper.absoluteVec(new Vec3(1.5, 1.5, 4.5)), new Vec3(1.0, 0.0, 0.0), 7.0);
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.getHealth() < health, Component.literal("The swarm should bite the cow in its path"));
            helper.assertTrue(cow.hasEffect(ModEffects.BLOOD_LOSS), Component.literal("Bitten creatures should be left with Blood Loss"));
            helper.assertTrue(cow.getDeltaMovement().x > 0.5, Component.literal("The swarm should push the cow along its path, but its motion is " + cow.getDeltaMovement()));
            removePlayer(helper, player);
        });
    }

    static void nightOfTerrorSendsMobsRunning(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 6));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setPersistenceRequired();
        double startDistance = zombie.distanceTo(player);
        helper.assertTrue(castSkill(player, BloodSkill.NIGHT_OF_TERROR), Component.literal("Night of Terror should cast"));
        helper.assertTrue(NightOfTerror.isTerrified(zombie), Component.literal("The zombie should be terrified"));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.distanceTo(player) > startDistance + 2.0, Component.literal("The zombie should run away from the vampire"));
            helper.assertTrue(zombie.getTarget() == null, Component.literal("A terrified zombie shouldn't target anyone"));
            zombie.discard();
            removePlayer(helper, player);
        });
    }

    static void vampiresDefendVampirePlayers(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(2, 1, 4));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        Vampirism.callForAid(player, zombie);
        helper.assertTrue(vampire.getTarget() == zombie, Component.literal("A vampire should turn on whatever attacks a vampire player"));

        // But not on another vampire
        Vampire brawler = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(6, 1, 6));
        Vampirism.callForAid(player, brawler);
        helper.assertTrue(vampire.getTarget() == zombie, Component.literal("Vampires don't turn on their own kind"));
        removePlayer(helper, player);
        zombie.discard();
        helper.succeed();
    }

    static void ephemeralSwordConjuresAndFades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        helper.assertTrue(castSkill(player, BloodSkill.EPHEMERAL_SWORD), Component.literal("Ephemeral Sword should cast"));
        helper.assertValueEqual(BloodPool.get(player), 60, "blood after conjuring the sword");
        ItemStack sword = player.getMainHandItem();
        helper.assertTrue(sword.is(ModItems.EPHEMERAL_SWORD.get()), Component.literal("The sword should appear in hand"));
        helper.assertTrue(sword.has(DataComponents.UNBREAKABLE), Component.literal("The sword can't break"));
        helper.assertTrue(sword.get(DataComponents.ATTRIBUTE_MODIFIERS).equals(new ItemStack(Items.IRON_SWORD).get(DataComponents.ATTRIBUTE_MODIFIERS)),
                Component.literal("It should hit as hard and as fast as an iron sword"));

        // Casting again replaces it rather than adding a second
        castSkill(player, BloodSkill.EPHEMERAL_SWORD);
        int swords = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            swords += player.getInventory().getItem(slot).is(ModItems.EPHEMERAL_SWORD.get()) ? 1 : 0;
        }
        helper.assertValueEqual(swords, 1, "swords after casting twice");

        // Once its minute is up it's gone
        player.getMainHandItem().set(ModDataComponents.EXPIRES_AT.get(), level.getGameTime() - 1);
        player.getMainHandItem().inventoryTick(level, player, EquipmentSlot.MAINHAND);
        helper.assertTrue(player.getMainHandItem().isEmpty(), Component.literal("The sword should fade when its time is up"));
        removePlayer(helper, player);
        helper.succeed();
    }

    // ---- Possess ----

    static void possessBecomesTheCreature(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MasterVampire master = helper.spawnWithNoFreeWill(ModEntities.MASTER_VAMPIRE.get(), new BlockPos(7, 1, 7));
        helper.assertTrue(!Possession.canBePossessed(master), Component.literal("Master vampires can't be possessed"));
        master.discard();

        ServerPlayer player = makeVampire(helper, 100);
        Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(4, 1, 4));
        sheep.setColor(DyeColor.RED);
        sheep.setHealth(5.0F);
        UUID uuid = sheep.getUUID();
        Possession.start(player, sheep);
        helper.assertTrue(Possession.isPossessing(player), Component.literal("The vampire should be possessing the sheep"));
        helper.assertTrue(level.getEntity(uuid) == null, Component.literal("The sheep should leave the world while it's possessed"));
        helper.assertTrue(player.position().distanceTo(sheep.position()) < 0.01, Component.literal("The vampire should take the sheep's place"));
        helper.assertTrue(player.getScale() < 0.8F, Component.literal("The vampire should shrink to the sheep's size, but is " + player.getScale()));
        helper.assertTrue(player.getVehicle() == null, Component.literal("Possessing isn't riding"));

        Possession.endPossessionBy(player);
        helper.assertTrue(!Possession.isPossessing(player), Component.literal("The possession should be over"));
        helper.assertTrue(level.getEntity(uuid) instanceof Sheep back && back.isAlive() && back.getColor() == DyeColor.RED && back.getHealth() == 5.0F,
                Component.literal("The same red, wounded sheep should step back into the world"));
        helper.assertValueEqual(player.getScale(), 1.0F, "the vampire's size after the possession");
        removePlayer(helper, player);
        level.getEntity(uuid).discard();
        helper.succeed();
    }

    static void possessedCreeperExplodes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        Creeper creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new BlockPos(4, 1, 4));
        UUID uuid = creeper.getUUID();
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(5, 1, 4));
        Possession.start(player, creeper);
        CreatureAbilities.press(player, null);
        helper.assertTrue(Possession.isPossessing(player) && cow.getHealth() == cow.getMaxHealth(), Component.literal("The creeper shouldn't blow the moment its fuse is lit"));
        helper.assertValueEqual(player.getData(ModAttachments.CREEPER_FUSE), CreatureAbilities.CREEPER_FUSE, "the fuse after lighting it");
        // Mock players aren't ticked, so the fuse is burned down by hand
        for (int tick = 0; tick < CreatureAbilities.CREEPER_FUSE; tick++) {
            Possession.tickPossession(player);
        }
        helper.assertTrue(!Possession.isPossessing(player), Component.literal("Blowing up should end the possession"));
        helper.assertTrue(level.getEntity(uuid) == null, Component.literal("The creeper is used up by its explosion"));
        helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), Component.literal("The explosion should hurt the cow next to it"));
        helper.assertTrue(player.isAlive() && player.getHealth() == player.getMaxHealth(), Component.literal("The vampire is spared their own blast"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void possessedSkeletonDrawsItsBow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        Skeleton skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new BlockPos(4, 1, 4));
        Possession.start(player, skeleton);
        AABB around = player.getBoundingBox().inflate(6.0);

        // A tap barely draws the bow: nothing flies
        CreatureAbilities.press(player, null);
        Possession.tickPossession(player);
        CreatureAbilities.release(player);
        helper.assertTrue(level.getEntitiesOfClass(Arrow.class, around).isEmpty(), Component.literal("A quick tap shouldn't shoot"));

        // Held for a full draw, it shoots on release
        for (int tick = 0; tick < 10; tick++) {
            Possession.tickPossession(player);
        }
        CreatureAbilities.press(player, null);
        helper.assertTrue(level.getEntitiesOfClass(Arrow.class, around).isEmpty(), Component.literal("Nothing flies while the bow is drawn"));
        for (int tick = 0; tick < CreatureAbilities.BOW_DRAW; tick++) {
            Possession.tickPossession(player);
        }
        CreatureAbilities.release(player);
        List<Arrow> arrows = level.getEntitiesOfClass(Arrow.class, around);
        helper.assertTrue(arrows.size() == 1 && arrows.get(0).isCritArrow(), Component.literal("A fully drawn bow should loose one critical arrow"));
        arrows.forEach(Arrow::discard);
        Possession.endPossessionBy(player);
        removePlayer(helper, player);
        level.getEntitiesOfClass(Skeleton.class, around).forEach(Skeleton::discard);
        helper.succeed();
    }

    // ---- Masters ----

    static void mastersHuntVampiresToo(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 0);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        MasterVampire master = helper.spawnWithNoFreeWill(ModEntities.MASTER_VAMPIRE.get(), new BlockPos(2, 1, 4));
        // (Whether the master attacks the vampire player can't be checked here: mock players are always in creative,
        // and nothing attacks creative players)
        helper.assertTrue(!MasterVampire.isEnemyOfVampires(player, null), Component.literal("Ordinary vampires still leave vampire players alone"));

        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        Vampirism.callForAid(player, zombie);
        helper.assertTrue(master.getTarget() != zombie, Component.literal("A Master Vampire doesn't come to a vampire player's aid"));
        removePlayer(helper, player);
        zombie.discard();
        master.discard();
        helper.succeed();
    }

    static void mastersOnlyRiseUnderABloodMoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(!BloodMoon.isActive(level), Component.literal("This test needs a night without a blood moon"));
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.assertTrue(!ModEntities.checkMasterVampireSpawnRules(ModEntities.MASTER_VAMPIRE.get(), level, EntitySpawnReason.NATURAL, pos, level.getRandom()),
                Component.literal("Master Vampires shouldn't spawn naturally without a blood moon"));
        for (int i = 0; i < 200; i++) {
            helper.assertTrue(Hunter.pickLeaderType(level, level.getRandom()) == ModEntities.HUNTER.get(),
                    Component.literal("Hunter parties shouldn't be led by a Master Hunter without a blood moon"));
        }
        helper.succeed();
    }

    static void masterVampiresDropRareTomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 0);
        // A killer who's unlocked every skill (tomes don't drop for anyone else: see guide.LockedTomes)
        player.setData(ModAttachments.UNLOCKED_SKILLS, java.util.Arrays.stream(BloodSkill.values()).map(BloodSkill::getName).toList());
        MasterVampire master = helper.spawnWithNoFreeWill(ModEntities.MASTER_VAMPIRE.get(), new BlockPos(4, 1, 4));
        LootTable loot = level.getServer().reloadableRegistries().getLootTable(master.getLootTable().orElseThrow());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, master)
                .withParameter(LootContextParams.ORIGIN, master.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                .create(LootContextParamSets.ENTITY);
        int tomes = 0;
        for (int i = 0; i < 200; i++) {
            for (ItemStack stack : loot.getRandomItems(params)) {
                if (stack.getItem() instanceof AncientTomeItem tome) {
                    helper.assertTrue(tome.skill() != BloodSkill.DASH && tome.skill() != BloodSkill.FLIGHT, Component.literal("Only the rare skills drop"));
                    tomes++;
                }
            }
        }
        // 30%: about 60 in 200
        helper.assertTrue(tomes > 25 && tomes < 100, Component.literal("Expected about 60 rare tomes in 200 Master Vampire kills, got " + tomes));
        removePlayer(helper, player);
        master.discard();
        helper.succeed();
    }

    // ---- Newer skills ----

    static void boilingHemorrhageScaldsAndShares(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 0);
        Vec3 centre = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        HemorrhageZone zone = HemorrhageZone.create(level, player, centre.x, centre.y, centre.z);
        level.addFreshEntity(zone);
        Cow scalded = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(4, 1, 5));
        Cow outside = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(8, 1, 8));
        for (int tick = 0; tick < 10; tick++) {
            zone.tick();
        }
        helper.assertTrue(scalded.getHealth() < scalded.getMaxHealth(), Component.literal("A cow standing in the zone should be scalded"));
        helper.assertValueEqual(outside.getHealth(), outside.getMaxHealth(), "health of a cow outside the zone");

        // A cow that dies in the zone, by any hand, gives its blood to the vampire
        Cow victim = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(3, 1, 4));
        victim.die(level.damageSources().generic());
        helper.assertValueEqual(BloodPool.get(player), BloodPool.bloodFrom(victim), "blood from a cow that died in the zone");
        zone.discard();
        removePlayer(helper, player);
        helper.succeed();
    }

    static void deathMarchWalksStraightOn(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setPersistenceRequired();
        // Facing east (+x)
        zombie.setYRot(-90.0F);
        zombie.setYBodyRot(-90.0F);
        double startX = zombie.getX();
        DeathMarch.march(zombie);
        helper.assertTrue(DeathMarch.isMarching(zombie), Component.literal("The zombie should be marching"));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getX() > startX + 2.5, Component.literal("The zombie should march east"));
            helper.assertTrue(Math.abs(zombie.getZ() - helper.absoluteVec(new Vec3(1.5, 1.0, 4.5)).z) < 0.6, Component.literal("It should march in a straight line"));
            zombie.discard();
        });
    }

    static void stalkersGazeLastsAndWashesOff(GameTestHelper helper) {
        ServerPlayer player = makeVampire(helper, 100);
        helper.assertTrue(castSkill(player, BloodSkill.STALKERS_GAZE), Component.literal("Stalker's Gaze should cast"));
        helper.assertValueEqual(player.getEffect(ModEffects.STALKERS_GAZE).getDuration(), 600, "Stalker's Gaze duration");
        helper.assertTrue(!castSkill(player, BloodSkill.STALKERS_GAZE), Component.literal("Can't recast while it's active"));
        ModEffects.HOLY_WATER.get().applyInstantaneousEffect(helper.getLevel(), null, null, player, 0, 1.0);
        helper.assertTrue(!player.hasEffect(ModEffects.STALKERS_GAZE), Component.literal("Holy Water should wash the gaze away"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void blackBloodHurtsAttackersBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow drinker = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(3, 1, 4));
        drinker.addEffect(new MobEffectInstance(ModEffects.BLACK_BLOOD, 200));
        Zombie attacker = spawnSturdyZombie(helper, new BlockPos(5, 1, 4));
        float health = attacker.getHealth();
        drinker.hurtServer(level, level.damageSources().mobAttack(attacker), 2.0F);
        float struck = attacker.getHealth();
        // A little less than the full 3 gets through the zombie's armor
        helper.assertTrue(health - struck > BlackBloodEffect.RETALIATION - 0.5F, Component.literal("Striking a Black Blood drinker should hurt the attacker back, but it lost " + (health - struck)));

        // An arrow from a distance isn't answered
        drinker.damageCooldownTime = 0;
        Arrow arrow = new Arrow(level, attacker, new ItemStack(Items.ARROW), null);
        drinker.hurtServer(level, level.damageSources().arrow(arrow, attacker), 2.0F);
        helper.assertValueEqual(attacker.getHealth(), struck, "attacker's health after shooting the drinker");
        arrow.discard();

        var recipes = level.getServer().getRecipeManager();
        for (String recipe : new String[] {"brewing/potion_awkward_token_of_blood", "brewing/potion_black_blood_glowstone", "brewing/potion_black_blood_redstone"}) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id(recipe))).isPresent(), Component.literal("Missing recipe " + recipe));
        }
        // Only ever drunk: it can't be made into a splash potion
        helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id("brewing/potion_black_blood_gunpowder"))).isEmpty(),
                Component.literal("Black Blood shouldn't become a splash potion"));
        attacker.discard();
        drinker.discard();
        helper.succeed();
    }

    static void huntersDrinkBlackBloodForAFight(GameTestHelper helper) {
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(2, 1, 4));
        Zombie zombie = spawnSturdyZombie(helper, new BlockPos(6, 1, 4));
        helper.assertTrue(!hunter.hasEffect(ModEffects.BLACK_BLOOD), Component.literal("A hunter at rest hasn't drunk Black Blood"));
        hunter.setTarget(zombie);
        helper.succeedWhen(() -> {
            helper.assertTrue(hunter.hasEffect(ModEffects.BLACK_BLOOD), Component.literal("A hunter going into a fight should drink Black Blood"));
            hunter.discard();
            zombie.discard();
        });
    }

    static void blackBloodBurnsUndeadBiters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(3, 1, 4));
        hunter.addEffect(new MobEffectInstance(ModEffects.BLACK_BLOOD, 200));
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(5, 1, 4));
        vampire.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        // Vampires shrug off most harm (Resistance), so their health is checked before and after the blow
        float health = vampire.getHealth();
        hunter.hurtServer(level, level.damageSources().mobAttack(vampire), 2.0F);
        helper.assertTrue(vampire.getHealth() < health, Component.literal("A vampire biting a Black Blood drinker should be hurt in turn"));
        hunter.discard();
        vampire.discard();
        helper.succeed();
    }

    static void vampiresRefuseBlackBlood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer vampire = makeVampire(helper, 0);
        vampire.setItemInHand(InteractionHand.MAIN_HAND, PotionContents.createItemStack(Items.POTION, ModPotions.BLACK_BLOOD));
        vampire.getMainHandItem().use(level, vampire, InteractionHand.MAIN_HAND);
        helper.assertTrue(!vampire.isUsingItem(), Component.literal("A vampire shouldn't be able to start drinking Black Blood"));

        ServerPlayer human = makePlayer(helper);
        human.setItemInHand(InteractionHand.MAIN_HAND, PotionContents.createItemStack(Items.POTION, ModPotions.BLACK_BLOOD));
        human.getMainHandItem().use(level, human, InteractionHand.MAIN_HAND);
        helper.assertTrue(human.isUsingItem(), Component.literal("A human can drink Black Blood"));
        removePlayer(helper, vampire);
        removePlayer(helper, human);
        helper.succeed();
    }

    static void vampiresRefuseGarlic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer vampire = makeVampire(helper, 0);
        vampire.getFoodData().setFoodLevel(10);
        for (ItemStack garlic : List.of(PotionContents.createItemStack(Items.POTION, ModPotions.GARLIC), new ItemStack(ModItems.GARLIC.get()))) {
            vampire.setItemInHand(InteractionHand.MAIN_HAND, garlic);
            vampire.getMainHandItem().use(level, vampire, InteractionHand.MAIN_HAND);
            helper.assertTrue(!vampire.isUsingItem(), Component.literal("A vampire shouldn't be able to drink or eat " + garlic.getHoverName().getString()));
        }

        ServerPlayer human = makePlayer(helper);
        human.setItemInHand(InteractionHand.MAIN_HAND, PotionContents.createItemStack(Items.POTION, ModPotions.GARLIC));
        human.getMainHandItem().use(level, human, InteractionHand.MAIN_HAND);
        helper.assertTrue(human.isUsingItem(), Component.literal("A human can drink a Potion of Garlic"));
        removePlayer(helper, vampire);
        removePlayer(helper, human);
        helper.succeed();
    }

    static void bloodBottlesRefillVampires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer vampire = makeVampire(helper, 10);
        ItemStack left = new ItemStack(ModItems.BLOOD_BOTTLE.get()).finishUsingItem(level, vampire);
        helper.assertValueEqual(BloodPool.get(vampire), 10 + BloodBottleItem.BLOOD, "blood after drinking a Blood Bottle");
        helper.assertTrue(left.is(Items.GLASS_BOTTLE), Component.literal("Drinking it leaves a glass bottle"));

        ServerPlayer human = makePlayer(helper);
        new ItemStack(ModItems.BLOOD_BOTTLE.get()).finishUsingItem(level, human);
        helper.assertTrue(human.hasEffect(MobEffects.NAUSEA) && BloodPool.get(human) == 0, Component.literal("Blood only sickens a human"));
        removePlayer(helper, vampire);
        removePlayer(helper, human);
        helper.succeed();
    }

    // ---- Garlic ----

    /// Hangs a Garlic Braid from a stone block one above `pos`
    private static BlockPos hangGarlic(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.above(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.GARLIC_BRAID.get().defaultBlockState().setValue(GarlicBraidBlock.HANGING, true));
        return helper.absolutePos(pos);
    }

    static void garlicBraidRepelsUndead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos garlic = hangGarlic(helper, new BlockPos(4, 2, 1));
        Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(4, 1, 3));
        helper.assertTrue(GarlicWard.nearestGarlic(level, husk.blockPosition(), GarlicWard.RADIUS).isPresent(),
                Component.literal("Undead should be able to smell the garlic"));
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.position().distanceTo(Vec3.atCenterOf(garlic)) > 5.0, Component.literal("The husk should back away from the garlic"));
            husk.discard();
        });
    }

    static void garlicWeakensUndead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(2, 1, 4));
        husk.addEffect(new MobEffectInstance(ModEffects.GARLIC, 200));
        Villager bitten = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(5, 1, 4));
        bitten.hurtServer(level, level.damageSources().mobAttack(husk), 10.0F);
        float lost = bitten.getMaxHealth() - bitten.getHealth();
        helper.assertTrue(Math.abs(lost - 10.0F * GarlicEffect.damageLeft(0)) < 0.01F, Component.literal("Garlic should take 40% off an undead's blow, lost " + lost));

        // The living don't mind garlic
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(6, 1, 6));
        cow.addEffect(new MobEffectInstance(ModEffects.GARLIC, 200));
        Villager kicked = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 2));
        kicked.hurtServer(level, level.damageSources().mobAttack(cow), 10.0F);
        helper.assertValueEqual(kicked.getMaxHealth() - kicked.getHealth(), 10.0F, "damage from a garlic-soaked cow");

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.hasEffect(MobEffects.SLOWNESS), Component.literal("Garlic should slow undead"));
            helper.assertTrue(!cow.hasEffect(MobEffects.SLOWNESS), Component.literal("Garlic shouldn't slow the living"));
        });
    }

    static void garlicReeksToVampirePlayers(GameTestHelper helper) {
        hangGarlic(helper, new BlockPos(4, 2, 6));
        ServerPlayer vampire = makeVampire(helper, 50);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        vampire.teleportTo(at.x, at.y, at.z);
        // Mock players are always in creative, which garlic leaves alone, so this skips straight to the check
        GarlicWard.reelFromGarlic(vampire);
        helper.assertTrue(vampire.hasEffect(ModEffects.GARLIC), Component.literal("A vampire next to hung garlic should reel from it"));
        removePlayer(helper, vampire);
        helper.succeed();
    }

    static void garlicGrowsAndBrews(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(ModItems.GARLIC.get().getBlock() == ModBlocks.GARLIC_CROP.get(), Component.literal("Garlic should plant the garlic crop"));
        var recipes = level.getServer().getRecipeManager();
        for (String recipe : new String[] {"garlic_braid", "brewing/potion_awkward_garlic", "brewing/potion_garlic_redstone", "brewing/potion_garlic_glowstone_dust",
                "brewing/potion_garlic_gunpowder", "brewing/splash_potion_garlic_dragon_breath", "brewing/potion_awkward_ectoplasm", "brewing/potion_ephemeral_redstone"}) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id(recipe))).isPresent(), Component.literal("Missing recipe " + recipe));
        }
        helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id("brewing/potion_ephemeral_gunpowder"))).isEmpty(),
                Component.literal("The Potion of Ephemeral is drink-only"));

        // Wild garlic turns up in grass now and then
        LootTable grass = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.withDefaultNamespace("blocks/short_grass")));
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .withParameter(LootContextParams.BLOCK_STATE, Blocks.SHORT_GRASS.defaultBlockState())
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY).create(LootContextParamSets.BLOCK);
        int garlic = 0;
        for (int i = 0; i < 1000; i++) {
            for (ItemStack stack : grass.getRandomItems(params)) {
                if (stack.is(ModItems.GARLIC.get())) {
                    garlic += stack.getCount();
                }
            }
        }
        // 4% a time: about 40 in 1000
        helper.assertTrue(garlic > 10 && garlic < 90, Component.literal("Expected about 40 garlic from 1000 tufts of grass, got " + garlic));
        helper.succeed();
    }

    // ---- The Potion of Ephemeral ----

    static void ephemeralPassesThroughWalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // The player stands inside a pillar of stone
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        ServerPlayer player = makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        player.addEffect(new MobEffectInstance(ModEffects.EPHEMERAL, 100));
        player.doTick();
        helper.assertTrue(player.noPhysics, Component.literal("A ghost shouldn't collide with walls"));
        helper.assertTrue(player.getAbilities().flying, Component.literal("A ghost should float"));
        helper.assertTrue(player.getData(ModAttachments.GHOSTLY), Component.literal("Others should see the player as a ghost"));

        player.removeEffect(ModEffects.EPHEMERAL);
        player.doTick();
        helper.assertTrue(!player.noPhysics && !player.getData(ModAttachments.GHOSTLY), Component.literal("The player should be solid again"));
        helper.assertTrue(level.noCollision(player, player.getBoundingBox()), Component.literal("A ghost turning solid inside a wall should be pushed out of it"));
        removePlayer(helper, player);
        helper.succeed();
    }

    static void ghostsCantBeHit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makePlayer(helper);
        Zombie zombie = spawnHelmetedZombie(helper, new BlockPos(4, 1, 4));
        player.addEffect(new MobEffectInstance(ModEffects.EPHEMERAL, 100));
        DamageSource blow = level.damageSources().mobAttack(zombie);
        helper.assertTrue(NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(player, new DamageContainer(blow, 4.0F))).isCanceled(),
                Component.literal("A zombie's blow should pass through a ghost"));
        helper.assertTrue(!NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(player, new DamageContainer(level.damageSources().fall(), 4.0F))).isCanceled(),
                Component.literal("Harm nobody dealt, like a fall, still hurts a ghost"));
        Arrow arrow = new Arrow(level, zombie, new ItemStack(Items.ARROW), null);
        helper.assertTrue(NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(arrow, new EntityHitResult(player))).isCanceled(),
                Component.literal("An arrow should fly straight through a ghost"));

        player.removeEffect(ModEffects.EPHEMERAL);
        helper.assertTrue(!NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(player, new DamageContainer(blow, 4.0F))).isCanceled(),
                Component.literal("Once solid again, the player can be hit"));
        zombie.discard();
        removePlayer(helper, player);
        helper.succeed();
    }

    // ---- The Master Vampire's summons ----

    static void orphanedMinionsCrumble(GameTestHelper helper) {
        Spider spider = helper.spawnWithNoFreeWill(EntityTypes.SPIDER, new BlockPos(4, 1, 4));
        spider.addTag(MasterVampire.FAMILIAR_TAG);
        helper.succeedWhen(() -> helper.assertTrue(spider.isRemoved(), Component.literal("A summon with no master should crumble")));
    }
}
