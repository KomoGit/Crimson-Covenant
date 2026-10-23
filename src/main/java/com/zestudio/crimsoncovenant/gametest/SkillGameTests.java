package com.zestudio.crimsoncovenant.gametest;

import java.util.List;
import java.util.UUID;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.AncientTranslatorBlock;
import com.zestudio.crimsoncovenant.entity.BloodOrb;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.HemorrhageZone;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.SwarmBat;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.item.BloodBottleItem;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModVillagers;
import com.zestudio.crimsoncovenant.skill.AncientTomeItem;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.skill.BloodSkills;
import com.zestudio.crimsoncovenant.skill.CreatureAbilities;
import com.zestudio.crimsoncovenant.skill.DeathMarch;
import com.zestudio.crimsoncovenant.skill.NightOfTerror;
import com.zestudio.crimsoncovenant.skill.Possession;
import com.zestudio.crimsoncovenant.skill.SkillKeeper;
import com.zestudio.crimsoncovenant.skill.SkillSlots;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.BloodMoon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Tests for the vampire's blood bar, the blood skills, the Ancient Translator, and where tomes come from
final class SkillGameTests {
    private SkillGameTests() {}

    /// Mock players are always in creative; take away its creative powers so flight and item use behave as in survival
    private static ServerPlayer makePlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.getAbilities().instabuild = false;
        player.getAbilities().invulnerable = false;
        return player;
    }

    private static ServerPlayer makeVampire(GameTestHelper helper, int blood) {
        ServerPlayer player = makePlayer(helper);
        player.setData(ModAttachments.IS_VAMPIRE, true);
        BloodPool.set(player, blood);
        return player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /// Wears the skill in the first slot and presses its key
    private static boolean castSkill(ServerPlayer player, BloodSkill skill) {
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
        BloodPool.set(player, BloodPool.MAX_BLOOD + 50);
        helper.assertValueEqual(BloodPool.get(player), BloodPool.MAX_BLOOD, "blood can't go over the maximum");
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
        helper.assertValueEqual(BloodPool.get(player), 0, "blood after throwing the spear");
        // That one was thrown from wherever the mock player stands, outside the test: get rid of it
        helper.getLevel().getEntitiesOfClass(BloodSpear.class, player.getBoundingBox().inflate(4.0)).forEach(BloodSpear::discard);
        // A second spear, aimed straight at a chicken a block away. It's ticked by hand so the mock player can be
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

    static void bloodParadeChainsThroughCrowds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeVampire(helper, 100);
        helper.assertTrue(castSkill(player, BloodSkill.BLOOD_PARADE), Component.literal("Blood Parade should cast"));
        helper.assertValueEqual(BloodPool.get(player), 0, "Blood Parade spends the whole bar");
        // That orb was thrown from wherever the mock player stands, outside the test: get rid of it
        level.getEntitiesOfClass(BloodOrb.class, player.getBoundingBox().inflate(4.0)).forEach(BloodOrb::discard);

        // Two zombies by the burst, and a third out of its reach but next to the second.
        // The burst is kept in the middle of the arena so it can't reach into other tests.
        Zombie near = spawnWeakZombie(helper, new BlockPos(3, 1, 4));
        Zombie edge = spawnWeakZombie(helper, new BlockPos(5, 1, 4));
        Zombie far = spawnWeakZombie(helper, new BlockPos(7, 1, 4));
        BloodOrb orb = new BloodOrb(level, player, new Vec3(1.0, 0.0, 0.0), 100);
        orb.setPos(helper.absoluteVec(new Vec3(3.0, 1.5, 4.5)));
        level.addFreshEntity(orb);
        orb.burst(level, null);
        helper.assertTrue(!near.isAlive() && !edge.isAlive(), Component.literal("The burst should kill the zombies around it"));
        helper.assertTrue(!far.isAlive(), Component.literal("A slain zombie's burst should reach the next one (far health " + far.getHealth()
                + ", from edge " + far.position().subtract(edge.position()) + ", edge dead at " + edge.position() + ")"));
        helper.assertValueEqual(BloodPool.get(player), 0, "blood from killing zombies (they're undead, so none)");
        removePlayer(helper, player);
        helper.succeed();
    }

    private static Zombie spawnWeakZombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setHealth(2.0F);
        return zombie;
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

    static void translatorTurnsTomesIntoSkills(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, ModBlocks.ANCIENT_TRANSLATOR.get());
        ServerPlayer player = makePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BloodSkill.DAYWALKER.tome()));
        helper.useBlock(pos, player);
        helper.assertTrue(player.getMainHandItem().isEmpty(), Component.literal("The translator should take the tome"));
        helper.assertBlockProperty(pos, AncientTranslatorBlock.TOME, BloodSkill.DAYWALKER.ordinal() + 1);
        removePlayer(helper, player);
        helper.runAfterDelay(AncientTranslatorBlock.TRANSLATION_TIME + 2, () -> {
            helper.assertBlockProperty(pos, AncientTranslatorBlock.TOME, 0);
            helper.assertItemEntityPresent(BloodSkill.DAYWALKER.skillItem(), pos.above(), 2.0);
            helper.succeed();
        });
    }

    static void brokenTranslatorGivesTheTomeBack(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, ModBlocks.ANCIENT_TRANSLATOR.get().defaultBlockState().setValue(AncientTranslatorBlock.TOME, BloodSkill.BLOOD_PARADE.ordinal() + 1));
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.assertItemEntityPresent(BloodSkill.BLOOD_PARADE.tome(), pos, 2.0);
        helper.assertItemEntityPresent(ModItems.ANCIENT_TRANSLATOR.get(), pos, 2.0);
        helper.succeed();
    }

    static void translatorRecipeUsesHuntersMarks(GameTestHelper helper) {
        var recipe = helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id("ancient_translator")));
        helper.assertTrue(recipe.isPresent(), Component.literal("Missing the Ancient Translator recipe"));
        helper.succeed();
    }

    // ---- Where tomes come from ----

    static void ancientButlerSellsTomes(GameTestHelper helper) {
        Villager butler = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        butler.setVillagerData(butler.getVillagerData().withProfession(ModVillagers.ANCIENT_BUTLER).withLevel(1));
        MerchantOffer dash = butler.getOffers().stream().filter(offer -> offer.getResult().is(BloodSkill.DASH.tome())).findFirst().orElse(null);
        helper.assertTrue(dash != null, Component.literal("A novice Ancient Butler should sell the Dash tome"));
        helper.assertTrue(dash.getCostB().is(ModItems.HUNTERS_MARK.get()), Component.literal("Tomes should cost Hunter's Marks, not Tokens of Blood"));
        boolean sellsBlood = butler.getOffers().stream().anyMatch(offer -> offer.getResult().is(ModItems.BLOOD_BOTTLE.get()));
        helper.assertTrue(sellsBlood, Component.literal("A novice Ancient Butler should sell Blood Bottles"));
        butler.discard();
        helper.succeed();
    }

    static void tomesTurnUpInChests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable library = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.withDefaultNamespace("chests/stronghold_library")));
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO)).create(LootContextParamSets.CHEST);
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
}
