package com.zestudio.crimsoncovenant.gametest;

import java.util.List;
import java.util.ArrayList;

import com.zestudio.crimsoncovenant.block.RatBurrowBlock;
import com.zestudio.crimsoncovenant.block.RatBurrowBlockEntity;
import com.zestudio.crimsoncovenant.block.RatTrapBlock;
import com.zestudio.crimsoncovenant.effect.MiasmaEffect;
import com.zestudio.crimsoncovenant.effect.CleanseEffect;
import com.zestudio.crimsoncovenant.effect.PlagueEffect;
import com.zestudio.crimsoncovenant.effect.Rabies;
import com.zestudio.crimsoncovenant.entity.MiasmaCloud;
import com.zestudio.crimsoncovenant.entity.PlagueDoctor;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.VampireBat;
import com.zestudio.crimsoncovenant.network.SkillPayloads;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.world.RatSpawner;
import com.zestudio.crimsoncovenant.world.VillageRats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.animal.cow.Cow;
import net.neoforged.neoforge.event.EventHooks;

/// Rats: their eating and breeding, their burrows, the Miasma, the Plague, and the trap. And rabies, which rats carry
/// among others.
final class RatGameTests {
    private RatGameTests() {}

    private static List<Rat> ratsIn(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Rat.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0));
    }

    static void ratsEatCropsAndHaveLitters(GameTestHelper helper) {
        BlockPos[] fields = {new BlockPos(2, 1, 2), new BlockPos(6, 1, 2)};
        for (BlockPos field : fields) {
            helper.setBlock(field.below(), Blocks.FARMLAND);
            helper.setBlock(field, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        }
        Rat first = helper.spawn(ModEntities.RAT.get(), new BlockPos(3, 1, 4));
        Rat second = helper.spawn(ModEntities.RAT.get(), new BlockPos(4, 1, 4));
        helper.assertTrue(Rat.eatCrop(first, helper.absolutePos(fields[0])) && Rat.eatCrop(second, helper.absolutePos(fields[1])),
                Component.literal("Both rats should eat a crop"));
        for (BlockPos field : fields) {
            helper.assertTrue(helper.getBlockState(field).isAir(), Component.literal("An eaten crop should be gone"));
        }
        helper.assertTrue(first.isInLove() && second.isInLove(), Component.literal("Rats that have eaten should be ready to breed"));
        helper.succeedWhen(() -> {
            List<Rat> babies = ratsIn(helper).stream().filter(Rat::isBaby).toList();
            helper.assertValueEqual(babies.size(), 2, "young rats in the litter");
            helper.assertTrue(babies.stream().allMatch(baby -> baby.getAge() <= -23000), Component.literal("Young rats should take a day to grow up"));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0)).isEmpty(),
                    Component.literal("Rats breeding shouldn't leave experience behind"));
        });
    }

    static void ratsEatHayAndPumpkins(GameTestHelper helper) {
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 4));
        BlockPos[] stores = {new BlockPos(2, 1, 2), new BlockPos(4, 1, 2), new BlockPos(6, 1, 2)};
        helper.setBlock(stores[0], Blocks.HAY_BLOCK);
        helper.setBlock(stores[1], Blocks.PUMPKIN);
        helper.setBlock(stores[2], Blocks.MELON);
        for (BlockPos store : stores) {
            helper.assertTrue(Rat.eatCrop(rat, helper.absolutePos(store)) && helper.getBlockState(store).isAir(),
                    Component.literal("Rats should eat " + store + " down to nothing"));
        }
        helper.setBlock(stores[0], Blocks.OAK_PLANKS);
        helper.assertTrue(!Rat.eatCrop(rat, helper.absolutePos(stores[0])), Component.literal("Rats don't eat planks"));
        helper.succeed();
    }

    static void ratsEatRottenFlesh(GameTestHelper helper) {
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 4));
        ItemEntity flesh = new ItemEntity(helper.getLevel(), rat.getX(), rat.getY(), rat.getZ(), new ItemStack(Items.ROTTEN_FLESH, 2));
        helper.getLevel().addFreshEntity(flesh);
        Rat.eatDropped(rat, flesh);
        helper.assertValueEqual(flesh.getItem().getCount(), 1, "rotten flesh left after a rat's meal");
        helper.assertTrue(rat.isInLove(), Component.literal("Rotten flesh should get a rat breeding too"));
        flesh.discard();
        helper.succeed();
    }

    static void ratsLiveInBurrowsAndDefendThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos dirt = new BlockPos(4, 1, 4);
        helper.setBlock(dirt, Blocks.DIRT);
        Rat digger = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 3));
        BlockPos burrowPos = helper.absolutePos(dirt);
        Rat.digInto(digger, burrowPos, Direction.NORTH);
        helper.assertTrue(level.getBlockState(burrowPos).is(ModBlocks.RAT_BURROW.get()), Component.literal("The rat should dig a burrow into the earth"));
        RatBurrowBlockEntity burrow = (RatBurrowBlockEntity) level.getBlockEntity(burrowPos);
        helper.assertTrue(digger.isRemoved() && burrow.occupantCount() == 1, Component.literal("The rat should be inside its burrow"));

        Rat neighbour = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 3));
        helper.assertTrue(burrow.tryEnter(neighbour), Component.literal("Another rat should move in"));

        // Break it open, and out they come, angry
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 1.5));
        player.teleportTo(at.x, at.y, at.z);
        BlockState state = level.getBlockState(burrowPos);
        state.getBlock().playerWillDestroy(level, burrowPos, state, player);
        level.removeBlock(burrowPos, false);
        List<Rat> rats = ratsIn(helper);
        helper.assertValueEqual(rats.size(), 2, "rats out of the broken burrow");
        helper.assertTrue(rats.stream().allMatch(Rat::isAngry), Component.literal("Rats whose home was broken should be angry"));
        rats.forEach(Rat::discard);
        SkillGameTests.removePlayer(helper, player);

        // ...and they go for whoever broke it. (Mobs can't target the always-creative mock player, so a villager
        // stands in as the culprit here.)
        helper.setBlock(dirt, Blocks.DIRT);
        Rat lodger = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 3));
        Rat.digInto(lodger, burrowPos, Direction.NORTH);
        Villager culprit = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
        ((RatBurrowBlockEntity) level.getBlockEntity(burrowPos)).releaseAll(culprit);
        List<Rat> released = ratsIn(helper);
        helper.assertTrue(released.size() == 1 && released.getFirst().getTarget() == culprit, Component.literal("The rats should go for whoever broke their burrow"));
        released.forEach(Rat::discard);
        helper.succeed();
    }

    static void burrowSpotsAreFound(GameTestHelper helper) {
        // A bank of earth with open ground in front of it
        BlockPos dirt = new BlockPos(4, 1, 4);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(dirt.above(), Blocks.STONE);
        var spot = Rat.findBurrowSpot(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 2)), 4);
        helper.assertTrue(spot.isPresent() && spot.get().pos().equals(helper.absolutePos(dirt)) && spot.get().face().getAxis().isHorizontal(),
                Component.literal("A rat should find the side of an earth bank to dig into, found " + spot));
        helper.succeed();
    }

    static void miasmaOverAnInfestedVillage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A bell, claimed as a meeting point the way villagers do, makes this a village
        BlockPos bell = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.BELL);
        level.getPoiManager().take(type -> type.is(PoiTypes.MEETING), (type, pos) -> pos.equals(bell), bell, 2);
        List<Rat> rats = new java.util.ArrayList<>();
        for (int i = 0; i <= Rat.MIASMA_THRESHOLD; i++) {
            rats.add(helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(1 + i % 6, 1, 6 + i / 6)));
        }
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(5, 1, 4));
        vampire.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));

        // Vampires breathe it easily, and so do the rats
        helper.assertTrue(MiasmaEffect.isImmune(vampire) && MiasmaEffect.isImmune(rats.getFirst()) && !MiasmaEffect.isImmune(villager),
                Component.literal("Vampires and rats shrug off the Miasma, villagers don't"));
        helper.assertTrue(!vampire.addEffect(new MobEffectInstance(ModEffects.MIASMA, 100)), Component.literal("The Miasma shouldn't take hold of a vampire"));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 2));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 2));
        helper.assertTrue(MiasmaEffect.isImmune(zombie) && MiasmaEffect.isImmune(golem), Component.literal("The undead and iron golems shrug off the Miasma too"));
        zombie.discard();
        golem.discard();
        // Long exposure blinds: the count of seconds runs on while the breather stays, and starts over once they leave
        long now = level.getGameTime();
        int seconds = 0;
        for (int i = 0; i < MiasmaCloud.BLINDING_SECONDS; i++) {
            seconds = MiasmaCloud.breathe(villager, now + i * 20L);
        }
        helper.assertValueEqual(seconds, MiasmaCloud.BLINDING_SECONDS, "seconds in the Miasma");
        helper.assertValueEqual(MiasmaCloud.breathe(villager, now + 10_000L), 1, "seconds in the Miasma after a long time away");
        villager.setData(ModAttachments.MIASMA_EXPOSURE, ModAttachments.MiasmaExposure.NONE);

        AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16.0);
        helper.startSequence()
                // The game notices the bell has made a village a moment after it's placed
                .thenWaitUntil(() -> {
                    rats.getFirst().checkForMiasma(level);
                    helper.assertValueEqual(level.getEntitiesOfClass(MiasmaCloud.class, around).size(), 1, "Miasma clouds over a village with more than ten rats (village "
                            + level.isVillage(rats.getFirst().blockPosition()) + ", rats " + Rat.countRatsNear(level, rats.getFirst().position(), Rat.VILLAGE_RANGE) + ")");
                })
                .thenExecute(() -> {
                    rats.getFirst().checkForMiasma(level);
                    helper.assertValueEqual(level.getEntitiesOfClass(MiasmaCloud.class, around).size(), 1,
                            "Miasma clouds once the rats have gathered it twice (it should thicken, not double)");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(villager.hasEffect(ModEffects.MIASMA) && villager.hasEffect(MobEffects.NAUSEA), Component.literal("The villager should be sickened by the Miasma"));
                    helper.assertTrue(!vampire.hasEffect(ModEffects.MIASMA), Component.literal("The vampire should be untouched"));
                    helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("The Miasma should poison the villager"));
                })
                .thenExecute(() -> level.getEntitiesOfClass(MiasmaCloud.class, around).forEach(MiasmaCloud::discard))
                .thenSucceed();
    }

    static void ratTrapsKillRats(GameTestHelper helper) {
        BlockPos ratTrap = new BlockPos(2, 1, 4);
        BlockPos villagerTrap = new BlockPos(6, 1, 4);
        helper.setBlock(ratTrap, ModBlocks.RAT_TRAP.get().defaultBlockState().setValue(RatTrapBlock.BAITED, true));
        helper.setBlock(villagerTrap, ModBlocks.RAT_TRAP.get());
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), ratTrap);
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, villagerTrap);
        helper.succeedWhen(() -> {
            helper.assertTrue(!rat.isAlive(), Component.literal("The trap should kill the rat"));
            helper.assertBlockProperty(ratTrap, RatTrapBlock.SPRUNG, true);
            helper.assertBlockProperty(ratTrap, RatTrapBlock.BAITED, false);
            helper.assertBlockProperty(villagerTrap, RatTrapBlock.SPRUNG, false);
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), Component.literal("Only rats set off a rat trap"));
        });
    }

    static void ratsTakeTheBait(GameTestHelper helper) {
        BlockPos trap = new BlockPos(6, 1, 4);
        helper.setBlock(trap, ModBlocks.RAT_TRAP.get().defaultBlockState().setValue(RatTrapBlock.BAITED, true));
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(1, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(!rat.isAlive(), Component.literal("The rat should go for the bait and get caught, but it's at " + helper.relativeVec(rat.position())));
            helper.assertBlockProperty(trap, RatTrapBlock.SPRUNG, true);
        });
    }

    static void catsCatchRats(GameTestHelper helper) {
        Cat cat = helper.spawn(EntityTypes.CAT, new BlockPos(1, 1, 4));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(7, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(!rat.isAlive(), Component.literal("The cat should run the rat down"));
            cat.discard();
        });
    }

    static void ratsChewBeforeTheyEat(GameTestHelper helper) {
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(3, 1, 4));
        ItemEntity flesh = new ItemEntity(helper.getLevel(), rat.getX() + 1.0, rat.getY(), rat.getZ(), new ItemStack(Items.ROTTEN_FLESH, 2));
        flesh.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(flesh);
        helper.startSequence()
                .thenExecuteAfter(15, () -> helper.assertValueEqual(flesh.getItem().getCount(), 2, "rotten flesh left a moment after the rat reached it"))
                .thenWaitUntil(() -> helper.assertValueEqual(flesh.getItem().getCount(), 1, "rotten flesh left once the rat has chewed through a piece"))
                .thenExecute(() -> {
                    flesh.discard();
                    rat.discard();
                })
                .thenSucceed();
    }

    static void youngRatsGrowFasterAndLiveWithTheirParents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rat baby = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 2));
        baby.setAge(-24000);
        baby.feed();
        helper.assertTrue(baby.getAge() > -24000 + 4000, Component.literal("A young rat that eats should grow up faster, age " + baby.getAge()));

        BlockPos home = helper.absolutePos(new BlockPos(6, 1, 6));
        Rat mother = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 4));
        Rat father = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(5, 1, 4));
        mother.setHomePos(home);
        mother.spawnChildFromBreeding(level, father);
        List<Rat> litter = ratsIn(helper).stream().filter(rat -> rat.isBaby() && rat != baby).toList();
        helper.assertTrue(litter.size() == 2 && litter.stream().allMatch(young -> home.equals(young.getHomePos())),
                Component.literal("The litter should share their parents' burrow"));
        helper.assertTrue(RatBurrowBlockEntity.CAPACITY >= 4, Component.literal("A burrow should have room for a pair and their litter"));
        helper.assertTrue(baby.getPathfindingMalus(PathType.WATER) < 0.0F, Component.literal("Rats should keep out of the water"));
        helper.succeed();
    }

    static void farmAnimalsCryOutAtRats(GameTestHelper helper) {
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 4));
        helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(5, 1, 4));
        helper.spawnWithNoFreeWill(EntityTypes.HORSE, new BlockPos(6, 1, 2));
        helper.assertValueEqual(rat.alarmFarmAnimals(helper.getLevel()), 2, "farm animals raising the alarm at a rat");
        helper.assertValueEqual(rat.alarmFarmAnimals(helper.getLevel()), 0, "farm animals raising the alarm again straight away");
        helper.succeed();
    }

    static void ratsGnawIntoWoodFromTheSide(GameTestHelper helper) {
        // A plank wall with open ground in front of it: a rat gnaws in from the side
        BlockPos wall = new BlockPos(4, 1, 4);
        helper.setBlock(wall, Blocks.OAK_PLANKS);
        helper.setBlock(wall.above(), Blocks.OAK_PLANKS);
        var spot = Rat.findBurrowSpot(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 2)), 4);
        helper.assertTrue(spot.isPresent() && spot.get().pos().equals(helper.absolutePos(wall)) && spot.get().face().getAxis().isHorizontal(),
                Component.literal("A rat should find the side of a wooden wall to gnaw into, found " + spot));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 3));
        Rat.digInto(rat, spot.get().pos(), spot.get().face());
        helper.assertBlockPresent(ModBlocks.RAT_HOLE.get(), wall);

        // A plank floor, open only above, is no good: rats only gnaw into wood from the side
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.OAK_PLANKS);
        var fromAbove = Rat.findBurrowSpot(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 0);
        helper.assertTrue(fromAbove.isEmpty() || !fromAbove.get().pos().equals(helper.absolutePos(new BlockPos(1, 0, 1))),
                Component.literal("Rats shouldn't gnaw down into a wooden floor"));
        helper.succeed();
    }

    static void miasmaRisesFromBurrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos burrow = new BlockPos(6, 1, 6);
        helper.setBlock(burrow, ModBlocks.RAT_BURROW.get().defaultBlockState().setValue(RatBurrowBlock.FACING, Direction.WEST));
        Rat homeless = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 2));
        helper.assertTrue(homeless.miasmaSource(level).equals(helper.absolutePos(burrow.west())), Component.literal("The Miasma should rise from the nearest burrow"));
        Rat housed = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(3, 1, 2));
        BlockPos home = new BlockPos(1, 1, 6);
        helper.setBlock(home, ModBlocks.RAT_BURROW.get().defaultBlockState().setValue(RatBurrowBlock.FACING, Direction.EAST));
        housed.setHomePos(helper.absolutePos(home));
        helper.assertTrue(housed.miasmaSource(level).equals(helper.absolutePos(home.east())), Component.literal("A rat's Miasma should rise from its own burrow"));
        helper.succeed();
    }

    static void ratsBoltFromVillagers(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 4));
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(5, 1, 4));
        double start = rat.distanceTo(villager);
        helper.succeedWhen(() -> {
            helper.assertTrue(rat.distanceTo(villager) > start + 3.0, Component.literal("The rat should run from the villager, now " + rat.distanceTo(villager) + " away"));
            rat.discard();
        });
    }

    static void villagersKilledByRatsRiseAsZombies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 4));
        villager.hurtServer(level, level.damageSources().mobAttack(rat), 1000.0F);
        List<ZombieVillager> risen = level.getEntitiesOfClass(ZombieVillager.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0));
        helper.assertTrue(villager.isRemoved() && risen.size() == 1, Component.literal("A villager killed by a rat should rise as a zombie villager"));
        risen.forEach(ZombieVillager::discard);
        rat.discard();
        helper.succeed();
    }

    static void ratsHideAtHomeFromDanger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos burrow = new BlockPos(6, 1, 4);
        helper.setBlock(burrow, ModBlocks.RAT_BURROW.get().defaultBlockState().setValue(RatBurrowBlock.FACING, Direction.WEST));
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(3, 1, 4));
        rat.setHomePos(helper.absolutePos(burrow));
        helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 1, 4));
        helper.succeedWhen(() -> helper.assertTrue(rat.isRemoved() && level.getBlockEntity(helper.absolutePos(burrow)) instanceof RatBurrowBlockEntity home
                && home.occupantCount() == 1, Component.literal("A frightened rat with a burrow should hide in it")));
    }

    static void ratSwarmsHuntLivestock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 6));
        List<Rat> rats = new ArrayList<>();
        for (int i = 0; i < Rat.SWARM_SIZE - 1; i++) {
            rats.add(helper.spawn(ModEntities.RAT.get(), new BlockPos(1 + i % 3, 1, 1 + i / 3)));
        }
        helper.assertTrue(rats.getFirst().raiseSwarm() == null, Component.literal("Nine rats aren't a swarm yet"));
        rats.add(helper.spawn(ModEntities.RAT.get(), new BlockPos(1, 1, 4)));
        helper.assertTrue(rats.getFirst().raiseSwarm() == cow, Component.literal("Ten rats are a swarm, and should go for the cow"));
        helper.assertTrue(rats.stream().allMatch(rat -> rat.getTarget() == cow), Component.literal("The whole swarm should go for the cow at once"));
        helper.succeedWhen(() -> {
            helper.assertTrue(!cow.isAlive(), Component.literal("The swarm should bring the cow down"));
            helper.assertTrue(rats.stream().filter(Rat::isAlive).anyMatch(Rat::isSated), Component.literal("The swarm should feast on its kill"));
            List<ItemEntity> meat = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16.0), item -> item.getItem().is(Items.BEEF));
            helper.assertTrue(meat.isEmpty(), Component.literal("Nothing of the cow's meat should be left"));
            rats.forEach(Rat::discard);
        });
    }

    static void ratsEatAnyDroppedFood(GameTestHelper helper) {
        for (ItemStack food : List.of(new ItemStack(Items.BREAD), new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.APPLE), new ItemStack(Items.CAKE),
                new ItemStack(Items.PUMPKIN_PIE), new ItemStack(Items.WHEAT))) {
            helper.assertTrue(Rat.isScavenge(food), Component.literal("Rats should eat dropped " + food.getHoverName().getString()));
        }
        helper.assertTrue(!Rat.isScavenge(new ItemStack(Items.STONE)), Component.literal("Rats shouldn't eat stone"));
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(2, 1, 4));
        ItemEntity cake = new ItemEntity(helper.getLevel(), rat.getX() + 3.0, rat.getY(), rat.getZ(), new ItemStack(Items.CAKE));
        cake.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(cake);
        helper.succeedWhen(() -> {
            helper.assertTrue(cake.isRemoved(), Component.literal("A rat should go and eat a dropped cake"));
            rat.discard();
        });
    }

    static void ratsAreWorthALittleExperience(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 2));
        Rat young = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 2));
        young.setBaby(true);
        int grown = rat.getExperienceReward(level, null);
        helper.assertTrue(grown >= 1 && grown <= 3, Component.literal("A grown rat should be worth a little experience, not " + grown));
        helper.assertTrue(young.shouldDropExperience() && young.getExperienceReward(level, null) == 1, Component.literal("Even a young rat should be worth a bit"));
        rat.discard();
        young.discard();
        helper.succeed();
    }

    static void ratsTurnUpInVillages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spot = helper.absolutePos(new BlockPos(5, 1, 5));
        helper.assertValueEqual(RatSpawner.trySpawnIn(level, spot, level.getRandom()), 0, "rats turning up away from any village");
        // A bell, claimed as a meeting point the way villagers do, makes this a village
        BlockPos bell = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.BELL);
        level.getPoiManager().take(type -> type.is(PoiTypes.MEETING), (type, pos) -> pos.equals(bell), bell, 2);
        int came = RatSpawner.trySpawnIn(level, spot, level.getRandom());
        helper.assertTrue(came >= 1 && came <= 2, Component.literal("A rat or two should turn up in a village at night, not " + came));
        level.getEntitiesOfClass(Rat.class, new AABB(spot).inflate(4.0)).forEach(Rat::discard);
        level.getPoiManager().release(bell);
        helper.succeed();
    }

    static void newVillagesHaveRatsBurrowedIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(3, 1, 3), new BlockPos(5, 1, 5))) {
            helper.setBlock(pos, Blocks.DIRT);
        }
        BlockPos centre = helper.absolutePos(new BlockPos(4, 1, 4));
        int dug = VillageRats.settle(level, centre, 1, level.getRandom());
        helper.assertTrue(dug >= 1, Component.literal("A new village should have rats burrowed in"));
        List<RatBurrowBlockEntity> burrows = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-5, -2, -5), centre.offset(5, 3, 5))) {
            if (level.getBlockEntity(pos) instanceof RatBurrowBlockEntity burrow) {
                burrows.add(burrow);
            }
        }
        helper.assertTrue(!burrows.isEmpty() && burrows.stream().allMatch(burrow -> burrow.occupantCount() >= 2),
                Component.literal("Each burrow should have a family of rats in it"));
        helper.assertTrue(level.getEntitiesOfClass(Rat.class, new AABB(centre).inflate(8.0)).isEmpty(), Component.literal("The rats should be inside, not out"));
        for (RatBurrowBlockEntity burrow : burrows) {
            level.removeBlock(burrow.getBlockPos(), false);
        }
        level.getEntitiesOfClass(Rat.class, new AABB(centre).inflate(8.0)).forEach(Rat::discard);
        helper.succeed();
    }

    static void ratsShelterFromTheRain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        float rain = level.getRainLevel(1.0F);
        try {
            level.setRainLevel(0.0F);
            helper.assertTrue(Rat.keepsIndoors(level) == !level.isDarkOutside(), Component.literal("In fine weather, only daylight should keep rats in"));
            level.setRainLevel(1.0F);
            helper.assertTrue(Rat.keepsIndoors(level), Component.literal("Rats should keep to their burrows while it rains"));
        } finally {
            level.setRainLevel(rain);
        }
        helper.succeed();
    }

    static void ironGolemsHuntRats(GameTestHelper helper) {
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 4));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(6, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(golem.getTarget() == rat, Component.literal("The iron golem should go after the rat"));
            rat.discard();
            golem.discard();
        });
    }

    static void plagueSapsTheLivingButNotTheUndead(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 4));
        helper.assertTrue(villager.addEffect(new MobEffectInstance(ModEffects.PLAGUE, 400)), Component.literal("Villagers can catch the Plague"));
        helper.assertTrue(!zombie.addEffect(new MobEffectInstance(ModEffects.PLAGUE, 400)) && PlagueEffect.isImmune(rat),
                Component.literal("The undead, and the rats that carry it, don't catch the Plague"));
        helper.succeedWhen(() -> helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("The Plague should sap the villager")));
    }

    static void onlyVampiresOpenTheSkills(GameTestHelper helper) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        helper.assertTrue(!SkillPayloads.tryOpenSkillsScreen(player) && player.containerMenu == player.inventoryMenu,
                Component.literal("Someone who isn't a vampire shouldn't get the blood skills menu"));
        player.setData(ModAttachments.IS_VAMPIRE, true);
        helper.assertTrue(SkillPayloads.tryOpenSkillsScreen(player) && player.containerMenu != player.inventoryMenu,
                Component.literal("A vampire should get the blood skills menu"));
        player.closeContainer();
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    // ---- Rabies ----

    static void rabiesCarriersAndTheirOdds(GameTestHelper helper) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(2, 1, 2));
        Wolf pet = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(3, 1, 2));
        pet.setTame(true, false);
        Fox fox = helper.spawnWithNoFreeWill(EntityTypes.FOX, new BlockPos(4, 1, 2));
        VampireBat bat = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE_BAT.get(), new BlockPos(5, 2, 2));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(6, 1, 2));
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(4, 1, 6));
        helper.assertValueEqual(Rabies.carrierOdds(wolf), 10, "odds (one in) of a wild wolf carrying rabies");
        helper.assertValueEqual(Rabies.carrierOdds(pet), 0, "odds of a tame wolf carrying rabies");
        helper.assertValueEqual(Rabies.carrierOdds(fox), 8, "odds (one in) of a fox carrying rabies");
        helper.assertValueEqual(Rabies.carrierOdds(bat), 6, "odds (one in) of a vampire bat carrying rabies");
        helper.assertValueEqual(Rabies.carrierOdds(rat), 5, "odds (one in) of a rat carrying rabies");
        helper.assertValueEqual(Rabies.carrierOdds(cow), 0, "odds of a cow carrying rabies");

        // Born in the wild, sooner or later one carries it, already some way along; bred, never
        RandomSource random = helper.getLevel().getRandom();
        boolean carried = false;
        for (int i = 0; i < 200 && !carried; i++) {
            carried = Rabies.mayCarry(fox, EntitySpawnReason.NATURAL, random);
        }
        MobEffectInstance hydrophobia = fox.getEffect(ModEffects.HYDROPHOBIA);
        helper.assertTrue(carried && hydrophobia != null, Component.literal("Some foxes born in the wild should carry Hydrophobia"));
        helper.assertTrue(hydrophobia.getDuration() >= Rabies.CARRIER_MIN_TICKS && hydrophobia.getDuration() < Rabies.CARRIER_MIN_TICKS + Rabies.CARRIER_SPREAD_TICKS,
                Component.literal("A carrier should be half a day to a day and a half from rabies, not " + hydrophobia.getDuration() + " ticks"));
        for (int i = 0; i < 200; i++) {
            Rabies.mayCarry(rat, EntitySpawnReason.BREEDING, random);
        }
        helper.assertTrue(!Rabies.isSick(rat), Component.literal("Bred animals shouldn't be born with rabies"));
        for (var mob : List.of(wolf, pet, fox, bat, rat, cow)) {
            mob.discard();
        }
        helper.succeed();
    }

    static void whoCatchesRabies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cat cat = helper.spawnWithNoFreeWill(EntityTypes.CAT, new BlockPos(2, 1, 2));
        Wolf wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(3, 1, 2));
        VampireBat bat = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE_BAT.get(), new BlockPos(4, 2, 2));
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(2, 1, 6));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(3, 1, 6));
        IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(5, 1, 6));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(7, 1, 6));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(7, 1, 2));
        vampire.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        // Made but never put in the world: a wither in a test arena would blow it apart
        WitherBoss wither = EntityTypes.WITHER.create(level, EntitySpawnReason.COMMAND);

        helper.assertTrue(Rabies.infect(cat, Rabies.HYDROPHOBIA_TICKS) && Rabies.infect(wolf, Rabies.HYDROPHOBIA_TICKS) && Rabies.infect(bat, Rabies.HYDROPHOBIA_TICKS),
                Component.literal("Cats, wolves and vampire bats should catch rabies"));
        helper.assertTrue(!Rabies.infect(cow, Rabies.HYDROPHOBIA_TICKS) && !Rabies.infect(villager, Rabies.HYDROPHOBIA_TICKS),
                Component.literal("Creatures that can't attack shouldn't catch rabies"));
        helper.assertTrue(!Rabies.infect(golem, Rabies.HYDROPHOBIA_TICKS), Component.literal("Iron golems shouldn't catch rabies"));
        helper.assertTrue(!Rabies.infect(zombie, Rabies.HYDROPHOBIA_TICKS) && !Rabies.infect(vampire, Rabies.HYDROPHOBIA_TICKS),
                Component.literal("The undead, vampires among them, shouldn't catch rabies"));
        helper.assertTrue(wither != null && !Rabies.canCatch(wither), Component.literal("Bosses shouldn't catch rabies"));
        for (var mob : List.of(cat, wolf, bat, cow, villager, golem, zombie, vampire)) {
            mob.discard();
        }
        helper.succeed();
    }

    static void hydrophobiaIsCleansedButRabiesIsNot(GameTestHelper helper) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(2, 1, 4));
        Fox fox = helper.spawnWithNoFreeWill(EntityTypes.FOX, new BlockPos(6, 1, 4));
        Rabies.infect(wolf, Rabies.HYDROPHOBIA_TICKS);
        Rabies.infect(fox, Rabies.HYDROPHOBIA_TICKS);
        // Milk takes every effect away: not this one
        fox.removeAllEffects();
        helper.assertTrue(Rabies.isHydrophobic(fox), Component.literal("Milk shouldn't cure Hydrophobia"));
        CleanseEffect.cleanse(wolf);
        helper.assertTrue(!Rabies.isSick(wolf), Component.literal("A Potion of Cleanse should cure Hydrophobia"));

        Rabies.becomeRabid(fox);
        helper.assertTrue(Rabies.isRabid(fox) && !Rabies.isHydrophobic(fox), Component.literal("Hydrophobia should give way to Rabies"));
        CleanseEffect.cleanse(fox);
        fox.removeAllEffects();
        helper.assertTrue(Rabies.isRabid(fox), Component.literal("Nothing should cure Rabies"));
        wolf.discard();
        fox.discard();
        helper.succeed();
    }

    static void hydrophobiaTurnsIntoRabies(GameTestHelper helper) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(4, 1, 4));
        Rabies.infect(wolf, 10);
        helper.succeedWhen(() -> {
            MobEffectInstance rabies = wolf.getEffect(ModEffects.RABIES);
            helper.assertTrue(rabies != null && !Rabies.isHydrophobic(wolf), Component.literal("Left alone, Hydrophobia should turn into Rabies"));
            helper.assertTrue(rabies.getDuration() > Rabies.RABIES_TICKS - 100, Component.literal("A rabid animal should have two days to live"));
            // Its foam gathers at its jaws, in front of its eyes
            Vec3 mouth = Rabies.mouth(wolf);
            Vec3 eyes = wolf.getEyePosition();
            helper.assertTrue(mouth.subtract(eyes).horizontalDistance() > wolf.getBbWidth() * 0.4 && Math.abs(mouth.y - eyes.y) < 0.3,
                    Component.literal("A rabid wolf should foam at the mouth, not elsewhere"));
            wolf.discard();
        });
    }

    static void rabiesRunsItsCourse(GameTestHelper helper) {
        Cat cat = helper.spawnWithNoFreeWill(EntityTypes.CAT, new BlockPos(4, 1, 4));
        cat.addEffect(new MobEffectInstance(ModEffects.RABIES, 10));
        helper.succeedWhen(() -> helper.assertTrue(!cat.isAlive(), Component.literal("A rabid cat should die when rabies runs its course")));
    }

    static void totemsSeeYouThroughRabies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Rabies running its course, with a totem in hand
        Cat cat = helper.spawnWithNoFreeWill(EntityTypes.CAT, new BlockPos(2, 1, 4));
        cat.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        cat.addEffect(new MobEffectInstance(ModEffects.RABIES, 10));
        // Any death the totem saves it from burns the sickness out too
        Wolf wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(6, 1, 4));
        wolf.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        Rabies.infect(wolf, Rabies.HYDROPHOBIA_TICKS);
        wolf.hurtServer(level, level.damageSources().generic(), 1000.0F);
        helper.assertTrue(wolf.isAlive() && wolf.getMainHandItem().isEmpty(), Component.literal("The wolf's totem should save it"));
        helper.assertTrue(!Rabies.isSick(wolf), Component.literal("A totem saving the wolf should cure its Hydrophobia"));
        helper.succeedWhen(() -> {
            helper.assertTrue(cat.isAlive() && cat.getMainHandItem().isEmpty(), Component.literal("A totem should save the cat from dying of rabies"));
            helper.assertTrue(!Rabies.isSick(cat), Component.literal("Saved by a totem, the cat should be over rabies"));
            cat.discard();
            wolf.discard();
        });
    }

    static void defyDeathSeesAVampireThroughRabies(GameTestHelper helper) {
        // Rabid before it turned: vampires can't catch it, but a rabid player who turns keeps it
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        player.addEffect(new MobEffectInstance(ModEffects.RABIES, 400));
        player.setData(ModAttachments.IS_VAMPIRE, true);
        BloodPool.set(player, 50);
        player.addEffect(new MobEffectInstance(ModEffects.DEFY_DEATH, 100));
        helper.assertTrue(Rabies.isRabid(player), Component.literal("The vampire should still have rabies"));
        // Mock players can't be hurt, so the death rabies brings is played out by hand (as in the Defy Death test)
        player.setHealth(0.0F);
        player.die(helper.getLevel().damageSources().source(ModDamageTypes.RABIES));
        helper.assertTrue(player.getHealth() > 0.0F, Component.literal("Defy Death should turn aside the death rabies brings"));
        helper.assertTrue(!Rabies.isRabid(player), Component.literal("Having cheated its death, the vampire should be over rabies"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void rabidBitesSpreadHydrophobia(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Wolf rabid = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(2, 1, 4));
        Wolf sick = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(2, 1, 6));
        Cat cat = helper.spawnWithNoFreeWill(EntityTypes.CAT, new BlockPos(4, 1, 4));
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(6, 1, 4));
        Wolf other = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(6, 1, 6));
        Rabies.becomeRabid(rabid);
        Rabies.infect(sick, Rabies.HYDROPHOBIA_TICKS);
        helper.assertTrue(!Rabies.bite(rabid, other, 0.5F), Component.literal("Nine bites in ten shouldn't pass it on"));
        helper.assertTrue(!Rabies.bite(sick, other, 0.0F), Component.literal("Only the rabid should pass it on, not the merely hydrophobic"));
        helper.assertTrue(Rabies.bite(rabid, cat, 0.05F) && Rabies.isHydrophobic(cat), Component.literal("One bite in ten should pass Hydrophobia on"));
        helper.assertTrue(!Rabies.bite(rabid, cow, 0.0F), Component.literal("A cow can't catch it"));

        // Bitten to hurt, never to kill: the cow is left with half a heart
        cow.setHealth(2.0F);
        cow.hurtServer(level, level.damageSources().mobAttack(rabid), 10.0F);
        helper.assertTrue(cow.isAlive() && cow.getHealth() >= 1.0F, Component.literal("A rabid animal shouldn't bite another animal to death"));

        helper.assertTrue(Rabies.rabidExperience(3) > 3, Component.literal("A rabid animal should be worth more experience"));
        helper.assertTrue(Rabies.refusesToEat(sick, new ItemStack(Items.BEEF)) && !Rabies.refusesToEat(other, new ItemStack(Items.BEEF)),
                Component.literal("A hydrophobic wolf should turn its nose up at meat"));
        for (var mob : List.of(rabid, sick, cat, cow, other)) {
            mob.discard();
        }
        helper.succeed();
    }

    static void rabidAnimalsGoForThoseNear(GameTestHelper helper) {
        Fox fox = helper.spawn(EntityTypes.FOX, new BlockPos(2, 1, 4));
        Cow near = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(4, 1, 4));
        Cow far = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(8, 1, 8));
        near.setHealth(3.0F);
        Rabies.becomeRabid(fox);
        helper.assertTrue(Rabies.wantsToBite(fox, near) && !Rabies.wantsToBite(fox, far), Component.literal("A rabid fox should go only for animals that come close"));
        helper.succeedWhen(() -> {
            helper.assertTrue(near.getHealth() < 3.0F, Component.literal("The rabid fox should bite the cow beside it"));
            helper.assertTrue(near.isAlive(), Component.literal("The rabid fox shouldn't kill the cow"));
            fox.discard();
            near.discard();
            far.discard();
        });
    }

    static void hydrophobicAnimalsRunFromWater(GameTestHelper helper) {
        // Sunk into the floor, so it can't spread across the arena
        helper.setBlock(new BlockPos(1, 0, 4), Blocks.WATER);
        Vec3 water = helper.absoluteVec(new Vec3(1.5, 0.5, 4.5));
        Wolf wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(2, 1, 4));
        Rabies.infect(wolf, Rabies.HYDROPHOBIA_TICKS);
        helper.succeedWhen(() -> {
            helper.assertTrue(wolf.position().distanceTo(water) > 4.0, Component.literal("A hydrophobic wolf should run from water"));
            wolf.discard();
        });
    }

    static void rabidAnimalsFearWaterToo(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 4), Blocks.WATER);
        Vec3 water = helper.absoluteVec(new Vec3(1.5, 0.5, 4.5));
        Wolf wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(2, 1, 4));
        Rabies.becomeRabid(wolf);
        helper.succeedWhen(() -> {
            helper.assertTrue(wolf.position().distanceTo(water) > 4.0, Component.literal("A rabid wolf should run from water as a hydrophobic one does"));
            wolf.discard();
        });
    }

    static void rabidAnimalsLeaveThoseByWaterAlone(GameTestHelper helper) {
        Fox fox = helper.spawn(EntityTypes.FOX, new BlockPos(2, 1, 4));
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(5, 1, 4));
        float health = cow.getHealth();
        Rabies.becomeRabid(fox);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fox.getTarget() == cow, Component.literal("The rabid fox should go for the cow")))
                .thenExecute(() -> {
                    // Water appears beside the cow while the fox is still on its way: it's as good as reaching it
                    helper.setBlock(new BlockPos(5, 0, 6), Blocks.WATER);
                    helper.assertTrue(Rabies.byWater(cow) && !Rabies.wantsToBite(fox, cow), Component.literal("A rabid animal shouldn't want anyone standing by water"));
                })
                .thenWaitUntil(() -> helper.assertTrue(fox.getTarget() != cow, Component.literal("The rabid fox should give up on a cow by water")))
                .thenExecuteAfter(40, () -> {
                    helper.assertValueEqual(cow.getHealth(), health, "the health of the cow by the water");
                    fox.discard();
                    cow.discard();
                })
                .thenSucceed();
    }

    static void rabidAnimalsSnapAtWhatCantCatchIt(GameTestHelper helper) {
        Fox fox = helper.spawn(EntityTypes.FOX, new BlockPos(2, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 7));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 2));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Villager far = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(8, 1, 8));
        villager.setHealth(2.0F);
        Rabies.becomeRabid(fox);
        helper.assertTrue(Rabies.wantsToBite(fox, villager) && Rabies.wantsToBite(fox, golem) && Rabies.wantsToBite(fox, zombie),
                Component.literal("A rabid animal should go for anything close, whether it can catch rabies or not"));
        helper.assertTrue(!Rabies.wantsToBite(fox, far), Component.literal("A rabid animal should leave be mobs that keep their distance"));
        // Only the villager stays, so it's the one bitten
        golem.discard();
        zombie.discard();
        far.discard();
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getHealth() < 2.0F, Component.literal("The rabid fox should bite the villager beside it"));
            helper.assertTrue(villager.isAlive(), Component.literal("The rabid fox shouldn't bite the villager to death"));
            helper.assertTrue(!Rabies.isSick(villager) && !Rabies.bite(fox, villager, 0.0F), Component.literal("Bitten or not, a villager can't catch rabies"));
            helper.assertTrue(fox.getTarget() == null && !Rabies.wantsToBite(fox, villager), Component.literal("Having snapped at the villager, the fox should leave it a while"));
            fox.discard();
            villager.discard();
        });
    }

    static void healthyAnimalsKeepClearOfTheRabid(GameTestHelper helper) {
        Wolf rabid = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(1, 1, 4));
        Wolf sick = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(1, 1, 7));
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 1, 4));
        Rabies.becomeRabid(rabid);
        Rabies.infect(sick, Rabies.HYDROPHOBIA_TICKS);
        helper.assertTrue(Rabies.shuns(cow, rabid), Component.literal("A healthy animal should keep clear of a rabid one"));
        helper.assertTrue(!Rabies.shuns(cow, sick), Component.literal("Hydrophobia alone shouldn't frighten other animals"));
        helper.assertTrue(!Rabies.shuns(sick, rabid), Component.literal("An animal that's sick itself shouldn't shy from the rabid"));
        sick.discard();
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.distanceTo(rabid) > 5.0F, Component.literal("The cow should move well away from the rabid wolf"));
            rabid.discard();
            cow.discard();
        });
    }

    static void plagueDoctorsCleanseHydrophobicAnimals(GameTestHelper helper) {
        // A corner walled off, with a sick wolf he can't see in it
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(6, 1, 5), new BlockPos(6, 3, 8))) {
            helper.setBlock(pos, Blocks.STONE);
        }
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(7, 1, 5), new BlockPos(8, 3, 5))) {
            helper.setBlock(pos, Blocks.STONE);
        }
        PlagueDoctor doctor = helper.spawn(ModEntities.PLAGUE_DOCTOR.get(), new BlockPos(2, 1, 4));
        Wolf seen = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(5, 1, 3));
        Wolf hidden = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(7, 1, 7));
        Fox rabid = helper.spawnWithNoFreeWill(EntityTypes.FOX, new BlockPos(2, 1, 7));
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Rabies.infect(seen, Rabies.HYDROPHOBIA_TICKS);
        Rabies.infect(hidden, Rabies.HYDROPHOBIA_TICKS);
        Rabies.becomeRabid(rabid);
        Rabies.infect(player, Rabies.HYDROPHOBIA_TICKS);
        helper.assertTrue(PlagueDoctor.needsCleansing(doctor, seen), Component.literal("The doctor should mean to cleanse a hydrophobic animal he can see"));
        helper.assertTrue(!PlagueDoctor.needsCleansing(doctor, hidden), Component.literal("The doctor can't cleanse what he can't see"));
        helper.assertTrue(!PlagueDoctor.needsCleansing(doctor, rabid), Component.literal("Rabies is past the doctor's help"));
        helper.assertTrue(!PlagueDoctor.needsCleansing(doctor, player), Component.literal("People should buy the potion, not be cleansed for free"));
        SkillGameTests.removePlayer(helper, player);
        rabid.discard();
        helper.succeedWhen(() -> {
            helper.assertTrue(!Rabies.isSick(seen), Component.literal("The doctor's thrown Cleanse should cure the wolf"));
            doctor.discard();
            seen.discard();
            hidden.discard();
        });
    }

    static void doctorLeavesHydrophobiaToThePotion(GameTestHelper helper) {
        PlagueDoctor doctor = helper.spawnWithNoFreeWill(ModEntities.PLAGUE_DOCTOR.get(), new BlockPos(4, 1, 4));
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        helper.assertTrue(Rabies.infect(player, Rabies.HYDROPHOBIA_TICKS), Component.literal("Players can catch rabies"));
        helper.assertValueEqual(PlagueDoctor.cureCost(player), 0, "the price the doctor asks for Hydrophobia (he doesn't treat it)");
        player.addEffect(new MobEffectInstance(ModEffects.PLAGUE, 600));
        player.getAbilities().instabuild = true;
        helper.assertTrue(doctor.treat(player) && !player.hasEffect(ModEffects.PLAGUE) && Rabies.isHydrophobic(player),
                Component.literal("Treating the Plague, the doctor should leave Hydrophobia to the potion"));
        // The potion he sells does it
        ItemStack potion = PotionContents.createItemStack(Items.POTION, ModPotions.CLEANSE);
        EventHooks.onItemUseFinish(player, potion.copy(), 0, potion.finishUsingItem(helper.getLevel(), player));
        helper.assertTrue(!Rabies.isSick(player), Component.literal("A drunk Potion of Cleanse should cure Hydrophobia"));
        player.setData(ModAttachments.IS_VAMPIRE, true);
        helper.assertTrue(!Rabies.canCatch(player), Component.literal("Vampires shouldn't catch rabies"));
        SkillGameTests.removePlayer(helper, player);
        doctor.discard();
        helper.succeed();
    }
}
